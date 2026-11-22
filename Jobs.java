package dev.skillsmp;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Jobs: players pick up to N jobs, earn money + XP for mining / chopping / farming / hunting / fishing,
 * level up for a bigger money bonus. Light: everything is a map lookup, payouts are shown once per second.
 */
public final class Jobs implements Listener {
    public record Pay(double money, double xp, boolean mature, String icon) {}

    public record Job(String id, String name, Material icon, List<String> description,
                      Map<String, Pay> breaks, Map<String, Pay> kills, Map<String, Pay> fish) {}

    private static final class Pending {
        double money;
        double xp;
        String job = "";
    }

    private final SkillSMP pl;
    private final NamespacedKey spawnerKey;
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Set<String> payable = new HashSet<>();      // block names that any job pays for
    private final Set<String> trackPlaced = new HashSet<>();  // of those, blocks that must not pay when a player placed them
    private final Map<String, Set<Long>> placed = new HashMap<>();
    private final Map<UUID, List<String>> active = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();

    public Jobs(SkillSMP pl) {
        this.pl = pl;
        this.spawnerKey = new NamespacedKey(pl, "spawner_mob");
        load();
        Bukkit.getScheduler().runTaskTimer(pl, this::flush, 20L, 20L);
    }

    /* ---------------- loading ---------------- */
    public void load() {
        jobs.clear();
        payable.clear();
        trackPlaced.clear();
        active.clear();
        ConfigurationSection root = pl.jobsCfg.getConfigurationSection("jobs");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Map<String, Pay> br = readPays(s.getConfigurationSection("break"));
            Map<String, Pay> kl = readPays(s.getConfigurationSection("kill"));
            Map<String, Pay> fs = readPays(s.getConfigurationSection("fish"));
            for (Map.Entry<String, Pay> en : br.entrySet()) {
                payable.add(en.getKey());
                if (!en.getValue().mature()) trackPlaced.add(en.getKey());
            }
            jobs.put(id.toLowerCase(Locale.ROOT), new Job(id.toLowerCase(Locale.ROOT), s.getString("name", id),
                    Util.mat(s.getString("icon"), Material.IRON_PICKAXE), s.getStringList("description"), br, kl, fs));
        }
    }

    private Map<String, Pay> readPays(ConfigurationSection s) {
        Map<String, Pay> out = new LinkedHashMap<>();
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            ConfigurationSection p = s.getConfigurationSection(k);
            if (p == null) continue;
            out.put(k.toUpperCase(Locale.ROOT), new Pay(Math.max(0, p.getDouble("money", 0)),
                    Math.max(0, p.getDouble("xp", 0)), p.getBoolean("mature", false), p.getString("icon", "")));
        }
        return out;
    }

    public Map<String, Job> all() { return jobs; }

    public Job get(String id) { return id == null ? null : jobs.get(id.toLowerCase(Locale.ROOT)); }

    private boolean enabled() { return pl.getConfig().getBoolean("jobs.enabled", true); }

    /* ---------------- joining / leaving ---------------- */
    public List<String> active(UUID id) {
        return active.computeIfAbsent(id, k -> new ArrayList<>(pl.data.jobList(k)));
    }

    public void forget(UUID id) {
        active.remove(id);
        pending.remove(id);
    }

    public boolean join(Player p, String jobId) {
        Job j = get(jobId);
        if (j == null) {
            pl.msg(p, "jobs-missing");
            return false;
        }
        UUID id = p.getUniqueId();
        List<String> list = active(id);
        if (list.contains(j.id())) {
            pl.msg(p, "jobs-already");
            return false;
        }
        int max = Math.max(1, pl.getConfig().getInt("jobs.max-jobs", 2));
        if (list.size() >= max) {
            pl.msg(p, "jobs-max", "%max%", String.valueOf(max));
            return false;
        }
        list.add(j.id());
        pl.data.setJobList(id, new ArrayList<>(list));
        pl.msg(p, "jobs-joined", "%job%", j.name());
        return true;
    }

    public boolean leave(Player p, String jobId) {
        Job j = get(jobId);
        if (j == null) {
            pl.msg(p, "jobs-missing");
            return false;
        }
        UUID id = p.getUniqueId();
        List<String> list = active(id);
        if (!list.remove(j.id())) {
            pl.msg(p, "jobs-not-joined");
            return false;
        }
        pl.data.setJobList(id, new ArrayList<>(list));
        pl.msg(p, "jobs-left", "%job%", j.name());
        return true;
    }

    /* ---------------- levels ---------------- */
    /** XP needed to go from this level to the next one. */
    public double need(int level) {
        return pl.getConfig().getDouble("jobs.level-xp-base", 100)
                + (level - 1) * pl.getConfig().getDouble("jobs.level-xp-step", 40);
    }

    public double bonusPercent(int level) {
        return level * pl.getConfig().getDouble("jobs.bonus-per-level", 1.5);
    }

    private void addXp(Player p, Job job, double xp) {
        UUID id = p.getUniqueId();
        int max = Math.max(1, pl.getConfig().getInt("jobs.max-level", 100));
        int lvl = pl.data.jobLevel(id, job.id());
        double cur = pl.data.jobXp(id, job.id()) + xp;
        boolean up = false;
        while (lvl < max && cur >= need(lvl)) {
            cur -= need(lvl);
            lvl++;
            up = true;
        }
        if (lvl >= max) cur = 0;
        pl.data.setJobProgress(id, job.id(), lvl, cur);
        if (up) {
            pl.msg(p, "jobs-levelup", "%job%", job.name(), "%level%", String.valueOf(lvl));
            p.showTitle(Title.title(Util.c("&a&lLEVEL UP!"), Util.c(job.name() + " &7- level &f" + lvl),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), "entity.player.levelup", 1f, 1f);
        }
    }

    /* ---------------- paying ---------------- */
    private void reward(Player p, Job j, Pay pay) {
        UUID id = p.getUniqueId();
        double mult = Math.max(0, pl.getConfig().getDouble("jobs.multiplier", 1.0));
        double bonus = 1 + bonusPercent(pl.data.jobLevel(id, j.id())) / 100.0;
        double money = Math.round(pay.money() * mult * bonus * 100.0) / 100.0;
        if (money > 0) {
            pl.data.add(id, money);
            pl.data.addJobEarned(id, j.id(), money);
        }
        if (pay.xp() > 0) addXp(p, j, pay.xp());
        Pending pd = pending.computeIfAbsent(id, k -> new Pending());
        pd.money += money;
        pd.xp += pay.xp();
        pd.job = j.name();
    }

    /** Shows what was earned in the last second as ONE action-bar message (no chat spam, less lag). */
    private void flush() {
        if (pending.isEmpty()) return;
        String text = pl.getConfig().getString("jobs.actionbar", "&a+%money% &7%job% &8(&b+%xp% XP&8)");
        for (Map.Entry<UUID, Pending> en : pending.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            Pending pd = en.getValue();
            if (p == null || (pd.money <= 0 && pd.xp <= 0)) continue;
            p.sendActionBar(Util.c(Util.rep(text, "%money%", pl.cur(pd.money), "%job%", pd.job,
                    "%xp%", String.format(Locale.US, "%.1f", pd.xp))));
        }
        pending.clear();
    }

    /* ---------------- events ---------------- */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!enabled() || p.getGameMode() == GameMode.CREATIVE) return;
        Block b = e.getBlock();
        String name = b.getType().name();
        if (!payable.contains(name)) return;
        Set<Long> set = placed.get(b.getWorld().getName());
        if (set != null && set.remove(b.getBlockKey())) return;     // placed by a player: no money
        List<String> list = active(p.getUniqueId());
        if (list.isEmpty()) return;
        for (String jid : list) {
            Job j = jobs.get(jid);
            if (j == null) continue;
            Pay pay = j.breaks().get(name);
            if (pay == null) continue;
            if (pay.mature() && !(b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge())) continue;
            reward(p, j, pay);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!enabled()) return;
        Block b = e.getBlockPlaced();
        if (!trackPlaced.contains(b.getType().name())) return;
        Set<Long> set = placed.computeIfAbsent(b.getWorld().getName(), k -> new HashSet<>());
        if (set.size() > 100_000) set.clear();                      // keeps memory small
        set.add(b.getBlockKey());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            e.getEntity().getPersistentDataContainer().set(spawnerKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        Player p = v.getKiller();
        if (p == null || v instanceof Player || !enabled() || p.getGameMode() == GameMode.CREATIVE) return;
        if (!pl.getConfig().getBoolean("jobs.pay-spawner-mobs", false)
                && v.getPersistentDataContainer().has(spawnerKey, PersistentDataType.BYTE)) return;
        List<String> list = active(p.getUniqueId());
        if (list.isEmpty()) return;
        String type = v.getType().name();
        for (String jid : list) {
            Job j = jobs.get(jid);
            if (j == null) continue;
            Pay pay = j.kills().get(type);
            if (pay != null) reward(p, j, pay);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item it)) return;
        Player p = e.getPlayer();
        if (!enabled() || p.getGameMode() == GameMode.CREATIVE) return;
        List<String> list = active(p.getUniqueId());
        if (list.isEmpty()) return;
        String name = it.getItemStack().getType().name();
        for (String jid : list) {
            Job j = jobs.get(jid);
            if (j == null) continue;
            Pay pay = j.fish().get(name);
            if (pay == null) pay = j.fish().get("DEFAULT");
            if (pay != null) reward(p, j, pay);
        }
    }
}
