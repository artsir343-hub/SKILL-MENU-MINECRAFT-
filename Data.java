package dev.skillsmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All player data (money, stats, homes, keys, ...), warps, spawn and auction listings, stored in data.yml. */
public final class Data {
    public record Listing(int id, UUID seller, String sellerName, double price, ItemStack item) {}

    private final SkillSMP pl;
    private final File file;
    private final YamlConfiguration yml;
    private boolean dirty;

    public Data(SkillSMP pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "data.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException e) {
            pl.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    public void saveIfDirty() { if (dirty) save(); }

    private String k(UUID id, String key) { return "players." + id + "." + key; }

    private double start() { return pl.getConfig().getDouble("economy.starting-balance", 100); }

    /* ---------- players ---------- */
    public boolean isNew(UUID id) { return !yml.contains(k(id, "name")); }

    public void touch(Player p) {
        UUID id = p.getUniqueId();
        yml.set(k(id, "name"), p.getName());
        if (!yml.contains(k(id, "money"))) yml.set(k(id, "money"), start());
        dirty = true;
    }

    public int playerCount() {
        ConfigurationSection s = yml.getConfigurationSection("players");
        return s == null ? 0 : s.getKeys(false).size();
    }

    public String name(UUID id) { return yml.getString(k(id, "name"), "Unknown"); }

    public UUID findUuid(String name) {
        ConfigurationSection s = yml.getConfigurationSection("players");
        if (s == null) return null;
        for (String key : s.getKeys(false)) {
            if (name.equalsIgnoreCase(s.getString(key + ".name", ""))) {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    // not a uuid key
                }
            }
        }
        return null;
    }

    public long lastSeen(UUID id) { return yml.getLong(k(id, "last-seen"), 0L); }

    public void setLastSeen(UUID id, long t) { yml.set(k(id, "last-seen"), t); dirty = true; }

    /** Total play time in milliseconds (finished sessions). */
    public long playtime(UUID id) { return yml.getLong(k(id, "playtime"), 0L); }

    public void addPlaytime(UUID id, long ms) {
        yml.set(k(id, "playtime"), playtime(id) + Math.max(0, ms));
        dirty = true;
    }

    /* ---------- money ---------- */
    public double bal(UUID id) { return yml.getDouble(k(id, "money"), start()); }

    /** Balances are capped (economy.max-balance) - a huge number overflowed to $92,233,720,368,547,760 before. */
    public void setBal(UUID id, double v) {
        double cap = Math.max(1000, pl.getConfig().getDouble("economy.max-balance", 1.0E15));
        if (Double.isNaN(v) || v < 0) v = 0;
        v = Math.min(v, cap);
        yml.set(k(id, "money"), Math.round(v * 100.0) / 100.0);
        dirty = true;
    }

    public void add(UUID id, double v) { setBal(id, bal(id) + v); }

    public boolean take(UUID id, double v) {
        if (bal(id) + 0.0001 < v) return false;
        setBal(id, bal(id) - v);
        return true;
    }

    /* ---------- stats ---------- */
    public int kills(UUID id) { return yml.getInt(k(id, "kills"), 0); }

    public int deaths(UUID id) { return yml.getInt(k(id, "deaths"), 0); }

    public void addKill(UUID id) { yml.set(k(id, "kills"), kills(id) + 1); dirty = true; }

    public void addDeath(UUID id) { yml.set(k(id, "deaths"), deaths(id) + 1); dirty = true; }

    public void resetStats(UUID id) {
        yml.set(k(id, "kills"), 0);
        yml.set(k(id, "deaths"), 0);
        dirty = true;
    }

    public double value(UUID id, String field) { return yml.getDouble(k(id, field), 0); }

    /** Top players by a numeric field (money, kills, deaths, playtime, bounty). Values are read once, then sorted. */
    public List<UUID> top(String field, int n) {
        List<UUID> ids = new ArrayList<>();
        ConfigurationSection s = yml.getConfigurationSection("players");
        if (s == null) return ids;
        Map<UUID, Double> vals = new HashMap<>();
        for (String key : s.getKeys(false)) {
            try {
                vals.put(UUID.fromString(key), s.getDouble(key + "." + field, 0));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        ids.addAll(vals.keySet());
        ids.sort((a, b) -> Double.compare(vals.get(b), vals.get(a)));
        return ids.size() > n ? new ArrayList<>(ids.subList(0, n)) : ids;
    }

    /* ---------- bounty / daily ---------- */
    public double bounty(UUID id) { return yml.getDouble(k(id, "bounty"), 0); }

    public void setBounty(UUID id, double v) {
        yml.set(k(id, "bounty"), Math.max(0, Math.round(v * 100.0) / 100.0));
        dirty = true;
    }

    public long dailyLast(UUID id) { return yml.getLong(k(id, "daily-last"), 0L); }

    public int dailyStreak(UUID id) { return yml.getInt(k(id, "daily-streak"), 0); }

    public void setDaily(UUID id, long at, int streak) {
        yml.set(k(id, "daily-last"), at);
        yml.set(k(id, "daily-streak"), streak);
        dirty = true;
    }

    /* ---------- locations (homes, warps, spawn) ---------- */
    private void writeLoc(String b, Location l) {
        yml.set(b + ".world", l.getWorld().getName());
        yml.set(b + ".x", l.getX());
        yml.set(b + ".y", l.getY());
        yml.set(b + ".z", l.getZ());
        yml.set(b + ".yaw", l.getYaw());
        yml.set(b + ".pitch", l.getPitch());
        dirty = true;
    }

    private Location readLoc(String b) {
        ConfigurationSection s = yml.getConfigurationSection(b);
        if (s == null) return null;
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w == null) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public Map<String, Location> homes(UUID id) {
        Map<String, Location> out = new LinkedHashMap<>();
        ConfigurationSection s = yml.getConfigurationSection(k(id, "homes"));
        if (s == null) return out;
        for (String h : s.getKeys(false)) {
            Location l = readLoc(k(id, "homes") + "." + h);
            if (l != null) out.put(h, l);
        }
        return out;
    }

    public void setHome(UUID id, String name, Location l) { writeLoc(k(id, "homes") + "." + name, l); }

    public boolean delHome(UUID id, String name) {
        String b = k(id, "homes") + "." + name;
        if (!yml.contains(b)) return false;
        yml.set(b, null);
        dirty = true;
        return true;
    }

    public Map<String, Location> warps() {
        Map<String, Location> out = new LinkedHashMap<>();
        ConfigurationSection s = yml.getConfigurationSection("warps");
        if (s == null) return out;
        for (String w : s.getKeys(false)) {
            Location l = readLoc("warps." + w);
            if (l != null) out.put(w, l);
        }
        return out;
    }

    public void setWarp(String name, Location l, String icon) {
        writeLoc("warps." + name, l);
        yml.set("warps." + name + ".icon", icon);
    }

    public String warpIcon(String name) { return yml.getString("warps." + name + ".icon", "ENDER_PEARL"); }

    public boolean delWarp(String name) {
        if (!yml.contains("warps." + name)) return false;
        yml.set("warps." + name, null);
        dirty = true;
        return true;
    }

    public void setSpawn(Location l) { writeLoc("spawn", l); }

    public Location spawn() { return readLoc("spawn"); }

    /* ---------- kits / keys ---------- */
    public long kitReady(UUID id, String kit) { return yml.getLong(k(id, "kits." + kit), 0L); }

    public void setKitReady(UUID id, String kit, long at) { yml.set(k(id, "kits." + kit), at); dirty = true; }

    public int keys(UUID id, String crate) { return yml.getInt(k(id, "keys." + crate), 0); }

    public void addKeys(UUID id, String crate, int amount) {
        yml.set(k(id, "keys." + crate), Math.max(0, keys(id, crate) + amount));
        dirty = true;
    }

    /* ---------- auction ---------- */
    public int addListing(UUID seller, String sellerName, double price, ItemStack item) {
        int id = yml.getInt("auction-next", 1);
        yml.set("auction-next", id + 1);
        String b = "auction." + id;
        yml.set(b + ".seller", seller.toString());
        yml.set(b + ".name", sellerName);
        yml.set(b + ".price", price);
        yml.set(b + ".item", item);
        save();
        return id;
    }

    public Listing listing(int id) {
        String b = "auction." + id;
        ItemStack it = yml.getItemStack(b + ".item");
        if (it == null) return null;
        try {
            return new Listing(id, UUID.fromString(yml.getString(b + ".seller", "")),
                    yml.getString(b + ".name", "?"), yml.getDouble(b + ".price"), it);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void removeListing(int id) {
        yml.set("auction." + id, null);
        save();
    }

    public List<Listing> listings() {
        List<Listing> out = new ArrayList<>();
        ConfigurationSection s = yml.getConfigurationSection("auction");
        if (s == null) return out;
        for (String key : s.getKeys(false)) {
            try {
                Listing l = listing(Integer.parseInt(key));
                if (l != null) out.add(l);
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        out.sort((a, b) -> Integer.compare(b.id(), a.id()));
        return out;
    }
}
