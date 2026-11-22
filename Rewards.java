package dev.skillsmp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** Kits and crates (percent chances + keys). */
public final class Rewards {
    public record Reward(String id, double weight, ItemStack icon, ConfigurationSection sec) {}

    private final SkillSMP pl;
    private final Random rnd = new Random();

    public Rewards(SkillSMP pl) { this.pl = pl; }

    /* ---------------- kits ---------------- */
    public List<ItemStack> kitItems(ConfigurationSection k) {
        List<ItemStack> out = new ArrayList<>();
        for (String s : k.getStringList("items")) {
            String[] a = s.split(":");
            Material m = Material.matchMaterial(a[0].trim());
            if (m == null || !m.isItem()) continue;
            int n = 1;
            if (a.length > 1) {
                try {
                    n = Integer.parseInt(a[1].trim());
                } catch (NumberFormatException ignored) {
                    // keep 1
                }
            }
            out.add(new ItemStack(m, Math.max(1, n)));
        }
        List<?> stacks = k.getList("stacks");
        if (stacks != null) {
            for (Object o : stacks) if (o instanceof ItemStack it) out.add(it.clone());
        }
        return out;
    }

    private boolean empty(ItemStack it) { return it == null || it.getType().isAir(); }

    /** Puts armor on if that slot is free. Returns true when the item was equipped. */
    private boolean equip(Player p, ItemStack it) {
        String n = it.getType().name();
        PlayerInventory inv = p.getInventory();
        if (n.endsWith("_HELMET") && empty(inv.getHelmet())) {
            inv.setHelmet(it);
            return true;
        }
        if ((n.endsWith("_CHESTPLATE") || n.equals("ELYTRA")) && empty(inv.getChestplate())) {
            inv.setChestplate(it);
            return true;
        }
        if (n.endsWith("_LEGGINGS") && empty(inv.getLeggings())) {
            inv.setLeggings(it);
            return true;
        }
        if (n.endsWith("_BOOTS") && empty(inv.getBoots())) {
            inv.setBoots(it);
            return true;
        }
        return false;
    }

    public void claimKit(Player p, String id) {
        ConfigurationSection k = pl.kits.getConfigurationSection("kits." + id);
        if (k == null) {
            pl.msg(p, "kit-missing");
            return;
        }
        UUID u = p.getUniqueId();
        String perm = k.getString("permission", "");
        if (!perm.isEmpty() && !p.hasPermission(perm) && !pl.isAdmin(p)) {
            pl.msg(p, "kit-no-perm");
            return;
        }
        long now = System.currentTimeMillis();
        long ready = pl.data.kitReady(u, id);
        if (ready == Long.MAX_VALUE) {
            pl.msg(p, "kit-once");
            return;
        }
        if (ready > now && !p.hasPermission("skillsmp.bypass.cooldown")) {
            pl.msg(p, "kit-cooldown", "%time%", Util.duration(ready - now));
            return;
        }
        double price = k.getDouble("price", 0);
        if (price > 0 && !pl.data.take(u, price)) {
            pl.msg(p, "not-enough-money", "%cost%", pl.cur(price));
            return;
        }
        for (ItemStack it : kitItems(k)) {
            if (!equip(p, it)) pl.eco.give(p, it);
        }
        boolean once = k.getBoolean("once", false);
        pl.data.setKitReady(u, id, once ? Long.MAX_VALUE : now + k.getLong("cooldown", 0) * 1000L);
        pl.msg(p, "kit-claimed", "%kit%", k.getString("name", id));
    }

    /* ---------------- crates: rewards and percent ---------------- */
    public List<Reward> rewards(String crate) {
        List<Reward> out = new ArrayList<>();
        ConfigurationSection rs = pl.crates.getConfigurationSection("crates." + crate + ".rewards");
        if (rs == null) return out;
        for (String id : rs.getKeys(false)) {
            ConfigurationSection s = rs.getConfigurationSection(id);
            if (s == null) continue;
            out.add(new Reward(id, Math.max(0, s.getDouble("chance", 0)), icon(s), s));
        }
        return out;
    }

    public double total(List<Reward> rs) {
        double t = 0;
        for (Reward r : rs) t += r.weight();
        return t;
    }

    /** Chance of one reward in percent (always relative to the crate total, so it adds up to 100). */
    public double percent(Reward r, List<Reward> rs) {
        double t = total(rs);
        return t <= 0 ? 0 : r.weight() * 100.0 / t;
    }

    /** Colored rarity name from the chance. Limits are in config crates.legendary-below / epic-below / rare-below. */
    public String rarity(double pct) {
        if (pct <= pl.getConfig().getDouble("crates.legendary-below", 3)) return "&6&lLEGENDARY";
        if (pct <= pl.getConfig().getDouble("crates.epic-below", 10)) return "&5&lEPIC";
        if (pct <= pl.getConfig().getDouble("crates.rare-below", 30)) return "&9&lRARE";
        return "&7COMMON";
    }

    private void announce(Player p, ConfigurationSection c, String crate, Reward win, List<Reward> rs) {
        double pct = percent(win, rs);
        if (pct > pl.getConfig().getDouble("crates.broadcast-below", 5)) return;
        pl.broadcast("crate-broadcast", "%player%", p.getName(), "%reward%", label(win),
                "%crate%", c.getString("name", crate), "%rarity%", rarity(pct));
    }

    /** Sets one reward to exactly pct % and scales all the others so the total stays 100%. */
    public void setPercent(String crate, String id, double pct) {
        pct = Math.max(0, Math.min(100, pct));
        List<Reward> rs = rewards(crate);
        double others = 0;
        for (Reward r : rs) if (!r.id().equals(id)) others += r.weight();
        double remaining = 100 - pct;
        for (Reward r : rs) {
            String path = "crates." + crate + ".rewards." + r.id() + ".chance";
            if (r.id().equals(id)) pl.crates.set(path, Math.round(pct * 100.0) / 100.0);
            else if (others > 0) pl.crates.set(path, Math.round(r.weight() * remaining / others * 100.0) / 100.0);
        }
        pl.saveCrates();
    }

    /** Scales all chances so they add up to exactly 100%. */
    public void balance(String crate) {
        List<Reward> rs = rewards(crate);
        double total = total(rs);
        if (total <= 0) return;
        for (Reward r : rs) {
            pl.crates.set("crates." + crate + ".rewards." + r.id() + ".chance",
                    Math.round(r.weight() * 10000.0 / total) / 100.0);
        }
        pl.saveCrates();
    }

    private ItemStack icon(ConfigurationSection s) {
        String type = s.getString("type", "item").toLowerCase(Locale.ROOT);
        String disp = s.getString("display");
        ItemStack it;
        switch (type) {
            case "stack" -> {
                ItemStack st = s.getItemStack("stack");
                it = st == null ? new ItemStack(Material.BARRIER) : st.clone();
            }
            case "money" -> it = Util.item(Material.GOLD_INGOT, "&6" + pl.cur(s.getDouble("amount")), null, 1);
            case "keys" -> it = Util.item(Material.TRIPWIRE_HOOK,
                    "&e" + s.getInt("amount", 1) + "x " + s.getString("crate", "?") + " key", null, 1);
            case "command" -> it = Util.item(Util.mat(s.getString("icon"), Material.NETHER_STAR),
                    "&d" + (disp == null ? "Special reward" : disp), null, 1);
            default -> it = new ItemStack(Util.mat(s.getString("material"), Material.STONE),
                    Math.max(1, Math.min(64, s.getInt("amount", 1))));
        }
        if (disp != null && !type.equals("command")) {
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.displayName(Util.c(disp));
                it.setItemMeta(meta);
            }
        }
        return it;
    }

    private String label(Reward r) {
        String d = r.sec().getString("display");
        return d != null ? d : Util.pretty(r.sec().getString("material", r.sec().getString("type", "reward")));
    }

    private void grant(Player p, ConfigurationSection s) {
        String type = s.getString("type", "item").toLowerCase(Locale.ROOT);
        switch (type) {
            case "money" -> pl.data.add(p.getUniqueId(), s.getDouble("amount"));
            case "stack" -> {
                ItemStack st = s.getItemStack("stack");
                if (st != null) pl.eco.give(p, st.clone());
            }
            case "keys" -> pl.data.addKeys(p.getUniqueId(), s.getString("crate", ""), s.getInt("amount", 1));
            case "command" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    s.getString("command", "").replace("%player%", p.getName()));
            default -> pl.eco.give(p, new ItemStack(Util.mat(s.getString("material"), Material.STONE),
                    Math.max(1, s.getInt("amount", 1))));
        }
    }

    /** Weighted random pick: a reward with 5% comes out about 5 times in 100 opens. */
    private Reward roll(List<Reward> rs) {
        double x = rnd.nextDouble() * total(rs);
        Reward last = rs.get(rs.size() - 1);
        for (Reward r : rs) {
            if (r.weight() <= 0) continue;
            last = r;
            x -= r.weight();
            if (x <= 0) return r;
        }
        return last;
    }

    /* ---------------- crates: keys ---------------- */
    public void buyKey(Player p, String crate) {
        ConfigurationSection c = pl.crates.getConfigurationSection("crates." + crate);
        if (c == null) {
            pl.msg(p, "crate-missing");
            return;
        }
        double price = c.getDouble("key-price", 0);
        if (price <= 0) {
            pl.msg(p, "key-not-sold");
            return;
        }
        if (!pl.data.take(p.getUniqueId(), price)) {
            pl.msg(p, "not-enough-money", "%cost%", pl.cur(price));
            return;
        }
        pl.data.addKeys(p.getUniqueId(), crate, 1);
        pl.msg(p, "key-bought", "%crate%", c.getString("name", crate), "%cost%", pl.cur(price));
    }

    /* ---------------- crates: open several at once (no animation) ---------------- */
    public void openMany(Player p, String crate, int n) {
        if (n <= 1) {
            open(p, crate);
            return;
        }
        ConfigurationSection c = pl.crates.getConfigurationSection("crates." + crate);
        if (c == null) {
            pl.msg(p, "crate-missing");
            return;
        }
        List<Reward> rs = rewards(crate);
        if (rs.isEmpty() || total(rs) <= 0) {
            pl.msg(p, "crate-empty");
            return;
        }
        UUID u = p.getUniqueId();
        boolean needKey = c.getBoolean("key-required", false);
        double cost = c.getDouble("cost", 0);
        Map<String, Integer> counts = new LinkedHashMap<>();
        int done = 0;
        for (int i = 0; i < n; i++) {
            if (needKey) {
                if (pl.data.keys(u, crate) < 1) break;
                pl.data.addKeys(u, crate, -1);
            } else if (cost > 0 && !pl.data.take(u, cost)) {
                break;
            }
            Reward win = roll(rs);
            grant(p, win.sec());
            announce(p, c, crate, win, rs);
            counts.merge(label(win), 1, Integer::sum);
            done++;
        }
        if (done == 0) {
            if (needKey) pl.msg(p, "crate-no-key"); else pl.msg(p, "not-enough-money", "%cost%", pl.cur(cost));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> en : counts.entrySet()) {
            if (sb.length() > 0) sb.append("&7, ");
            sb.append("&f").append(en.getValue()).append("x ").append(en.getKey());
        }
        pl.msg(p, "crate-won-many", "%amount%", String.valueOf(done), "%rewards%", sb.toString());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    /* ---------------- crates: opening ---------------- */
    public void open(Player p, String crate) {
        ConfigurationSection c = pl.crates.getConfigurationSection("crates." + crate);
        if (c == null) {
            pl.msg(p, "crate-missing");
            return;
        }
        List<Reward> rs = rewards(crate);
        if (rs.isEmpty() || total(rs) <= 0) {
            pl.msg(p, "crate-empty");
            return;
        }
        UUID u = p.getUniqueId();
        double cost = c.getDouble("cost", 0);
        if (c.getBoolean("key-required", false)) {
            if (pl.data.keys(u, crate) < 1) {
                pl.msg(p, "crate-no-key");
                return;
            }
            pl.data.addKeys(u, crate, -1);
        } else if (cost > 0 && !pl.data.take(u, cost)) {
            pl.msg(p, "not-enough-money", "%cost%", pl.cur(cost));
            return;
        }

        // the prize is decided and given right away; the animation is only cosmetic
        Reward win = roll(rs);
        grant(p, win.sec());
        announce(p, c, crate, win, rs);
        String rarityText = rarity(percent(win, rs));

        int steps = Math.max(4, pl.getConfig().getInt("crates.animation-steps", 24));
        int speed = Math.max(1, pl.getConfig().getInt("crates.animation-speed", 3));
        List<ItemStack> seq = new ArrayList<>();
        for (int i = 0; i < steps + 9; i++) seq.add(roll(rs).icon());
        seq.set(steps + 4, win.icon());

        Menu m = new Menu(Util.c(pl.serverName() + " &8\u00bb " + c.getString("name", crate)), 3);
        ItemStack pane = Util.item(Material.BLUE_STAINED_GLASS_PANE, " ", null, 1);
        for (int i = 0; i < 27; i++) m.set(i, pane);
        m.set(4, Util.item(Material.HOPPER, "&9\u25bc", null, 1));
        m.set(22, Util.item(Material.HOPPER, "&9\u25b2", null, 1));
        m.open(p);

        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (i > steps) {
                    cancel();
                    if (p.isOnline()) {
                        pl.msg(p, "crate-won", "%reward%", label(win), "%rarity%", rarityText);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    }
                    return;
                }
                if (p.isOnline() && p.getOpenInventory().getTopInventory().equals(m.getInventory())) {
                    for (int s = 0; s < 9; s++) m.getInventory().setItem(9 + s, seq.get(i + s));
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
                }
                i++;
            }
        }.runTaskTimer(pl, 0L, speed);
    }
}
