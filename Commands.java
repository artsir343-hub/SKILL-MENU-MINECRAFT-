package dev.skillsmp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class Commands implements CommandExecutor, TabCompleter {
    private static final Set<String> CONSOLE_OK = Set.of("economy", "skillsmp", "crates", "money", "broadcast", "lagclear");

    private final SkillSMP pl;

    public Commands(SkillSMP pl) { this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        Player p = s instanceof Player pp ? pp : null;
        if (p == null && !CONSOLE_OK.contains(n)) {
            pl.msg(s, "players-only");
            return true;
        }
        switch (n) {
            case "menu" -> pl.menus.main(p);
            case "tpa", "tpahere" -> {
                boolean here = n.equals("tpahere");
                if (a.length == 0) {
                    pl.menus.tpaPicker(p, here);
                    return true;
                }
                Player t = Bukkit.getPlayer(a[0]);
                if (t == null) pl.msg(p, "player-not-found"); else pl.tp.request(p, t, here);
            }
            case "tpaccept" -> pl.tp.accept(p);
            case "tpdeny" -> pl.tp.deny(p);
            case "tpcancel" -> pl.tp.cancel(p);
            case "sethome" -> pl.tp.setHome(p, a.length > 0 ? a[0] : "home");
            case "home" -> {
                if (a.length == 0) pl.menus.homes(p, false); else pl.tp.toHome(p, a[0]);
            }
            case "homes" -> pl.menus.homes(p, false);
            case "delhome" -> delHome(p, a);
            case "kits" -> pl.menus.kits(p);
            case "kit" -> {
                if (a.length == 0) pl.menus.kits(p); else pl.rewards.claimKit(p, a[0].toLowerCase(Locale.ROOT));
            }
            case "crates" -> crates(s, p, a);
            case "rtp" -> pl.tp.rtp(p);
            case "money" -> money(s, p, a);
            case "pay" -> pay(p, a);
            case "shop" -> pl.menus.shop(p);
            case "sell" -> sell(p, a);
            case "ah" -> ah(p, a);
            case "top" -> pl.menus.top(p);
            case "economy" -> economy(s, a);
            case "adminpanel" -> pl.menus.admin(p);
            case "skillsmp" -> plugin(s, a);
            case "warp" -> {
                if (a.length == 0) pl.menus.warps(p); else pl.tp.toWarp(p, a[0].toLowerCase(Locale.ROOT));
            }
            case "spawn" -> pl.tp.toSpawn(p);
            case "setspawn" -> {
                if (!pl.isAdmin(s)) {
                    pl.msg(s, "no-permission");
                } else {
                    pl.data.setSpawn(p.getLocation());
                    pl.msg(p, "saved");
                }
            }
            case "setwarp" -> setWarp(p, a);
            case "delwarp" -> delWarp(p, a);
            case "back" -> pl.tp.back(p);
            case "bounty" -> bounty(p, a);
            case "daily" -> pl.extras.daily(p);
            case "broadcast" -> broadcast(s, a);
            case "lagclear" -> {
                if (pl.isAdmin(s)) pl.extras.clearLag(); else pl.msg(s, "no-permission");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void setWarp(Player p, String[] a) {
        if (!pl.isAdmin(p)) {
            pl.msg(p, "no-permission");
            return;
        }
        String id = a.length > 0 ? Util.id(a[0]) : null;
        if (id == null) {
            pl.msg(p, "usage", "%usage%", "/setwarp <name>");
            return;
        }
        Material held = p.getInventory().getItemInMainHand().getType();
        pl.data.setWarp(id, p.getLocation(), held.isAir() ? "ENDER_PEARL" : held.name());
        pl.msg(p, "warp-set", "%name%", id);
    }

    private void delWarp(Player p, String[] a) {
        if (!pl.isAdmin(p)) {
            pl.msg(p, "no-permission");
            return;
        }
        String id = a.length > 0 ? Util.id(a[0]) : null;
        if (id == null) {
            pl.msg(p, "usage", "%usage%", "/delwarp <name>");
            return;
        }
        if (pl.data.delWarp(id)) pl.msg(p, "warp-deleted", "%name%", id); else pl.msg(p, "warp-missing");
    }

    private void bounty(Player p, String[] a) {
        if (a.length < 2) {
            pl.menus.bounties(p);
            return;
        }
        UUID id = pl.resolve(a[0]);
        Double amt = Util.num(a[1]);
        if (id == null) {
            pl.msg(p, "player-not-found");
            return;
        }
        if (amt == null || amt <= 0) {
            pl.msg(p, "invalid-number");
            return;
        }
        pl.extras.placeBounty(p, id, amt);
    }

    private void broadcast(CommandSender s, String[] a) {
        if (!pl.isAdmin(s)) {
            pl.msg(s, "no-permission");
            return;
        }
        if (a.length == 0) {
            pl.msg(s, "usage", "%usage%", "/broadcast <message>");
            return;
        }
        String prefix = pl.getConfig().getString("messages.prefix", "").replace("%server%", pl.serverName());
        Bukkit.broadcast(Util.c(prefix + "&f" + String.join(" ", a)));
    }

    private void delHome(Player p, String[] a) {
        String h = a.length > 0 ? Util.id(a[0]) : null;
        if (h == null) pl.msg(p, "usage", "%usage%", "/delhome <name>");
        else if (pl.data.delHome(p.getUniqueId(), h)) pl.msg(p, "home-deleted", "%name%", h);
        else pl.msg(p, "home-missing", "%name%", h);
    }

    private void crates(CommandSender s, Player p, String[] a) {
        if (a.length >= 1 && a[0].equalsIgnoreCase("givekey")) {
            if (!pl.isAdmin(s)) {
                pl.msg(s, "no-permission");
                return;
            }
            if (a.length < 4) {
                pl.msg(s, "usage", "%usage%", "/crates givekey <player> <crate> <amount>");
                return;
            }
            UUID id = pl.resolve(a[1]);
            String crate = a[2].toLowerCase(Locale.ROOT);
            Double amt = Util.num(a[3]);
            if (id == null) {
                pl.msg(s, "player-not-found");
                return;
            }
            if (pl.crates.getConfigurationSection("crates." + crate) == null) {
                pl.msg(s, "crate-missing");
                return;
            }
            if (amt == null) {
                pl.msg(s, "invalid-number");
                return;
            }
            pl.data.addKeys(id, crate, (int) Math.round(amt));
            pl.msg(s, "keys-given", "%amount%", String.valueOf(Math.round(amt)), "%crate%", crate, "%player%", pl.data.name(id));
            return;
        }
        if (p == null) {
            pl.msg(s, "players-only");
            return;
        }
        if (a.length >= 1 && a[0].equalsIgnoreCase("keys")) {
            ConfigurationSection cs = pl.crates.getConfigurationSection("crates");
            if (cs != null) {
                for (String id : cs.getKeys(false)) {
                    pl.msg(p, "keys-line", "%crate%", id, "%amount%", String.valueOf(pl.data.keys(p.getUniqueId(), id)));
                }
            }
            return;
        }
        pl.menus.crates(p);
    }

    private void money(CommandSender s, Player p, String[] a) {
        if (a.length == 0) {
            if (p == null) {
                pl.msg(s, "players-only");
                return;
            }
            pl.msg(p, "balance", "%money%", pl.cur(pl.data.bal(p.getUniqueId())));
            return;
        }
        UUID id = pl.resolve(a[0]);
        if (id == null) {
            pl.msg(s, "player-not-found");
            return;
        }
        pl.msg(s, "balance-other", "%player%", pl.data.name(id), "%money%", pl.cur(pl.data.bal(id)));
    }

    private void pay(Player p, String[] a) {
        if (a.length < 2) {
            pl.msg(p, "usage", "%usage%", "/pay <player> <amount>");
            return;
        }
        Player t = Bukkit.getPlayer(a[0]);
        if (t == null) {
            pl.msg(p, "player-not-found");
            return;
        }
        if (t.equals(p)) {
            pl.msg(p, "pay-self");
            return;
        }
        Double d = Util.num(a[1]);
        if (d == null || d <= 0) {
            pl.msg(p, "invalid-number");
            return;
        }
        double min = pl.getConfig().getDouble("economy.pay-min", 1);
        if (d < min) {
            pl.msg(p, "pay-min", "%min%", pl.cur(min));
            return;
        }
        if (!pl.data.take(p.getUniqueId(), d)) {
            pl.msg(p, "not-enough-money", "%cost%", pl.cur(d));
            return;
        }
        pl.data.add(t.getUniqueId(), d);
        pl.msg(p, "pay-sent", "%player%", t.getName(), "%money%", pl.cur(d));
        pl.msg(t, "pay-received", "%player%", p.getName(), "%money%", pl.cur(d));
    }

    private void sell(Player p, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("hand")) pl.eco.sellHand(p);
        else if (a.length > 0 && a[0].equalsIgnoreCase("all")) pl.eco.sellAll(p);
        else pl.menus.sellMenu(p);
    }

    private void ah(Player p, String[] a) {
        if (a.length >= 2 && a[0].equalsIgnoreCase("sell")) {
            Double d = Util.num(a[1]);
            if (d == null || d <= 0) pl.msg(p, "invalid-number"); else pl.eco.listItem(p, d);
        } else {
            pl.menus.auction(p);
        }
    }

    private void economy(CommandSender s, String[] a) {
        if (!pl.isAdmin(s)) {
            pl.msg(s, "no-permission");
            return;
        }
        String usage = "/economy <give|take|set|reset> <player> [amount]";
        if (a.length < 2) {
            pl.msg(s, "usage", "%usage%", usage);
            return;
        }
        UUID id = pl.resolve(a[1]);
        if (id == null) {
            pl.msg(s, "player-not-found");
            return;
        }
        String op = a[0].toLowerCase(Locale.ROOT);
        if (op.equals("reset")) {
            pl.data.setBal(id, pl.getConfig().getDouble("economy.starting-balance", 100));
        } else {
            Double d = a.length >= 3 ? Util.num(a[2]) : null;
            if (d == null) {
                pl.msg(s, "usage", "%usage%", usage);
                return;
            }
            switch (op) {
                case "give" -> pl.data.add(id, d);
                case "take" -> pl.data.setBal(id, pl.data.bal(id) - d);
                case "set" -> pl.data.setBal(id, d);
                default -> {
                    pl.msg(s, "usage", "%usage%", usage);
                    return;
                }
            }
        }
        pl.msg(s, "eco-changed", "%player%", pl.data.name(id), "%money%", pl.cur(pl.data.bal(id)));
    }

    private void plugin(CommandSender s, String[] a) {
        if (!pl.isAdmin(s)) {
            pl.msg(s, "no-permission");
            return;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            pl.reloadAll();
            pl.msg(s, "reloaded");
        } else if (a.length > 1 && a[0].equalsIgnoreCase("compass")) {
            Player t = Bukkit.getPlayer(a[1]);
            if (t == null) pl.msg(s, "player-not-found"); else pl.ensureCompass(t);
        } else {
            pl.msg(s, "usage", "%usage%", "/skillsmp <reload | compass <player>>");
        }
    }

    /* ---------------- tab completion ---------------- */
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            switch (n) {
                case "tpa", "tpahere", "pay", "money" -> players(out);
                case "home", "delhome" -> {
                    if (s instanceof Player p) out.addAll(pl.data.homes(p.getUniqueId()).keySet());
                }
                case "kit" -> {
                    ConfigurationSection k = pl.kits.getConfigurationSection("kits");
                    if (k != null) out.addAll(k.getKeys(false));
                }
                case "economy" -> out.addAll(List.of("give", "take", "set", "reset"));
                case "sell" -> out.addAll(List.of("hand", "all"));
                case "ah" -> out.add("sell");
                case "crates" -> out.addAll(List.of("givekey", "keys"));
                case "warp" -> out.addAll(pl.data.warps().keySet());
                case "delwarp" -> out.addAll(pl.data.warps().keySet());
                case "bounty" -> players(out);
                case "skillsmp" -> out.addAll(List.of("reload", "compass"));
                default -> { }
            }
        } else if (a.length == 2 && (n.equals("economy") || n.equals("crates") || n.equals("skillsmp"))) {
            players(out);
        } else if (a.length == 3 && n.equals("crates")) {
            ConfigurationSection cs = pl.crates.getConfigurationSection("crates");
            if (cs != null) out.addAll(cs.getKeys(false));
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private void players(List<String> out) {
        for (Player o : Bukkit.getOnlinePlayers()) out.add(o.getName());
    }
}
