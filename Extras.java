package dev.skillsmp;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Welcome messages, announcer, playtime, bounties, daily reward and admin tools. */
public final class Extras {
    private final SkillSMP pl;
    private final Map<UUID, Long> joinedAt = new HashMap<>();
    private BukkitTask announcer;
    private BukkitTask lagTask;
    private int announceIndex = 0;
    private int lagLeft = 0;

    public Extras(SkillSMP pl) { this.pl = pl; }

    /* ---------------- welcome ---------------- */
    public void welcome(Player p, boolean first, long awayMs, int number) {
        ConfigurationSection s = pl.getConfig().getConfigurationSection(first ? "welcome.first-join" : "welcome.join");
        if (s == null || !s.getBoolean("enabled", true)) return;
        String away = awayMs > 0 ? Util.duration(awayMs) : "-";
        java.util.function.UnaryOperator<String> f = x ->
                pl.ph(x, p).replace("%number%", String.valueOf(number)).replace("%away%", away);

        for (String line : s.getStringList("message")) p.sendMessage(Util.c(f.apply(line)));
        String bc = s.getString("broadcast", "");
        if (!bc.isEmpty()) Bukkit.broadcast(Util.c(f.apply(bc)));

        String t = s.getString("title", "");
        String st = s.getString("subtitle", "");
        if (!t.isEmpty() || !st.isEmpty()) {
            int stay = Math.max(1, s.getInt("title-seconds", 3));
            p.showTitle(Title.title(Util.c(f.apply(t)), Util.c(f.apply(st)),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(stay), Duration.ofMillis(800))));
        }
        String sound = s.getString("sound", "");
        if (!sound.isEmpty()) p.playSound(p.getLocation(), sound, 1f, 1f);

        if (first) {
            double bonus = s.getDouble("money", 0);
            if (bonus > 0) pl.data.add(p.getUniqueId(), bonus);
            String kit = s.getString("kit", "");
            if (!kit.isEmpty()) pl.rewards.claimKit(p, kit.toLowerCase(Locale.ROOT));
        }
    }

    /* ---------------- playtime ---------------- */
    public void sessionStart(Player p) { joinedAt.put(p.getUniqueId(), System.currentTimeMillis()); }

    public void sessionEnd(Player p) {
        long now = System.currentTimeMillis();
        Long t = joinedAt.remove(p.getUniqueId());
        if (t != null) pl.data.addPlaytime(p.getUniqueId(), now - t);
        pl.data.setLastSeen(p.getUniqueId(), now);
    }

    /** Moves the running sessions into the saved total (called every few minutes and on shutdown). */
    public void flush() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> en : joinedAt.entrySet()) {
            pl.data.addPlaytime(en.getKey(), now - en.getValue());
            pl.data.setLastSeen(en.getKey(), now);
            en.setValue(now);
        }
    }

    public long playtime(Player p) {
        Long t = joinedAt.get(p.getUniqueId());
        return pl.data.playtime(p.getUniqueId()) + (t == null ? 0 : System.currentTimeMillis() - t);
    }

    /* ---------------- announcer ---------------- */
    public void startAnnouncer() {
        if (announcer != null) {
            announcer.cancel();
            announcer = null;
        }
        if (!pl.getConfig().getBoolean("announcer.enabled", false)) return;
        long ticks = Math.max(10, pl.getConfig().getInt("announcer.interval", 300)) * 20L;
        announcer = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            List<String> list = pl.getConfig().getStringList("announcer.messages");
            if (list.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
            announceIndex = (announceIndex + 1) % list.size();
            String prefix = pl.getConfig().getString("announcer.prefix", "");
            Bukkit.broadcast(Util.c(pl.ph(prefix + list.get(announceIndex), null)));
        }, ticks, ticks);
    }

    /* ---------------- bounty ---------------- */
    public void placeBounty(Player p, UUID target, double amount) {
        if (target.equals(p.getUniqueId())) {
            pl.msg(p, "bounty-self");
            return;
        }
        double min = pl.getConfig().getDouble("bounty.min", 100);
        if (amount < min) {
            pl.msg(p, "bounty-min", "%min%", pl.cur(min));
            return;
        }
        if (!pl.data.take(p.getUniqueId(), amount)) {
            pl.msg(p, "not-enough-money", "%cost%", pl.cur(amount));
            return;
        }
        pl.data.setBounty(target, pl.data.bounty(target) + amount);
        pl.broadcast("bounty-placed", "%player%", p.getName(), "%money%", pl.cur(amount), "%target%", pl.data.name(target));
    }

    public void claimBounty(Player killer, Player victim) {
        double b = pl.data.bounty(victim.getUniqueId());
        if (b <= 0) return;
        pl.data.setBounty(victim.getUniqueId(), 0);
        pl.data.add(killer.getUniqueId(), b);
        pl.broadcast("bounty-claimed", "%killer%", killer.getName(), "%victim%", victim.getName(), "%money%", pl.cur(b));
    }

    /* ---------------- daily reward ---------------- */
    public void daily(Player p) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = pl.data.dailyLast(id);
        long day = 24L * 3600L * 1000L;
        if (now - last < day) {
            pl.msg(p, "daily-wait", "%time%", Util.duration(day - (now - last)));
            return;
        }
        int streak = (last > 0 && now - last < 2 * day) ? pl.data.dailyStreak(id) + 1 : 1;
        int cap = Math.max(1, pl.getConfig().getInt("daily.max-streak", 7));
        int eff = Math.min(streak, cap);
        double reward = pl.getConfig().getDouble("daily.reward", 500)
                + (eff - 1) * pl.getConfig().getDouble("daily.streak-bonus", 100);
        pl.data.add(id, reward);
        pl.data.setDaily(id, now, streak);
        pl.msg(p, "daily-claimed", "%money%", pl.cur(reward), "%streak%", String.valueOf(streak));
    }

    /* ---------------- admin tools ---------------- */
    public void heal(Player t) {
        try {
            t.setHealth(20.0);
        } catch (IllegalArgumentException ignored) {
            // max health below 20
        }
        t.setFoodLevel(20);
        t.setSaturation(20f);
        t.setFireTicks(0);
    }

    public GameMode nextMode(Player t) {
        if (t.getGameMode() == GameMode.SURVIVAL) return GameMode.CREATIVE;
        if (t.getGameMode() == GameMode.CREATIVE) return GameMode.SPECTATOR;
        return GameMode.SURVIVAL;
    }

    /* ---------------- lag clear (every few minutes) ---------------- */
    public void startLagClear() {
        if (lagTask != null) {
            lagTask.cancel();
            lagTask = null;
        }
        if (!pl.getConfig().getBoolean("lag.enabled", true)) return;
        lagLeft = Math.max(30, pl.getConfig().getInt("lag.interval", 300));
        lagTask = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            lagLeft--;
            if (lagLeft <= 0) {
                clearLag();
                lagLeft = Math.max(30, pl.getConfig().getInt("lag.interval", 300));
                return;
            }
            if (pl.getConfig().getIntegerList("lag.warnings").contains(lagLeft)) {
                pl.broadcast("lag-warn", "%seconds%", String.valueOf(lagLeft));
            }
        }, 20L, 20L);
    }

    /** Removes dropped items and XP orbs from every loaded chunk (the usual cause of lag). */
    public int clearLag() {
        int items = 0;
        int orbs = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Item it : w.getEntitiesByClass(Item.class)) {
                it.remove();
                items++;
            }
            for (ExperienceOrb o : w.getEntitiesByClass(ExperienceOrb.class)) {
                o.remove();
                orbs++;
            }
        }
        pl.broadcast("lag-done", "%items%", String.valueOf(items), "%orbs%", String.valueOf(orbs));
        return items + orbs;
    }
}
