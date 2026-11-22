package dev.skillsmp;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Every GUI of the plugin. All of them are plain chest menus, so Bedrock players can use them through Geyser. */
public final class Menus {
    public record Entry(String id, ItemStack icon) {}

    private static final String[][] SETTINGS = {
            {"server-name", "Name shown in every menu, compass and sidebar"},
            {"sidebar.enabled", "Show the dashboard (true/false)"},
            {"sidebar.compact", "Small dashboard (true) or big one (false)"},
            {"lag.enabled", "Automatic lag clear (true/false)"},
            {"lag.interval", "Seconds between lag clears (300 = 5 min)"},
            {"menus.click-delay", "Ignore taps right after a menu opens (ms) - fixes mobile tap-through"},
            {"menus.page-size", "Items per menu page (27 = shulker box, max 45)"},
            {"homes.limit", "Homes every player gets"},
            {"welcome.first-join.enabled", "Welcome for NEW players (true/false)"},
            {"welcome.first-join.broadcast", "Chat message for everyone when a new player joins"},
            {"welcome.first-join.title", "Big title for new players"},
            {"welcome.first-join.subtitle", "Small title for new players"},
            {"welcome.first-join.sound", "Sound key, example entity.player.levelup (none = no sound)"},
            {"welcome.first-join.money", "Bonus money for new players"},
            {"welcome.first-join.kit", "Kit id given to new players (none = no kit)"},
            {"welcome.join.enabled", "Welcome back for returning players (true/false)"},
            {"welcome.join.title", "Big title for returning players"},
            {"welcome.join.subtitle", "Small title for returning players"},
            {"welcome.join.sound", "Sound key for returning players"},
            {"welcome.join-message", "Join message (default = vanilla, none = hidden)"},
            {"welcome.quit-message", "Quit message (default = vanilla, none = hidden)"},
            {"announcer.enabled", "Auto announcements (true/false)"},
            {"announcer.interval", "Seconds between announcements"},
            {"crates.broadcast-below", "Tell everyone when a reward with this % or less is won"},
            {"crates.legendary-below", "Chance % that counts as LEGENDARY"},
            {"crates.epic-below", "Chance % that counts as EPIC"},
            {"crates.rare-below", "Chance % that counts as RARE"},
            {"rtp.min-radius", "RTP minimum distance"},
            {"rtp.max-radius", "RTP maximum distance"},
            {"rtp.cooldown", "RTP cooldown (seconds)"},
            {"rtp.cost", "RTP cost (money)"},
            {"teleport.warmup", "Teleport warm-up (seconds)"},
            {"tpa.timeout", "TPA request timeout (seconds)"},
            {"economy.starting-balance", "Starting balance of new players"},
            {"economy.kill-reward", "Money for a kill"},
            {"economy.auction-tax", "Auction tax (percent)"},
            {"economy.auction-max-listings", "Max auction listings per player"},
            {"daily.reward", "Daily reward money"},
            {"daily.streak-bonus", "Extra money per streak day"},
            {"bounty.min", "Minimum bounty"}
    };

    private final SkillSMP pl;

    public Menus(SkillSMP pl) { this.pl = pl; }

    /* ======================================================= helpers */
    private ItemStack filler() {
        return Util.item(Util.mat(pl.getConfig().getString("menus.filler", "BLUE_STAINED_GLASS_PANE"),
                Material.BLUE_STAINED_GLASS_PANE), " ", null, 1);
    }

    private ItemStack btn(Material m, String name, String... lore) {
        return Util.item(m, name, Arrays.asList(lore), 1);
    }

    private String title(String name) { return pl.serverName() + " &8\u00bb &7" + name; }

    private ItemStack head(OfflinePlayer o, String name, String... lore) {
        ItemStack it = Util.item(Material.PLAYER_HEAD, name, Arrays.asList(lore), 1);
        if (it.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(o);
            it.setItemMeta(sm);
        }
        return it;
    }

    private boolean adm(Player p) {
        if (pl.isAdmin(p)) return true;
        pl.msg(p, "no-permission");
        p.closeInventory();
        return false;
    }

    private void askNum(Player p, String question, Consumer<Double> then) {
        pl.ask(p, question, s -> {
            Double d = Util.num(s);
            if (d == null) {
                pl.msg(p, "invalid-number");
                return;
            }
            then.accept(d);
        });
    }

    public void pick(Player p, String ttl, List<Entry> entries, int page, Consumer<Entry> onPick,
                     Runnable back, Consumer<Menu> decorate) {
        pickRows(p, ttl, entries, page, onPick, back, decorate, 0);
    }

    /** Generic paginated list. Nav row: 0 back, 3 prev, 5 next, 8 close; slots 1,2,4,6,7 are free for decorate(). */
    public void pickRows(Player p, String ttl, List<Entry> entries, int page, Consumer<Entry> onPick,
                         Runnable back, Consumer<Menu> decorate, int minRows) {
        int per = Math.max(9, Math.min(45, pl.getConfig().getInt("menus.page-size", 27)));   // 27 = a shulker box
        int pages = Math.max(1, (entries.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        int shown = Math.max(0, Math.min(per, entries.size() - pg * per));
        int rows = Math.min(6, (per + 8) / 9 + 1);   // the item rows + 1 navigation row, always the same size
        Menu m = new Menu(Util.c(ttl + (pages > 1 ? " &8(" + (pg + 1) + "/" + pages + ")" : "")), rows);
        for (int i = 0; i < shown; i++) {
            Entry e = entries.get(pg * per + i);
            m.set(i, e.icon(), (who, t) -> onPick.accept(e));
        }
        int nav = (rows - 1) * 9;
        for (int i = shown; i < rows * 9; i++) m.set(i, filler());
        if (entries.isEmpty()) m.set(13, btn(Material.BARRIER, "&cNothing here yet"));
        if (back != null) m.set(nav, btn(Material.ARROW, "&cBack"), (who, t) -> back.run());
        if (pg > 0) {
            m.set(nav + 3, btn(Material.ARROW, "&ePrevious page"),
                    (who, t) -> pickRows(who, ttl, entries, pg - 1, onPick, back, decorate, minRows));
        }
        if (pg < pages - 1) {
            m.set(nav + 5, btn(Material.ARROW, "&eNext page"),
                    (who, t) -> pickRows(who, ttl, entries, pg + 1, onPick, back, decorate, minRows));
        }
        m.set(nav + 8, btn(Material.BARRIER, "&cClose"), (who, t) -> who.closeInventory());
        if (decorate != null) decorate.accept(m);
        m.open(p);
    }

    public void confirm(Player p, String ttl, ItemStack info, String yes, String no, Runnable onYes, Runnable onNo) {
        Menu m = new Menu(Util.c(ttl), 3);
        m.set(13, info);
        m.set(11, btn(Material.LIME_CONCRETE, yes), (who, t) -> {
            who.closeInventory();
            onYes.run();
        });
        m.set(15, btn(Material.RED_CONCRETE, no), (who, t) -> {
            who.closeInventory();
            if (onNo != null) onNo.run();
        });
        m.fill(filler());
        m.open(p);
    }

    public void confirm(Player p, String ttl, ItemStack info, Runnable onYes, Runnable onNo) {
        confirm(p, ttl, info, "&a&lConfirm", "&c&lCancel", onYes, onNo);
    }

    /* ======================================================= main menu (compass) */
    public void main(Player p) {
        ConfigurationSection m = pl.getConfig().getConfigurationSection("menus.main");
        if (m == null) return;
        int rows = Math.max(1, Math.min(6, m.getInt("rows", 3)));
        Menu menu = new Menu(Util.c(pl.ph(m.getString("title", "%server% &8\u00bb &7Menu"), p)), rows);
        menu.guardMs = Math.max(0L, pl.getConfig().getLong("menus.click-delay", 700L));
        ConfigurationSection b = m.getConfigurationSection("buttons");
        if (b != null) {
            for (String id : b.getKeys(false)) {
                ConfigurationSection s = b.getConfigurationSection(id);
                if (s == null || (id.equals("admin") && !pl.isAdmin(p))) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= rows * 9) continue;
                String cmd = s.getString("command", "");
                menu.set(slot, Util.build(s, x -> pl.ph(x, p)), (who, t) -> {
                    if (!cmd.isEmpty()) {
                        who.closeInventory();
                        who.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                    } else {
                        action(who, id);
                    }
                });
            }
        }
        if (m.getBoolean("fill", true)) menu.fill(filler());
        menu.open(p);
    }

    private void action(Player p, String id) {
        switch (id) {
            case "rtp" -> {
                p.closeInventory();
                pl.tp.rtp(p);
            }
            case "tpa" -> tpaPicker(p, false);
            case "homes" -> homes(p, false);
            case "shop" -> shop(p);
            case "kits" -> kits(p);
            case "crates" -> crates(p);
            case "auction" -> auction(p);
            case "top" -> top(p);
            case "warps" -> warps(p);
            case "bounty" -> bounties(p);
            case "daily" -> {
                p.closeInventory();
                pl.extras.daily(p);
            }
            case "spawn" -> {
                p.closeInventory();
                pl.tp.toSpawn(p);
            }
            case "admin" -> admin(p);
            case "close" -> p.closeInventory();
            default -> { }
        }
    }

    /* ======================================================= TPA */
    public void tpaPicker(Player p, boolean here) {
        List<Entry> list = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            list.add(new Entry(o.getName(), head(o, "&b" + o.getName(), "&7Click to send a request")));
        }
        pick(p, title(here ? "Bring a player to you" : "Teleport to a player"), list, 0, e -> {
            Player target = Bukkit.getPlayerExact(e.id());
            p.closeInventory();
            if (target == null) pl.msg(p, "player-offline"); else pl.tp.request(p, target, here);
        }, () -> main(p), m -> {
            int nav = m.size() - 9;
            m.set(nav + 2, btn(Material.ENDER_EYE, here ? "&aMode: &fBring player to me" : "&aMode: &fGo to player",
                    "&7Click to switch mode"), (w, c) -> tpaPicker(w, !here));
            m.set(nav + 6, btn(Material.WRITABLE_BOOK, "&eIncoming requests", "&7Accept or deny"), (w, c) -> requests(w));
        });
    }

    public void requests(Player p) {
        Teleports.Request r = pl.tp.pending(p);
        if (r == null) {
            p.closeInventory();
            pl.msg(p, "no-request");
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        String nm = from == null ? "?" : from.getName();
        ItemStack info = from == null
                ? btn(Material.BARRIER, "&c?")
                : head(from, "&b" + nm, r.here() ? "&7wants you to teleport to them" : "&7wants to teleport to you");
        confirm(p, title("Request from " + nm), info, "&a&lAccept", "&c&lDeny",
                () -> pl.tp.accept(p), () -> pl.tp.deny(p));
    }

    /* ======================================================= homes */
    public void homes(Player p, boolean delete) {
        Map<String, Location> hs = pl.data.homes(p.getUniqueId());
        List<Map.Entry<String, Location>> list = new ArrayList<>(hs.entrySet());
        int limit = pl.tp.maxHomes(p);
        int slots = Math.max(1, Math.min(7, Math.max(limit, list.size())));
        Menu m = new Menu(Util.c(title(delete ? "Delete a home" : "Homes")), 3);
        m.set(4, btn(Material.RED_BED, "&b&lYour homes", "&7Used: &f" + hs.size() + "&7/&f" + limit,
                delete ? "&cDelete mode: click a home to delete it" : "&7Click a bed to teleport"));
        int start = 9 + (9 - slots) / 2;
        for (int i = 0; i < slots; i++) {
            int slot = start + i;
            if (i < list.size()) {
                Map.Entry<String, Location> h = list.get(i);
                Location l = h.getValue();
                String name = h.getKey();
                ItemStack icon = Util.item(Material.RED_BED, "&b&l" + name, List.of(
                        "&7World: &f" + l.getWorld().getName(),
                        "&7XYZ: &f" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ(), "",
                        delete ? "&cClick to delete" : "&eClick to teleport"), 1);
                m.set(slot, icon, (w, t) -> {
                    if (delete) {
                        confirm(w, "&cDelete home " + name + "?", icon, () -> {
                            pl.data.delHome(w.getUniqueId(), name);
                            pl.msg(w, "home-deleted", "%name%", name);
                            homes(w, true);
                        }, () -> homes(w, true));
                    } else {
                        w.closeInventory();
                        pl.tp.toHome(w, name);
                    }
                });
            } else {
                m.set(slot, btn(Material.GRAY_DYE, "&7Empty home slot", "&eClick to set a home here"), (w, t) -> {
                    if (delete) return;
                    pl.ask(w, "Type a name for your new home", s -> pl.tp.setHome(w, s));
                });
            }
        }
        m.set(18, btn(Material.ARROW, "&cBack"), (w, t) -> main(w));
        m.set(22, delete ? btn(Material.RED_BED, "&aBack to teleport mode") : btn(Material.TNT, "&cDelete mode"),
                (w, t) -> homes(w, !delete));
        m.fill(filler());
        m.open(p);
    }

    /* ======================================================= warps */
    public void warps(Player p) {
        List<Entry> list = new ArrayList<>();
        for (String name : pl.data.warps().keySet()) {
            list.add(new Entry(name, Util.item(Util.mat(pl.data.warpIcon(name), Material.ENDER_PEARL),
                    "&b" + name, List.of("&7Click to teleport"), 1)));
        }
        pick(p, title("Warps"), list, 0, e -> {
            p.closeInventory();
            pl.tp.toWarp(p, e.id());
        }, () -> main(p), null);
    }

    /* ======================================================= shop */
    public void shop(Player p) {
        ConfigurationSection cats = pl.shop.getConfigurationSection("categories");
        List<Entry> list = new ArrayList<>();
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                ConfigurationSection cs = cats.getConfigurationSection(id);
                if (cs == null) continue;
                ConfigurationSection items = cs.getConfigurationSection("items");
                int n = items == null ? 0 : items.getKeys(false).size();
                list.add(new Entry(id, Util.item(Util.mat(cs.getString("icon"), Material.CHEST), cs.getString("name", id),
                        List.of("&7Items: &f" + n, "", "&eClick to open"), 1)));
            }
        }
        pick(p, title("Shop"), list, 0, e -> shopCategory(p, e.id()), () -> main(p),
                m -> m.set(m.size() - 9 + 4, btn(Material.HOPPER, "&6Sell items", "&7Open the sell chest",
                        "&7(or use /sell hand, /sell all)"), (w, c) -> sellMenu(w)));
    }

    public void shopCategory(Player p, String cat) {
        ConfigurationSection items = pl.shop.getConfigurationSection("categories." + cat + ".items");
        List<Entry> list = new ArrayList<>();
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(id);
                if (s == null) continue;
                Material mat = Material.matchMaterial(s.getString("material", ""));
                if (mat == null || !mat.isItem()) continue;      // unknown material in shop.yml: hidden
                double buy = s.getDouble("buy", 0), sell = s.getDouble("sell", 0);
                list.add(new Entry(id, Util.item(mat, s.getString("name", "&b" + Util.pretty(mat.name())), List.of(
                        "&7Buy: " + (buy > 0 ? "&a" + pl.cur(buy) : "&8not for sale"),
                        "&7Sell: " + (sell > 0 ? "&6" + pl.cur(sell) : "&8not sellable"), "", "&eClick to select"), 1)));
            }
        }
        String name = pl.shop.getString("categories." + cat + ".name", cat);
        pick(p, title(name), list, 0, e -> shopItem(p, cat, e.id()), () -> shop(p), null);
    }

    public void shopItem(Player p, String cat, String id) {
        ConfigurationSection s = pl.shop.getConfigurationSection("categories." + cat + ".items." + id);
        if (s == null) {
            shopCategory(p, cat);
            return;
        }
        Material mat = Util.mat(s.getString("material"), Material.STONE);
        double buy = s.getDouble("buy", 0), sell = s.getDouble("sell", 0);
        Menu m = new Menu(Util.c(title(Util.pretty(mat.name()))), 3);
        m.set(4, Util.item(mat, s.getString("name", "&b" + Util.pretty(mat.name())), List.of(
                "&7Buy: " + (buy > 0 ? "&a" + pl.cur(buy) : "&8not for sale"),
                "&7Sell: " + (sell > 0 ? "&6" + pl.cur(sell) : "&8not sellable"), "",
                "&7Your balance: &f" + pl.cur(pl.data.bal(p.getUniqueId())),
                "&7You have: &f" + pl.eco.count(p, mat)), 1));
        int[] amts = {1, 16, 64};
        for (int i = 0; i < 3; i++) {
            int a = amts[i];
            if (buy > 0) {
                m.set(10 + i, Util.item(Material.LIME_STAINED_GLASS_PANE, "&aBuy x" + a,
                        List.of("&7Cost: &f" + pl.cur(buy * a)), a), (w, c) -> {
                    pl.eco.buy(w, mat, a, buy);
                    shopItem(w, cat, id);
                });
            }
            if (sell > 0) {
                m.set(14 + i, Util.item(Material.RED_STAINED_GLASS_PANE, "&cSell x" + a,
                        List.of("&7You get: &f" + pl.cur(sell * a)), a), (w, c) -> {
                    pl.eco.sell(w, mat, a, sell);
                    shopItem(w, cat, id);
                });
            }
        }
        if (sell > 0) {
            m.set(22, btn(Material.HOPPER, "&6Sell all", "&7Sells every " + Util.pretty(mat.name()) + " you have"), (w, c) -> {
                pl.eco.sell(w, mat, Integer.MAX_VALUE, sell);
                shopItem(w, cat, id);
            });
        }
        m.set(18, btn(Material.ARROW, "&cBack"), (w, c) -> shopCategory(w, cat));
        m.fill(filler());
        m.open(p);
    }

    /** /sell : put items in the chest, close it, everything sellable is sold. */
    public void sellMenu(Player p) {
        Menu m = new Menu(Util.c(title("Sell &8(put items in, then close)")), 5);
        m.locked = false;
        m.onClose = who -> pl.eco.sellContents(who, m.getInventory());
        m.open(p);
    }

    /* ======================================================= kits */
    public void kits(Player p) {
        ConfigurationSection ks = pl.kits.getConfigurationSection("kits");
        List<Entry> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (ks != null) {
            for (String id : ks.getKeys(false)) {
                ConfigurationSection k = ks.getConfigurationSection(id);
                if (k == null) continue;
                long ready = pl.data.kitReady(p.getUniqueId(), id);
                List<String> lore = new ArrayList<>(k.getStringList("lore"));
                lore.add("");
                double price = k.getDouble("price", 0);
                if (price > 0) lore.add("&7Price: &f" + pl.cur(price));
                if (ready == Long.MAX_VALUE) lore.add("&cAlready claimed (one-time kit)");
                else if (ready > now) lore.add("&cCooldown: &f" + Util.duration(ready - now));
                else lore.add("&aReady to claim");
                lore.add("");
                lore.add("&eClick to preview & claim");
                list.add(new Entry(id, Util.item(Util.mat(k.getString("icon"), Material.CHEST), k.getString("name", id), lore, 1)));
            }
        }
        pick(p, title("Kits"), list, 0, e -> kitPreview(p, e.id()), () -> main(p), null);
    }

    private void kitPreview(Player p, String id) {
        ConfigurationSection k = pl.kits.getConfigurationSection("kits." + id);
        if (k == null) {
            kits(p);
            return;
        }
        List<ItemStack> items = pl.rewards.kitItems(k);
        Menu m = new Menu(Util.c(title(k.getString("name", id))), 6);
        for (int i = 0; i < items.size() && i < 45; i++) m.set(i, items.get(i));
        for (int i = 45; i < 54; i++) m.set(i, filler());
        long now = System.currentTimeMillis();
        long ready = pl.data.kitReady(p.getUniqueId(), id);
        double price = k.getDouble("price", 0);
        String status = ready == Long.MAX_VALUE ? "&cAlready claimed"
                : ready > now ? "&cCooldown: &f" + Util.duration(ready - now) : "&aReady to claim";
        m.set(45, btn(Material.ARROW, "&cBack"), (w, t) -> kits(w));
        m.set(49, btn(Material.LIME_CONCRETE, "&a&lCLAIM KIT", status,
                price > 0 ? "&7Price: &f" + pl.cur(price) : "&7Free", "&7Items: &f" + items.size()), (w, t) -> {
            pl.rewards.claimKit(w, id);
            kitPreview(w, id);
        });
        m.open(p);
    }

    /* ======================================================= crates */
    public void crates(Player p) {
        ConfigurationSection cs = pl.crates.getConfigurationSection("crates");
        List<Entry> list = new ArrayList<>();
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection c = cs.getConfigurationSection(id);
                if (c == null) continue;
                double cost = c.getDouble("cost", 0);
                List<String> lore = new ArrayList<>();
                if (c.getBoolean("key-required", false)) {
                    lore.add("&7Needs a key &8(you have &f" + pl.data.keys(p.getUniqueId(), id) + "&8)");
                } else {
                    lore.add("&7Cost: &f" + (cost > 0 ? pl.cur(cost) : "Free"));
                }
                lore.add("");
                lore.add("&eClick to open");
                list.add(new Entry(id, Util.item(Util.mat(c.getString("icon"), Material.CHEST), c.getString("name", id), lore, 1)));
            }
        }
        pick(p, title("Crates"), list, 0, e -> crateDetail(p, e.id()), () -> main(p), null);
    }

    private void crateDetail(Player p, String id) {
        ConfigurationSection c = pl.crates.getConfigurationSection("crates." + id);
        if (c == null) {
            crates(p);
            return;
        }
        boolean needKey = c.getBoolean("key-required", false);
        double cost = c.getDouble("cost", 0);
        double keyPrice = c.getDouble("key-price", 0);
        int keys = pl.data.keys(p.getUniqueId(), id);
        String how = needKey ? "&7Needs &f1 key &7per open" : "&7Cost: &f" + (cost > 0 ? pl.cur(cost) : "Free") + " &7per open";
        Menu m = new Menu(Util.c(title(c.getString("name", id))), 3);
        m.set(4, Util.item(Util.mat(c.getString("icon"), Material.CHEST), c.getString("name", id),
                List.of("&7Rewards: &f" + pl.rewards.rewards(id).size(), how, "&7Your keys: &f" + keys), 1));
        m.set(10, btn(Material.BOOK, "&e&lPreview rewards", "&7See every reward, its chance", "&7and rarity"),
                (w, t) -> previewCrate(w, id));
        m.set(12, btn(Material.LIME_CONCRETE, "&a&lOPEN x1", how, "&7With animation"), (w, t) -> pl.rewards.open(w, id));
        m.set(13, Util.item(Material.YELLOW_CONCRETE, "&e&lOPEN x5", List.of(how, "&7Instant"), 5),
                (w, t) -> {
                    pl.rewards.openMany(w, id, 5);
                    crateDetail(w, id);
                });
        m.set(14, Util.item(Material.ORANGE_CONCRETE, "&6&lOPEN x10", List.of(how, "&7Instant"), 10),
                (w, t) -> {
                    pl.rewards.openMany(w, id, 10);
                    crateDetail(w, id);
                });
        if (keyPrice > 0) {
            m.set(16, btn(Material.TRIPWIRE_HOOK, "&b&lBuy a key", "&7Price: &f" + pl.cur(keyPrice),
                    "&7You have: &f" + keys), (w, t) -> {
                pl.rewards.buyKey(w, id);
                crateDetail(w, id);
            });
        }
        m.set(18, btn(Material.ARROW, "&cBack"), (w, t) -> crates(w));
        m.fill(filler());
        m.open(p);
    }

    private List<Entry> rewardEntries(String crate) {
        List<Rewards.Reward> rs = pl.rewards.rewards(crate);
        List<Entry> list = new ArrayList<>();
        for (Rewards.Reward r : rs) {
            ItemStack ic = r.icon().clone();
            double pct = pl.rewards.percent(r, rs);
            Util.addLore(ic, "", String.format(Locale.US, "&7Chance: &f%.2f%%", pct), "&7Rarity: " + pl.rewards.rarity(pct));
            list.add(new Entry(r.id(), ic));
        }
        return list;
    }

    private void previewCrate(Player p, String id) {
        pick(p, title("Rewards"), rewardEntries(id), 0, e -> { }, () -> crateDetail(p, id), null);
    }

    /* ======================================================= auction house */
    public void auction(Player p) {
        List<Entry> list = new ArrayList<>();
        for (Data.Listing l : pl.data.listings()) {
            ItemStack icon = l.item().clone();
            boolean own = l.seller().equals(p.getUniqueId());
            Util.addLore(icon, "", "&7Price: &a" + pl.cur(l.price()), "&7Seller: &f" + l.sellerName(), "",
                    own ? "&cClick to cancel &7(item is returned)" : "&eClick to buy");
            list.add(new Entry(String.valueOf(l.id()), icon));
        }
        pick(p, title("Auction House"), list, 0, e -> {
            Data.Listing l = pl.data.listing(Integer.parseInt(e.id()));
            if (l == null) {
                pl.msg(p, "listing-gone");
                auction(p);
                return;
            }
            boolean own = l.seller().equals(p.getUniqueId());
            confirm(p, own ? "&cCancel your listing?" : "&aBuy for " + pl.cur(l.price()) + "?", e.icon(), () -> {
                pl.eco.buyListing(p, l.id());
                auction(p);
            }, () -> auction(p));
        }, () -> main(p), m -> m.set(m.size() - 9 + 4,
                btn(Material.GOLD_NUGGET, "&aSell the item in your hand", "&7Hold an item, then click"),
                (w, c) -> askNum(w, "Price for the item in your hand", price -> {
                    pl.eco.listItem(w, price);
                    auction(w);
                })));
    }

    /* ======================================================= bounties */
    public void bounties(Player p) {
        List<Entry> list = new ArrayList<>();
        for (UUID id : pl.data.top("bounty", 45)) {
            double b = pl.data.bounty(id);
            if (b <= 0) break;
            list.add(new Entry(id.toString(), head(Bukkit.getOfflinePlayer(id), "&c" + pl.data.name(id),
                    "&7Bounty: &6" + pl.cur(b), "", "&7Kill them to collect it")));
        }
        pick(p, title("Bounties"), list, 0, e -> { }, () -> main(p),
                m -> m.set(m.size() - 9 + 4, btn(Material.GOLD_NUGGET, "&aPlace a bounty",
                        "&7Pick a player, set the reward"), (w, c) -> bountyPlace(w)));
    }

    private void bountyPlace(Player p) {
        pickPlayer(p, title("Place a bounty on..."), t -> askNum(p, "Bounty amount on " + t.getName(),
                amount -> {
                    pl.extras.placeBounty(p, t.getUniqueId(), amount);
                    bounties(p);
                }), () -> bounties(p));
    }

    /* ======================================================= leaderboards */
    public void top(Player p) {
        Menu m = new Menu(Util.c(title("Leaderboards")), 3);
        m.set(10, btn(Material.GOLD_INGOT, "&6Richest players", "&7Top 10 by money", "", "&eClick to view"), (w, t) -> topList(w, "money"));
        m.set(12, btn(Material.DIAMOND_SWORD, "&cMost kills", "&7Top 10 by kills", "", "&eClick to view"), (w, t) -> topList(w, "kills"));
        m.set(14, btn(Material.SKELETON_SKULL, "&7Most deaths", "&7Top 10 by deaths", "", "&eClick to view"), (w, t) -> topList(w, "deaths"));
        m.set(16, btn(Material.CLOCK, "&bMost playtime", "&7Top 10 by time played", "", "&eClick to view"), (w, t) -> topList(w, "playtime"));
        m.set(22, btn(Material.ARROW, "&cBack"), (w, t) -> main(w));
        m.fill(filler());
        m.open(p);
    }

    private void topList(Player p, String field) {
        String label = switch (field) {
            case "money" -> "Richest";
            case "kills" -> "Kills";
            case "playtime" -> "Playtime";
            default -> "Deaths";
        };
        List<Entry> list = new ArrayList<>();
        int rank = 1;
        for (UUID id : pl.data.top(field, 10)) {
            double v = pl.data.value(id, field);
            String val = field.equals("money") ? pl.cur(v)
                    : field.equals("playtime") ? Util.duration((long) v) : String.valueOf((long) v);
            list.add(new Entry(id.toString(), head(Bukkit.getOfflinePlayer(id), "&e#" + rank + " &b" + pl.data.name(id),
                    "&7" + label + ": &f" + val)));
            rank++;
        }
        pick(p, title("Top " + label), list, 0, e -> { }, () -> top(p), null);
    }

    /* ======================================================= admin panel */
    public void admin(Player p) {
        if (!adm(p)) return;
        Menu m = new Menu(Util.c(title("Admin Panel")), 4);
        m.set(10, btn(Material.GOLD_INGOT, "&6Economy", "&7Give / take / set money"), (w, t) -> adminEconomy(w));
        m.set(11, btn(Material.EMERALD, "&aShop editor", "&7Categories and items"), (w, t) -> adminShop(w));
        m.set(12, btn(Material.CHEST, "&eKits editor", "&7Create / delete kits"), (w, t) -> adminKits(w));
        m.set(13, btn(Material.ENDER_CHEST, "&dCrates editor", "&7Rewards, % chances, keys"), (w, t) -> adminCrates(w));
        m.set(14, btn(Material.FILLED_MAP, "&bWarps & spawn", "&7Create / delete warps"), (w, t) -> adminWarps(w));
        m.set(15, btn(Material.PLAYER_HEAD, "&bPlayers", "&7Heal, gamemode, fly, kick..."), (w, t) -> adminPlayers(w));
        m.set(16, btn(Material.CLOCK, "&bServer tools", "&7Time, weather, clear items..."), (w, t) -> adminServer(w));
        m.set(19, btn(Material.COMPARATOR, "&bSettings", "&7Welcome, announcer, RTP, TPA..."), (w, t) -> adminSettings(w));
        m.set(20, btn(Material.NETHERITE_HELMET, "&cStaff", "&7Add / remove admins", "&8(owner only)"), (w, t) -> adminStaff(w));
        m.set(21, btn(Material.BELL, "&eBroadcast", "&7Send a message to everyone"), (w, t) -> {
            if (!adm(w)) return;
            pl.ask(w, "Message to broadcast", s -> {
                String prefix = pl.getConfig().getString("messages.prefix", "").replace("%server%", pl.serverName());
                Bukkit.broadcast(Util.c(prefix + "&f" + s));
            });
        });
        m.set(22, btn(Material.REDSTONE, "&7Reload files", "&7config, shop, kits, crates"), (w, t) -> {
            if (!adm(w)) return;
            pl.reloadAll();
            w.closeInventory();
            pl.msg(w, "reloaded");
        });
        m.set(31, btn(Material.ARROW, "&cBack"), (w, t) -> main(w));
        m.fill(filler());
        m.open(p);
    }

    /* ---------- pickers ---------- */
    private void pickPlayer(Player p, String ttl, Consumer<Player> then, Runnable back) {
        List<Entry> list = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            list.add(new Entry(o.getName(), head(o, "&b" + o.getName(), "&7Balance: &f" + pl.cur(pl.data.bal(o.getUniqueId())))));
        }
        pick(p, ttl, list, 0, e -> {
            Player t = Bukkit.getPlayerExact(e.id());
            if (t == null) pl.msg(p, "player-offline"); else then.accept(t);
        }, back, null);
    }

    private void pickCategory(Player p, Consumer<String> then, Runnable back) {
        ConfigurationSection cats = pl.shop.getConfigurationSection("categories");
        List<Entry> list = new ArrayList<>();
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                ConfigurationSection cs = cats.getConfigurationSection(id);
                list.add(new Entry(id, Util.item(Util.mat(cs == null ? null : cs.getString("icon"), Material.CHEST), "&b" + id, null, 1)));
            }
        }
        pick(p, title("Pick a category"), list, 0, e -> then.accept(e.id()), back, null);
    }

    private void pickShopItem(Player p, String cat, Consumer<String> then, Runnable back) {
        ConfigurationSection items = pl.shop.getConfigurationSection("categories." + cat + ".items");
        List<Entry> list = new ArrayList<>();
        if (items != null) {
            for (String id : items.getKeys(false)) {
                Material mat = Util.mat(items.getString(id + ".material"), Material.STONE);
                list.add(new Entry(id, Util.item(mat, "&b" + id, null, 1)));
            }
        }
        pick(p, title("Pick an item"), list, 0, e -> then.accept(e.id()), back, null);
    }

    private void pickKit(Player p, Consumer<String> then, Runnable back) {
        ConfigurationSection ks = pl.kits.getConfigurationSection("kits");
        List<Entry> list = new ArrayList<>();
        if (ks != null) {
            for (String id : ks.getKeys(false)) {
                list.add(new Entry(id, Util.item(Util.mat(ks.getString(id + ".icon"), Material.CHEST), "&e" + id, null, 1)));
            }
        }
        pick(p, title("Pick a kit"), list, 0, e -> then.accept(e.id()), back, null);
    }

    private void pickCrate(Player p, Consumer<String> then, Runnable back) {
        ConfigurationSection cs = pl.crates.getConfigurationSection("crates");
        List<Entry> list = new ArrayList<>();
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                List<Rewards.Reward> rs = pl.rewards.rewards(id);
                list.add(new Entry(id, Util.item(Util.mat(cs.getString(id + ".icon"), Material.CHEST), "&d" + id,
                        List.of("&7Rewards: &f" + rs.size(),
                                "&7Key required: &f" + cs.getBoolean(id + ".key-required", false),
                                "&7Cost: &f" + pl.cur(cs.getDouble(id + ".cost", 0)),
                                "&7Key price: &f" + pl.cur(cs.getDouble(id + ".key-price", 0))), 1)));
            }
        }
        pick(p, title("Pick a crate"), list, 0, e -> then.accept(e.id()), back, null);
    }

    private void pickWarp(Player p, Consumer<String> then, Runnable back) {
        List<Entry> list = new ArrayList<>();
        for (String name : pl.data.warps().keySet()) {
            list.add(new Entry(name, Util.item(Util.mat(pl.data.warpIcon(name), Material.ENDER_PEARL), "&b" + name, null, 1)));
        }
        pick(p, title("Pick a warp"), list, 0, e -> then.accept(e.id()), back, null);
    }

    /* ---------- economy ---------- */
    private void adminEconomy(Player p) {
        if (!adm(p)) return;
        pickPlayer(p, title("Economy &8\u00bb &7pick a player"), t -> ecoActions(p, t), () -> admin(p));
    }

    private void ecoActions(Player p, Player t) {
        if (!adm(p)) return;
        UUID id = t.getUniqueId();
        Menu m = new Menu(Util.c(title("Economy &8\u00bb &b" + t.getName())), 3);
        m.set(4, head(t, "&b" + t.getName(), "&7Balance: &f" + pl.cur(pl.data.bal(id))));
        m.set(10, btn(Material.LIME_DYE, "&aGive money"), (w, c) -> askNum(w, "Amount to GIVE to " + t.getName(), d -> {
            pl.data.add(id, d);
            ecoDone(w, t);
        }));
        m.set(12, btn(Material.RED_DYE, "&cTake money"), (w, c) -> askNum(w, "Amount to TAKE from " + t.getName(), d -> {
            pl.data.setBal(id, pl.data.bal(id) - d);
            ecoDone(w, t);
        }));
        m.set(14, btn(Material.YELLOW_DYE, "&eSet money"), (w, c) -> askNum(w, "New balance for " + t.getName(), d -> {
            pl.data.setBal(id, d);
            ecoDone(w, t);
        }));
        m.set(16, btn(Material.BARRIER, "&4Reset to starting balance"), (w, c) -> {
            pl.data.setBal(id, pl.getConfig().getDouble("economy.starting-balance", 100));
            ecoDone(w, t);
        });
        m.set(22, btn(Material.ARROW, "&cBack"), (w, c) -> adminEconomy(w));
        m.fill(filler());
        m.open(p);
    }

    private void ecoDone(Player admin, Player t) {
        pl.msg(admin, "eco-changed", "%player%", t.getName(), "%money%", pl.cur(pl.data.bal(t.getUniqueId())));
        ecoActions(admin, t);
    }

    /* ---------- shop editor ---------- */
    private void adminShop(Player p) {
        if (!adm(p)) return;
        Menu m = new Menu(Util.c(title("Shop editor")), 3);
        m.set(10, btn(Material.CHEST, "&aAdd category", "&7Hold an item = the icon"), (w, t) -> shopAddCategory(w));
        m.set(11, btn(Material.EMERALD, "&aAdd item", "&7Hold the item first"), (w, t) -> shopAddItem(w));
        m.set(12, btn(Material.GOLD_NUGGET, "&eEdit item prices"), (w, t) -> shopEditItem(w));
        m.set(14, btn(Material.REDSTONE, "&cRemove item"), (w, t) -> shopRemoveItem(w));
        m.set(15, btn(Material.TNT, "&cRemove category"), (w, t) -> shopRemoveCategory(w));
        m.set(22, btn(Material.ARROW, "&cBack"), (w, t) -> admin(w));
        m.fill(filler());
        m.open(p);
    }

    private void shopAddCategory(Player p) {
        pl.ask(p, "New category id (example: blocks)", s -> {
            String id = Util.id(s);
            if (id == null) {
                pl.msg(p, "invalid-name");
                return;
            }
            Material ic = p.getInventory().getItemInMainHand().getType();
            pl.shop.set("categories." + id + ".icon", (ic.isAir() ? Material.CHEST : ic).name());
            pl.shop.set("categories." + id + ".name", "&b" + Util.pretty(id));
            pl.saveShop();
            pl.msg(p, "shop-saved");
            adminShop(p);
        });
    }

    private void shopAddItem(Player p) {
        Material mat = p.getInventory().getItemInMainHand().getType();
        if (mat.isAir()) {
            pl.msg(p, "hold-item");
            return;
        }
        pickCategory(p, cat -> askNum(p, "BUY price per item (0 = not for sale)",
                buy -> askNum(p, "SELL price per item (0 = can't be sold)", sell -> {
                    String b = "categories." + cat + ".items." + mat.name().toLowerCase(Locale.ROOT);
                    pl.shop.set(b + ".material", mat.name());
                    pl.shop.set(b + ".buy", buy);
                    pl.shop.set(b + ".sell", sell);
                    pl.saveShop();
                    pl.msg(p, "shop-saved");
                    adminShop(p);
                })), () -> adminShop(p));
    }

    private void shopEditItem(Player p) {
        pickCategory(p, cat -> pickShopItem(p, cat, id -> askNum(p, "New BUY price (0 = not for sale)",
                buy -> askNum(p, "New SELL price (0 = can't be sold)", sell -> {
                    String b = "categories." + cat + ".items." + id;
                    pl.shop.set(b + ".buy", buy);
                    pl.shop.set(b + ".sell", sell);
                    pl.saveShop();
                    pl.msg(p, "shop-saved");
                    adminShop(p);
                })), () -> adminShop(p)), () -> adminShop(p));
    }

    private void shopRemoveItem(Player p) {
        pickCategory(p, cat -> pickShopItem(p, cat, id -> confirm(p, "&cRemove " + id + "?", btn(Material.PAPER, "&f" + id), () -> {
            pl.shop.set("categories." + cat + ".items." + id, null);
            pl.saveShop();
            pl.msg(p, "shop-saved");
            adminShop(p);
        }, () -> adminShop(p)), () -> adminShop(p)), () -> adminShop(p));
    }

    private void shopRemoveCategory(Player p) {
        pickCategory(p, cat -> confirm(p, "&cRemove category " + cat + "?", btn(Material.PAPER, "&f" + cat), () -> {
            pl.shop.set("categories." + cat, null);
            pl.saveShop();
            pl.msg(p, "shop-saved");
            adminShop(p);
        }, () -> adminShop(p)), () -> adminShop(p));
    }

    /* ---------- kits editor ---------- */
    private void adminKits(Player p) {
        if (!adm(p)) return;
        Menu m = new Menu(Util.c(title("Kits editor")), 3);
        m.set(10, btn(Material.CHEST, "&aCreate kit from my inventory", "&7Copies everything you carry:",
                "&7hotbar, inventory, armor, offhand", "&8(the menu compass is skipped)"), (w, t) -> kitCreate(w));
        m.set(12, btn(Material.GOLD_NUGGET, "&eEdit kit price", "&7Pick a kit, set a new price"), (w, t) -> kitPrice(w));
        m.set(14, btn(Material.CLOCK, "&eEdit kit cooldown", "&7Seconds between claims"), (w, t) -> kitCooldown(w));
        m.set(15, btn(Material.TRIPWIRE_HOOK, "&eToggle one-time kit", "&7Only claimable once per player"), (w, t) -> kitToggleOnce(w));
        m.set(16, btn(Material.TNT, "&cDelete kit"), (w, t) -> kitDelete(w));
        m.set(22, btn(Material.ARROW, "&cBack"), (w, t) -> admin(w));
        m.fill(filler());
        m.open(p);
    }

    private void kitCreate(Player p) {
        pl.ask(p, "New kit id (example: vip)", s -> {
            String id = Util.id(s);
            if (id == null) {
                pl.msg(p, "invalid-name");
                return;
            }
            askNum(p, "Cooldown in seconds (0 = none)", cd -> askNum(p, "Price (0 = free)", price -> {
                List<ItemStack> stacks = new ArrayList<>();
                PlayerInventory inv = p.getInventory();
                for (ItemStack it : inv.getStorageContents()) {
                    if (it != null && !it.getType().isAir() && !pl.isCompass(it)) stacks.add(it.clone());
                }
                for (ItemStack it : inv.getArmorContents()) {
                    if (it != null && !it.getType().isAir()) stacks.add(it.clone());
                }
                ItemStack off = inv.getItemInOffHand();
                if (!off.getType().isAir()) stacks.add(off.clone());
                if (stacks.isEmpty()) {
                    pl.msg(p, "hold-item");
                    return;
                }
                String b = "kits." + id;
                pl.kits.set(b + ".name", "&a" + Util.pretty(id) + " Kit");
                pl.kits.set(b + ".icon", stacks.get(0).getType().name());
                pl.kits.set(b + ".cooldown", Math.round(cd));
                pl.kits.set(b + ".price", price);
                pl.kits.set(b + ".once", false);
                pl.kits.set(b + ".permission", "");
                pl.kits.set(b + ".stacks", stacks);
                pl.saveKits();
                pl.msg(p, "saved");
                adminKits(p);
            }));
        });
    }

    private void kitPrice(Player p) {
        pickKit(p, id -> askNum(p, "New price for " + id + " (0 = free)", price -> {
            pl.kits.set("kits." + id + ".price", price);
            pl.saveKits();
            pl.msg(p, "saved");
            adminKits(p);
        }), () -> adminKits(p));
    }

    private void kitCooldown(Player p) {
        pickKit(p, id -> askNum(p, "New cooldown in seconds for " + id, cd -> {
            pl.kits.set("kits." + id + ".cooldown", Math.round(cd));
            pl.saveKits();
            pl.msg(p, "saved");
            adminKits(p);
        }), () -> adminKits(p));
    }

    private void kitToggleOnce(Player p) {
        pickKit(p, id -> {
            String path = "kits." + id + ".once";
            pl.kits.set(path, !pl.kits.getBoolean(path, false));
            pl.saveKits();
            pl.msg(p, "saved");
            adminKits(p);
        }, () -> adminKits(p));
    }

    private void kitDelete(Player p) {
        pickKit(p, id -> confirm(p, "&cDelete kit " + id + "?", btn(Material.PAPER, "&f" + id), () -> {
            pl.kits.set("kits." + id, null);
            pl.saveKits();
            pl.msg(p, "saved");
            adminKits(p);
        }, () -> adminKits(p)), () -> adminKits(p));
    }

    /* ---------- crates editor ---------- */
    private void adminCrates(Player p) {
        if (!adm(p)) return;
        Menu m = new Menu(Util.c(title("Crates editor")), 4);
        m.set(10, btn(Material.ENDER_CHEST, "&aCreate crate", "&7Hold an item = the icon"), (w, t) -> crateCreate(w));
        m.set(11, btn(Material.DIAMOND, "&aAdd item reward", "&7Hold the item, set its % chance"), (w, t) -> crateAddItem(w));
        m.set(12, btn(Material.GOLD_INGOT, "&aAdd money reward", "&7Money amount + % chance"), (w, t) -> crateAddMoney(w));
        m.set(13, btn(Material.COMMAND_BLOCK, "&aAdd command reward", "&7Runs a command + % chance"), (w, t) -> crateAddCommand(w));
        m.set(14, btn(Material.COMPARATOR, "&eEdit reward chance %", "&7Others rebalance to 100%"), (w, t) -> crateEditChance(w));
        m.set(15, btn(Material.REDSTONE, "&cRemove reward"), (w, t) -> crateRemoveReward(w));
        m.set(16, btn(Material.TNT, "&cDelete crate"), (w, t) -> crateDelete(w));
        m.set(19, btn(Material.TRIPWIRE_HOOK, "&eToggle key required", "&7Key or money to open"), (w, t) -> crateToggleKey(w));
        m.set(20, btn(Material.GOLD_NUGGET, "&eSet open cost", "&7Money per open"), (w, t) -> crateSetCost(w));
        m.set(21, btn(Material.EMERALD, "&eSet key price", "&7Players can buy keys (0 = no)"), (w, t) -> crateSetKeyPrice(w));
        m.set(22, btn(Material.NAME_TAG, "&bGive keys to a player", "&7Pick crate, player, amount"), (w, t) -> crateGiveKeys(w));
        m.set(23, btn(Material.NAME_TAG, "&bRename crate", "&7Colors allowed"), (w, t) -> crateRename(w));
        m.set(24, btn(Material.ITEM_FRAME, "&bSet crate icon", "&7Hold an item first"), (w, t) -> crateSetIcon(w));
        m.set(31, btn(Material.ARROW, "&cBack"), (w, t) -> admin(w));
        m.fill(filler());
        m.open(p);
    }

    private void crateRename(Player p) {
        pickCrate(p, crate -> pl.ask(p, "New name for " + crate + " (colors ok, example &d&lMythic Crate)", name -> {
            pl.crates.set("crates." + crate + ".name", name);
            pl.saveCrates();
            pl.msg(p, "saved");
            adminCrates(p);
        }), () -> adminCrates(p));
    }

    private void crateSetIcon(Player p) {
        Material held = p.getInventory().getItemInMainHand().getType();
        if (held.isAir()) {
            pl.msg(p, "hold-item");
            return;
        }
        pickCrate(p, crate -> {
            pl.crates.set("crates." + crate + ".icon", held.name());
            pl.saveCrates();
            pl.msg(p, "saved");
            adminCrates(p);
        }, () -> adminCrates(p));
    }

    private void crateCreate(Player p) {
        Material held = p.getInventory().getItemInMainHand().getType();
        Material icon = held.isAir() ? Material.CHEST : held;
        pl.ask(p, "New crate id (example: vote)", s -> {
            String id = Util.id(s);
            if (id == null) {
                pl.msg(p, "invalid-name");
                return;
            }
            askNum(p, "Cost per open (0 = free)", cost -> {
                String b = "crates." + id;
                pl.crates.set(b + ".name", "&d" + Util.pretty(id) + " Crate");
                pl.crates.set(b + ".icon", icon.name());
                pl.crates.set(b + ".cost", cost);
                pl.crates.set(b + ".key-required", false);
                pl.crates.set(b + ".key-price", 0);
                pl.saveCrates();
                pl.msg(p, "saved");
                adminCrates(p);
            });
        });
    }

    private void crateAddItem(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            pl.msg(p, "hold-item");
            return;
        }
        ItemStack copy = hand.clone();
        pickCrate(p, crate -> askNum(p, "Chance in % for this reward (example 5 = 5%)", pct -> {
            String rid = "r" + System.currentTimeMillis();
            String b = "crates." + crate + ".rewards." + rid;
            pl.crates.set(b + ".type", "stack");
            pl.crates.set(b + ".stack", copy);
            pl.crates.set(b + ".chance", 0);
            pl.rewards.setPercent(crate, rid, pct);
            pl.msg(p, "saved");
            adminCrates(p);
        }), () -> adminCrates(p));
    }

    private void crateAddMoney(Player p) {
        pickCrate(p, crate -> askNum(p, "Money amount", amount -> askNum(p, "Chance in % (example 5 = 5%)", pct -> {
            String rid = "r" + System.currentTimeMillis();
            String b = "crates." + crate + ".rewards." + rid;
            pl.crates.set(b + ".type", "money");
            pl.crates.set(b + ".amount", amount);
            pl.crates.set(b + ".display", "&6" + pl.cur(amount));
            pl.crates.set(b + ".chance", 0);
            pl.rewards.setPercent(crate, rid, pct);
            pl.msg(p, "saved");
            adminCrates(p);
        })), () -> adminCrates(p));
    }

    private void crateAddCommand(Player p) {
        Material held = p.getInventory().getItemInMainHand().getType();
        Material icon = held.isAir() ? Material.NETHER_STAR : held;
        pickCrate(p, crate -> pl.ask(p, "Command to run (without /, use %player%), example: give %player% diamond 5", cmd ->
                pl.ask(p, "Name shown for this reward (colors ok)", display -> askNum(p, "Chance in % (example 5 = 5%)", pct -> {
                    String rid = "r" + System.currentTimeMillis();
                    String b = "crates." + crate + ".rewards." + rid;
                    pl.crates.set(b + ".type", "command");
                    pl.crates.set(b + ".command", cmd);
                    pl.crates.set(b + ".display", display);
                    pl.crates.set(b + ".icon", icon.name());
                    pl.crates.set(b + ".chance", 0);
                    pl.rewards.setPercent(crate, rid, pct);
                    pl.msg(p, "saved");
                    adminCrates(p);
                }))), () -> adminCrates(p));
    }

    private void crateEditChance(Player p) {
        pickCrate(p, crate -> pick(p, title("Edit chance %"), rewardEntries(crate), 0,
                e -> askNum(p, "New chance in % for this reward (others rebalance)", pct -> {
                    pl.rewards.setPercent(crate, e.id(), pct);
                    pl.msg(p, "saved");
                    adminCrates(p);
                }), () -> adminCrates(p), null), () -> adminCrates(p));
    }

    private void crateRemoveReward(Player p) {
        pickCrate(p, crate -> pick(p, title("Remove a reward"), rewardEntries(crate), 0,
                e -> confirm(p, "&cRemove this reward?", e.icon(), () -> {
                    pl.crates.set("crates." + crate + ".rewards." + e.id(), null);
                    pl.saveCrates();
                    pl.rewards.balance(crate);
                    pl.msg(p, "saved");
                    adminCrates(p);
                }, () -> adminCrates(p)), () -> adminCrates(p), null), () -> adminCrates(p));
    }

    private void crateToggleKey(Player p) {
        pickCrate(p, crate -> {
            String path = "crates." + crate + ".key-required";
            pl.crates.set(path, !pl.crates.getBoolean(path, false));
            pl.saveCrates();
            pl.msg(p, "saved");
            adminCrates(p);
        }, () -> adminCrates(p));
    }

    private void crateSetCost(Player p) {
        pickCrate(p, crate -> askNum(p, "New cost per open (0 = free)", cost -> {
            pl.crates.set("crates." + crate + ".cost", cost);
            pl.saveCrates();
            pl.msg(p, "saved");
            adminCrates(p);
        }), () -> adminCrates(p));
    }

    private void crateSetKeyPrice(Player p) {
        pickCrate(p, crate -> askNum(p, "Price of ONE key (0 = keys can't be bought)", price -> {
            pl.crates.set("crates." + crate + ".key-price", price);
            pl.saveCrates();
            pl.msg(p, "saved");
            adminCrates(p);
        }), () -> adminCrates(p));
    }

    private void crateGiveKeys(Player p) {
        pickCrate(p, crate -> pickPlayer(p, title("Give keys to..."),
                t -> askNum(p, "How many keys for " + t.getName() + "?", amount -> {
                    int n = (int) Math.round(amount);
                    pl.data.addKeys(t.getUniqueId(), crate, n);
                    pl.msg(p, "keys-given", "%amount%", String.valueOf(n), "%crate%", crate, "%player%", t.getName());
                    adminCrates(p);
                }), () -> adminCrates(p)), () -> adminCrates(p));
    }

    private void crateDelete(Player p) {
        pickCrate(p, crate -> confirm(p, "&cDelete crate " + crate + "?", btn(Material.PAPER, "&f" + crate), () -> {
            pl.crates.set("crates." + crate, null);
            pl.saveCrates();
            pl.msg(p, "saved");
            adminCrates(p);
        }, () -> adminCrates(p)), () -> adminCrates(p));
    }

    /* ---------- warps editor ---------- */
    private void adminWarps(Player p) {
        if (!adm(p)) return;
        Menu m = new Menu(Util.c(title("Warps & spawn")), 3);
        m.set(10, btn(Material.FILLED_MAP, "&aSet warp here", "&7Hold an item = the icon"), (w, t) -> {
            if (!adm(w)) return;
            Material held = w.getInventory().getItemInMainHand().getType();
            Location here = w.getLocation();
            pl.ask(w, "Warp name (example: farm)", s -> {
                String id = Util.id(s);
                if (id == null) {
                    pl.msg(w, "invalid-name");
                    return;
                }
                pl.data.setWarp(id, here, held.isAir() ? "ENDER_PEARL" : held.name());
                pl.msg(w, "warp-set", "%name%", id);
                adminWarps(w);
            });
        });
        m.set(12, btn(Material.TNT, "&cDelete warp"), (w, t) -> pickWarp(w, id -> confirm(w, "&cDelete warp " + id + "?",
                btn(Material.PAPER, "&f" + id), () -> {
                    pl.data.delWarp(id);
                    pl.msg(w, "warp-deleted", "%name%", id);
                    adminWarps(w);
                }, () -> adminWarps(w)), () -> adminWarps(w)));
        m.set(14, btn(Material.BEACON, "&aSet spawn here", "&7Used by /spawn"), (w, t) -> {
            if (!adm(w)) return;
            pl.data.setSpawn(w.getLocation());
            pl.msg(w, "saved");
        });
        m.set(22, btn(Material.ARROW, "&cBack"), (w, t) -> admin(w));
        m.fill(filler());
        m.open(p);
    }

    /* ---------- player tools ---------- */
    private void adminPlayers(Player p) {
        if (!adm(p)) return;
        pickPlayer(p, title("Players &8\u00bb &7pick one"), t -> playerTools(p, t), () -> admin(p));
    }

    private void playerTools(Player p, Player t) {
        if (!adm(p)) return;
        if (!t.isOnline()) {
            adminPlayers(p);
            return;
        }
        UUID id = t.getUniqueId();
        Menu m = new Menu(Util.c(title("Player &8\u00bb &b" + t.getName())), 4);
        m.set(4, head(t, "&b" + t.getName(), "&7Money: &f" + pl.cur(pl.data.bal(id)),
                "&7Kills: &c" + pl.data.kills(id) + " &7Deaths: &f" + pl.data.deaths(id),
                "&7Bounty: &6" + pl.cur(pl.data.bounty(id)), "&7Playtime: &f" + Util.duration(pl.extras.playtime(t))));
        m.set(10, btn(Material.ENDER_PEARL, "&bTeleport to player"), (w, c) -> {
            if (!t.isOnline()) return;
            w.closeInventory();
            w.teleport(t);
        });
        m.set(11, btn(Material.ENDER_EYE, "&bBring player to me"), (w, c) -> {
            if (!t.isOnline()) return;
            w.closeInventory();
            t.teleport(w);
        });
        m.set(12, btn(Material.GOLDEN_APPLE, "&aHeal & feed"), (w, c) -> {
            if (!t.isOnline()) return;
            pl.extras.heal(t);
            pl.msg(w, "saved");
        });
        m.set(13, btn(Material.COMMAND_BLOCK, "&eGamemode: &f" + t.getGameMode().name(), "&7Click to cycle"), (w, c) -> {
            if (!t.isOnline()) return;
            GameMode gm = pl.extras.nextMode(t);
            t.setGameMode(gm);
            playerTools(w, t);
        });
        m.set(14, btn(Material.FEATHER, "&bFly: &f" + (t.getAllowFlight() ? "ON" : "OFF"), "&7Click to toggle"), (w, c) -> {
            if (!t.isOnline()) return;
            boolean on = !t.getAllowFlight();
            t.setAllowFlight(on);
            if (!on) t.setFlying(false);
            playerTools(w, t);
        });
        m.set(15, btn(Material.GOLD_BLOCK, "&6Clear bounty"), (w, c) -> {
            pl.data.setBounty(id, 0);
            playerTools(w, t);
        });
        m.set(16, btn(Material.BARRIER, "&cKick player"), (w, c) -> confirm(w, "&cKick " + t.getName() + "?",
                btn(Material.PAPER, "&f" + t.getName()), () -> {
                    if (t.isOnline()) t.kick(Util.c("&cYou were kicked by an admin."));
                    adminPlayers(w);
                }, () -> playerTools(w, t)));
        m.set(21, btn(Material.TNT, "&cReset kills & deaths"), (w, c) -> confirm(w, "&cReset stats of " + t.getName() + "?",
                btn(Material.PAPER, "&f" + t.getName()), () -> {
                    pl.data.resetStats(id);
                    playerTools(w, t);
                }, () -> playerTools(w, t)));
        m.set(31, btn(Material.ARROW, "&cBack"), (w, c) -> adminPlayers(w));
        m.fill(filler());
        m.open(p);
    }

    /* ---------- server tools ---------- */
    private void adminServer(Player p) {
        if (!adm(p)) return;
        boolean sidebarOn = pl.getConfig().getBoolean("sidebar.enabled", true);
        boolean annOn = pl.getConfig().getBoolean("announcer.enabled", false);
        Menu m = new Menu(Util.c(title("Server tools")), 3);
        m.set(10, btn(Material.SUNFLOWER, "&eSet day"), (w, c) -> {
            if (adm(w)) w.getWorld().setTime(1000L);
        });
        m.set(11, btn(Material.BLACK_WOOL, "&8Set night"), (w, c) -> {
            if (adm(w)) w.getWorld().setTime(13000L);
        });
        m.set(12, btn(Material.WHITE_WOOL, "&fClear weather"), (w, c) -> {
            if (!adm(w)) return;
            w.getWorld().setStorm(false);
            w.getWorld().setThundering(false);
        });
        m.set(13, btn(Material.WATER_BUCKET, "&bRain"), (w, c) -> {
            if (adm(w)) w.getWorld().setStorm(true);
        });
        m.set(14, btn(Material.HOPPER, "&c&lClear lag now", "&7Removes dropped items and XP orbs",
                "&7(also runs automatically)"), (w, c) -> {
            if (adm(w)) pl.extras.clearLag();
        });
        m.set(15, btn(Material.BEACON, "&aSet spawn here", "&7Used by /spawn"), (w, c) -> {
            if (!adm(w)) return;
            pl.data.setSpawn(w.getLocation());
            pl.msg(w, "saved");
        });
        m.set(16, btn(Material.OAK_SIGN, "&bSidebar: &f" + (sidebarOn ? "ON" : "OFF"), "&7Click to toggle"), (w, c) -> {
            if (!adm(w)) return;
            pl.getConfig().set("sidebar.enabled", !pl.getConfig().getBoolean("sidebar.enabled", true));
            pl.saveConfig();
            pl.refresh();
            adminServer(w);
        });
        boolean compact = pl.getConfig().getBoolean("sidebar.compact", true);
        m.set(17 + 0, btn(Material.PAPER, "&bSidebar style: &f" + (compact ? "SMALL" : "BIG"), "&7Click to switch"), (w, c) -> {
            if (!adm(w)) return;
            pl.getConfig().set("sidebar.compact", !pl.getConfig().getBoolean("sidebar.compact", true));
            pl.saveConfig();
            pl.refresh();
            adminServer(w);
        });
        boolean lagOn = pl.getConfig().getBoolean("lag.enabled", true);
        m.set(20, btn(Material.REPEATER, "&bAuto lag clear: &f" + (lagOn ? "ON" : "OFF"), "&7Every " +
                pl.getConfig().getInt("lag.interval", 300) / 60 + " min", "&7Click to toggle"), (w, c) -> {
            if (!adm(w)) return;
            pl.getConfig().set("lag.enabled", !pl.getConfig().getBoolean("lag.enabled", true));
            pl.saveConfig();
            pl.refresh();
            adminServer(w);
        });
        m.set(21, btn(Material.BELL, "&bAnnouncer: &f" + (annOn ? "ON" : "OFF"), "&7Click to toggle",
                "&7Edit the messages in config.yml"), (w, c) -> {
            if (!adm(w)) return;
            pl.getConfig().set("announcer.enabled", !pl.getConfig().getBoolean("announcer.enabled", false));
            pl.saveConfig();
            pl.refresh();
            adminServer(w);
        });
        m.set(22, btn(Material.ARROW, "&cBack"), (w, c) -> admin(w));
        m.fill(filler());
        m.open(p);
    }

    /* ---------- settings ---------- */
    private void adminSettings(Player p) {
        if (!adm(p)) return;
        List<Entry> list = new ArrayList<>();
        for (String[] s : SETTINGS) {
            list.add(new Entry(s[0], Util.item(Material.PAPER, "&b" + s[0], List.of("&7" + s[1],
                    "&7Current: &f" + pl.getConfig().get(s[0]), "", "&eClick to change"), 1)));
        }
        pick(p, title("Settings"), list, 0, e -> pl.ask(p, "New value for " + e.id(), v -> {
            Object old = pl.getConfig().get(e.id());
            Object val = v;
            if (old instanceof Boolean) {
                String x = v.toLowerCase(Locale.ROOT);
                if (x.equals("true") || x.equals("on") || x.equals("yes")) {
                    val = Boolean.TRUE;
                } else if (x.equals("false") || x.equals("off") || x.equals("no")) {
                    val = Boolean.FALSE;
                } else {
                    pl.msg(p, "invalid-value");
                    return;
                }
            } else if (old instanceof Number) {
                Double d = Util.num(v);
                if (d == null) {
                    pl.msg(p, "invalid-number");
                    return;
                }
                val = old instanceof Integer ? (Object) (int) Math.round(d) : (Object) d;
            } else if (v.equalsIgnoreCase("none")) {
                val = "";
            }
            pl.getConfig().set(e.id(), val);
            pl.saveConfig();
            pl.refresh();
            pl.msg(p, "saved");
            adminSettings(p);
        }), () -> admin(p), null);
    }

    /* ---------- staff ---------- */
    private void adminStaff(Player p) {
        if (!adm(p)) return;
        if (!pl.isOwner(p)) {
            pl.msg(p, "owner-only");
            return;
        }
        List<Entry> list = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            boolean ad = o.getScoreboardTags().contains("admin");
            list.add(new Entry(o.getName(), head(o, "&b" + o.getName(),
                    "&7Admin: " + (ad ? "&aYES" : "&cNO"), "", "&eClick to toggle")));
        }
        pick(p, title("Staff"), list, 0, e -> {
            Player t = Bukkit.getPlayerExact(e.id());
            if (t != null) {
                if (t.getScoreboardTags().contains("admin")) t.removeScoreboardTag("admin"); else t.addScoreboardTag("admin");
            }
            adminStaff(p);
        }, () -> admin(p), null);
    }
}
