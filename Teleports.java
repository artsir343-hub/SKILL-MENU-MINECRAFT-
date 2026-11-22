package dev.skillsmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TPA, homes, warps, spawn, back and RTP.
 * Every teleport goes through teleport(): only ONE teleport per player at a time, double taps are ignored
 * and the move itself is async (chunk is loaded first) - this stops the "teleports me again / snaps back" problem.
 */
public final class Teleports {
    public record Request(UUID from, boolean here, long expires) {}

    private static final Set<Material> UNSAFE = Set.of(Material.LAVA, Material.WATER, Material.CACTUS,
            Material.FIRE, Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.POWDER_SNOW);

    private final SkillSMP pl;
    private final Map<UUID, Request> incoming = new HashMap<>();   // key = the player who must accept
    private final Map<UUID, Long> rtpCd = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingTp = new HashMap<>();
    private final Map<UUID, Long> justTp = new HashMap<>();
    private final Map<UUID, Location> lastLoc = new HashMap<>();
    private final Set<UUID> searching = new HashSet<>();

    public Teleports(SkillSMP pl) { this.pl = pl; }

    /* ---------------- TPA ---------------- */
    public Request pending(Player target) {
        Request r = incoming.get(target.getUniqueId());
        if (r != null && r.expires() < System.currentTimeMillis()) {
            incoming.remove(target.getUniqueId());
            return null;
        }
        return r;
    }

    public void request(Player from, Player to, boolean here) {
        if (from.equals(to)) {
            pl.msg(from, "tpa-self");
            return;
        }
        long timeout = pl.getConfig().getInt("tpa.timeout", 60) * 1000L;
        incoming.put(to.getUniqueId(), new Request(from.getUniqueId(), here, System.currentTimeMillis() + timeout));
        pl.msg(from, "tpa-sent", "%player%", to.getName());
        pl.msg(to, here ? "tpahere-received" : "tpa-received", "%player%", from.getName());
        if (pl.getConfig().getBoolean("tpa.auto-open-menu", false)) pl.menus.requests(to);
    }

    public void accept(Player target) {
        Request r = pending(target);
        if (r == null) {
            pl.msg(target, "no-request");
            return;
        }
        incoming.remove(target.getUniqueId());   // removed first, so a double tap can never teleport twice
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            pl.msg(target, "player-offline");
            return;
        }
        pl.msg(target, "tpa-accepted-self");
        pl.msg(from, "tpa-accepted", "%player%", target.getName());
        if (r.here()) teleport(target, from.getLocation()); else teleport(from, target.getLocation());
    }

    public void deny(Player target) {
        Request r = pending(target);
        if (r == null) {
            pl.msg(target, "no-request");
            return;
        }
        incoming.remove(target.getUniqueId());
        pl.msg(target, "tpa-denied-self");
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) pl.msg(from, "tpa-denied", "%player%", target.getName());
    }

    public void cancel(Player from) {
        incoming.values().removeIf(r -> r.from().equals(from.getUniqueId()));
        pl.msg(from, "tpa-cancelled");
    }

    public void forget(UUID id) {
        incoming.remove(id);
        incoming.values().removeIf(r -> r.from().equals(id));
        BukkitTask t = pendingTp.remove(id);
        if (t != null) t.cancel();
        justTp.remove(id);
        lastLoc.remove(id);
        searching.remove(id);
        Long cd = rtpCd.get(id);
        if (cd != null && cd < System.currentTimeMillis()) rtpCd.remove(id);
    }

    /* ---------------- the one place where players are moved ---------------- */
    public void teleport(Player p, Location dest) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long done = justTp.get(id);
        if (done != null && now - done < 2500) return;     // double tap / duplicate event right after a teleport
        BukkitTask old = pendingTp.remove(id);
        if (old != null) old.cancel();                     // a new teleport replaces the waiting one, never two at once

        int warm = pl.getConfig().getInt("teleport.warmup", 3);
        if (warm <= 0 || p.hasPermission("skillsmp.bypass.warmup")) {
            go(p, dest);
            return;
        }
        Location start = p.getLocation();
        pl.msg(p, "tp-warmup", "%seconds%", String.valueOf(warm));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(pl, () -> {
            pendingTp.remove(id);
            if (!p.isOnline()) return;
            Location cur = p.getLocation();
            if (!cur.getWorld().equals(start.getWorld()) || cur.distanceSquared(start) > 1.5) {
                pl.msg(p, "tp-cancelled");
                return;
            }
            go(p, dest);
        }, warm * 20L);
        pendingTp.put(id, task);
    }

    private void go(Player p, Location dest) {
        UUID id = p.getUniqueId();
        lastLoc.put(id, p.getLocation());
        justTp.put(id, System.currentTimeMillis());
        p.teleportAsync(dest).thenAccept(ok -> Bukkit.getScheduler().runTask(pl, () -> {
            if (ok && p.isOnline()) pl.msg(p, "tp-done");
        }));
    }

    /* ---------------- back / spawn / warps ---------------- */
    public void setBack(UUID id, Location l) { lastLoc.put(id, l); }

    public void back(Player p) {
        Location l = lastLoc.get(p.getUniqueId());
        if (l == null) {
            pl.msg(p, "back-none");
            return;
        }
        teleport(p, l);
    }

    public void toSpawn(Player p) {
        Location l = pl.data.spawn();
        if (l == null) l = Bukkit.getWorlds().get(0).getSpawnLocation();
        teleport(p, l);
    }

    public void toWarp(Player p, String name) {
        Location l = pl.data.warps().get(name);
        if (l == null) {
            pl.msg(p, "warp-missing");
            return;
        }
        teleport(p, l);
    }

    /* ---------------- homes ---------------- */
    /** Everyone gets the same number of homes (config homes.limit, default 5). */
    public int maxHomes(Player p) {
        return Math.max(1, Math.min(50, pl.getConfig().getInt("homes.limit", 5)));
    }

    public void setHome(Player p, String raw) {
        String name = Util.id(raw == null || raw.isBlank() ? "home" : raw);
        if (name == null) {
            pl.msg(p, "invalid-name");
            return;
        }
        Map<String, Location> hs = pl.data.homes(p.getUniqueId());
        int max = maxHomes(p);
        if (!hs.containsKey(name) && hs.size() >= max) {
            pl.msg(p, "home-limit", "%max%", String.valueOf(max));
            return;
        }
        pl.data.setHome(p.getUniqueId(), name, p.getLocation());
        pl.msg(p, "home-set", "%name%", name);
    }

    public void toHome(Player p, String raw) {
        String name = Util.id(raw);
        Location l = name == null ? null : pl.data.homes(p.getUniqueId()).get(name);
        if (l == null) {
            pl.msg(p, "home-missing", "%name%", raw);
            return;
        }
        teleport(p, l);
    }

    /* ---------------- RTP ---------------- */
    public void rtp(Player p) {
        UUID id = p.getUniqueId();
        if (searching.contains(id)) return;                // already searching, ignore the second tap
        long now = System.currentTimeMillis();
        Long until = rtpCd.get(id);
        if (until != null && until > now && !p.hasPermission("skillsmp.bypass.cooldown")) {
            pl.msg(p, "rtp-cooldown", "%time%", Util.duration(until - now));
            return;
        }
        double cost = pl.getConfig().getDouble("rtp.cost", 0);
        if (cost > 0 && !pl.data.take(id, cost)) {
            pl.msg(p, "not-enough-money", "%cost%", pl.cur(cost));
            return;
        }
        World w = Bukkit.getWorld(pl.getConfig().getString("rtp.world", "world"));
        if (w == null) w = p.getWorld();
        rtpCd.put(id, now + pl.getConfig().getInt("rtp.cooldown", 300) * 1000L);
        searching.add(id);
        pl.msg(p, "rtp-searching");
        attempt(p, w, 0, cost);
    }

    private void attempt(Player p, World w, int n, double cost) {
        UUID id = p.getUniqueId();
        if (!p.isOnline()) {
            searching.remove(id);
            return;
        }
        if (n >= 12) {
            searching.remove(id);
            if (cost > 0) pl.data.add(id, cost);
            rtpCd.remove(id);
            pl.msg(p, "rtp-failed");
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int min = Math.max(0, pl.getConfig().getInt("rtp.min-radius", 500));
        int max = Math.max(min + 1, pl.getConfig().getInt("rtp.max-radius", 5000));
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = r.nextInt(min, max);
        int x = pl.getConfig().getInt("rtp.center-x", 0) + (int) (Math.cos(ang) * dist);
        int z = pl.getConfig().getInt("rtp.center-z", 0) + (int) (Math.sin(ang) * dist);
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask(pl, () -> {
            if (!p.isOnline()) {
                searching.remove(id);
                return;
            }
            Block b = w.getHighestBlockAt(x, z);
            Block above = b.getRelative(0, 1, 0);
            boolean safe = !b.isLiquid() && !UNSAFE.contains(b.getType()) && !b.isPassable()
                    && above.isPassable() && !above.isLiquid() && b.getRelative(0, 2, 0).isPassable()
                    && w.getWorldBorder().isInside(b.getLocation());
            if (safe) {
                searching.remove(id);
                teleport(p, b.getLocation().add(0.5, 1, 0.5));
            } else {
                attempt(p, w, n + 1, cost);
            }
        }));
    }
}
