package dev.skillsmp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class SkillSMP extends JavaPlugin {
    private static final String[] COMMANDS = {
            "menu", "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "sethome", "home", "delhome", "homes",
            "kits", "kit", "crates", "rtp", "money", "pay", "shop", "sell", "ah", "top", "economy", "adminpanel",
            "skillsmp", "warp", "spawn", "setspawn", "setwarp", "delwarp", "back", "bounty", "daily", "broadcast",
            "lagclear"
    };
    private static final int CONFIG_VERSION = 3;
    private static final int SHOP_VERSION = 2;

    private record Prompt(Consumer<String> handler, long expires) {}

    public NamespacedKey compassKey;
    public Data data;
    public Economy eco;
    public Rewards rewards;
    public Menus menus;
    public Teleports tp;
    public Sidebar sidebar;
    public Extras extras;
    public YamlConfiguration shop, kits, crates;

    private File shopFile, kitsFile, cratesFile;
    private final Map<Material, Double> sellPrices = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new HashMap<>();

    @Override
    public void onEnable() {
        migrate();
        saveDefaultConfig();
        // add any new options from this version to the existing config.yml (your edits are kept)
        getConfig().options().copyDefaults(true);
        saveConfig();
        for (String f : new String[]{"shop.yml", "kits.yml", "crates.yml"}) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }
        compassKey = new NamespacedKey(this, "menu_compass");
        loadFiles();

        data = new Data(this);
        eco = new Economy(this);
        rewards = new Rewards(this);
        extras = new Extras(this);
        menus = new Menus(this);
        tp = new Teleports(this);
        sidebar = new Sidebar(this);

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        Commands cmds = new Commands(this);
        for (String n : COMMANDS) {
            PluginCommand pc = getCommand(n);
            if (pc != null) {
                pc.setExecutor(cmds);
                pc.setTabCompleter(cmds);
            }
        }

        // keep the compass in everyone's inventory (also brings it back if it was lost/destroyed)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("compass.enabled", true)) return;
            for (Player p : Bukkit.getOnlinePlayers()) ensureCompass(p);
        }, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, () -> sidebar.updateAll(), 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            extras.flush();
            data.saveIfDirty();
        }, 20L * 300, 20L * 300);

        extras.startAnnouncer();
        extras.startLagClear();
        for (Player p : Bukkit.getOnlinePlayers()) onJoin(p, false);
        getLogger().info("SKILL SMP enabled.");
    }

    @Override
    public void onDisable() {
        if (extras != null) extras.flush();
        if (data != null) data.save();
    }

    /** Old config.yml / shop.yml are saved as config-old.yml / shop-old.yml and replaced by the new layout. */
    private void migrate() {
        File dir = getDataFolder();
        File cf = new File(dir, "config.yml");
        if (cf.exists() && YamlConfiguration.loadConfiguration(cf).getInt("config-version", 0) < CONFIG_VERSION) {
            backup(cf, "config-old.yml");
        }
        File sf = new File(dir, "shop.yml");
        if (sf.exists() && YamlConfiguration.loadConfiguration(sf).getInt("version", 0) < SHOP_VERSION) {
            backup(sf, "shop-old.yml");
        }
    }

    private void backup(File f, String name) {
        try {
            Files.move(f.toPath(), new File(getDataFolder(), name).toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info(f.getName() + " was updated to the new layout. Your old file was saved as " + name);
        } catch (IOException e) {
            getLogger().warning("Could not back up " + f.getName() + ": " + e.getMessage());
        }
    }

    /* ---------------- files ---------------- */
    public void loadFiles() {
        shopFile = new File(getDataFolder(), "shop.yml");
        kitsFile = new File(getDataFolder(), "kits.yml");
        cratesFile = new File(getDataFolder(), "crates.yml");
        shop = YamlConfiguration.loadConfiguration(shopFile);
        kits = YamlConfiguration.loadConfiguration(kitsFile);
        crates = YamlConfiguration.loadConfiguration(cratesFile);
        rebuildSell();
    }

    public void saveShop() {
        try { shop.save(shopFile); } catch (IOException e) { getLogger().warning("shop.yml: " + e.getMessage()); }
        rebuildSell();
    }

    public void saveKits() {
        try { kits.save(kitsFile); } catch (IOException e) { getLogger().warning("kits.yml: " + e.getMessage()); }
    }

    public void saveCrates() {
        try { crates.save(cratesFile); } catch (IOException e) { getLogger().warning("crates.yml: " + e.getMessage()); }
    }

    private void rebuildSell() {
        sellPrices.clear();
        ConfigurationSection cats = shop.getConfigurationSection("categories");
        if (cats == null) return;
        for (String c : cats.getKeys(false)) {
            ConfigurationSection items = cats.getConfigurationSection(c + ".items");
            if (items == null) continue;
            for (String id : items.getKeys(false)) {
                Material m = Material.matchMaterial(items.getString(id + ".material", ""));
                double s = items.getDouble(id + ".sell", 0);
                if (m != null && s > 0) sellPrices.putIfAbsent(m, s);
            }
        }
    }

    public double sellPrice(Material m) { return sellPrices.getOrDefault(m, 0.0); }

    public void reloadAll() {
        reloadConfig();
        loadFiles();
        refresh();
    }

    /** Re-applies server name / compass look / sidebar / announcer to everyone. */
    public void refresh() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshCompass(p);
            sidebar.show(p);
        }
        extras.startAnnouncer();
        extras.startLagClear();
    }

    /* ---------------- text helpers ---------------- */
    public String serverName() { return getConfig().getString("server-name", "&9&lSKILL SMP"); }

    public String cur(double v) { return getConfig().getString("economy.currency-symbol", "$") + Util.money(v); }

    /** Short form for tiny spaces (sidebar): $1.2K, $3.4M, $5.6B ... */
    public String curShort(double v) { return getConfig().getString("economy.currency-symbol", "$") + Util.shortNum(v); }

    public String ph(String s, Player p) {
        if (s == null) return "";
        s = s.replace("%server%", serverName());
        if (p != null) {
            UUID id = p.getUniqueId();
            int k = data.kills(id), d = data.deaths(id);
            String kdr = d == 0 ? String.valueOf(k) : String.format(Locale.US, "%.2f", (double) k / d);
            s = s.replace("%player%", p.getName())
                    .replace("%money%", cur(data.bal(id)))
                    .replace("%money_short%", curShort(data.bal(id)))
                    .replace("%kills%", String.valueOf(k))
                    .replace("%deaths%", String.valueOf(d))
                    .replace("%kdr%", kdr)
                    .replace("%playtime%", Util.duration(extras.playtime(p)))
                    .replace("%bounty%", cur(data.bounty(id)))
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        }
        return s;
    }

    public void msg(CommandSender to, String key, String... kv) {
        String raw = getConfig().getString("messages." + key, "&c[missing message: " + key + "]");
        if (raw.isEmpty()) return;
        String full = getConfig().getString("messages.prefix", "") + Util.rep(raw, kv);
        to.sendMessage(Util.c(full.replace("%server%", serverName())));
    }

    public void broadcast(String key, String... kv) {
        String raw = getConfig().getString("messages." + key, "");
        if (raw.isEmpty()) return;
        String full = getConfig().getString("messages.prefix", "") + Util.rep(raw, kv);
        Bukkit.broadcast(Util.c(full.replace("%server%", serverName())));
    }

    /* ---------------- permissions ---------------- */
    /** Admin = op, permission skillsmp.admin, or tag "admin"/"owner" (/tag <player> add admin). */
    public boolean isAdmin(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        return p.isOp() || p.hasPermission("skillsmp.admin")
                || p.getScoreboardTags().contains("admin") || p.getScoreboardTags().contains("owner");
    }

    public boolean isOwner(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        return p.isOp() || p.hasPermission("skillsmp.owner") || p.getScoreboardTags().contains("owner");
    }

    public UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        return data.findUuid(name);
    }

    /* ---------------- chat prompts ---------------- */
    public void ask(Player p, String question, Consumer<String> handler) {
        p.closeInventory();
        prompts.put(p.getUniqueId(), new Prompt(handler, System.currentTimeMillis() + 120_000L));
        p.sendMessage(Util.c(getConfig().getString("messages.prefix", "").replace("%server%", serverName())
                + "&e" + question + " &7(type &ccancel &7to abort)"));
    }

    /** Returns the waiting chat prompt of that player (null when none or it expired after 2 minutes). */
    public Consumer<String> takePrompt(UUID id) {
        Prompt pr = prompts.remove(id);
        if (pr == null || pr.expires() < System.currentTimeMillis()) return null;
        return pr.handler();
    }

    public void clearPrompt(UUID id) { prompts.remove(id); }

    /* ---------------- player lifecycle ---------------- */
    public void onJoin(Player p, boolean announce) {
        UUID id = p.getUniqueId();
        boolean first = data.isNew(id);          // must be checked BEFORE touch()
        long last = data.lastSeen(id);
        data.touch(p);
        extras.sessionStart(p);
        int number = data.playerCount();
        long away = last > 0 ? System.currentTimeMillis() - last : 0L;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (getConfig().getBoolean("compass.enabled", true)) ensureCompass(p);
            sidebar.show(p);
            if (announce) extras.welcome(p, first, away, number);
        }, 20L);
    }

    /* ---------------- menu compass ---------------- */
    public ItemStack compass() {
        ConfigurationSection s = getConfig().getConfigurationSection("compass");
        ItemStack it = s != null ? Util.build(s, x -> ph(x, null))
                : Util.item(Material.COMPASS, serverName() + " &fMenu", null, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    public boolean isCompass(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }

    /** Guarantees exactly one menu compass: gives it back if missing, removes duplicates. */
    public void ensureCompass(Player p) {
        PlayerInventory inv = p.getInventory();
        int want = Math.max(0, Math.min(8, getConfig().getInt("compass.slot", 8)));
        if (isCompass(inv.getItem(want))) return;       // normal case: already there, nothing to scan
        boolean found = isCompass(p.getItemOnCursor());
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isCompass(contents[i])) {
                if (found) inv.setItem(i, null); else found = true;
            }
        }
        if (found) return;
        int slot = Math.max(0, Math.min(8, getConfig().getInt("compass.slot", 8)));
        ItemStack existing = inv.getItem(slot);
        if (existing != null && !existing.getType().isAir()) {
            inv.setItem(slot, null);
            for (ItemStack left : inv.addItem(existing).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        inv.setItem(slot, compass());
    }

    private void refreshCompass(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isCompass(contents[i])) inv.setItem(i, compass());
        }
    }
}
