package dev.skillsmp;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The dashboard on the right side of the screen. Light: lines are only rewritten when the text changed. */
public final class Sidebar {
    private static final String S = "\u00a7";

    private final SkillSMP pl;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> last = new HashMap<>();
    private String topMoney = "-";
    private String topKills = "-";
    private int tick = 0;

    public Sidebar(SkillSMP pl) { this.pl = pl; }

    public void show(Player p) {
        if (!pl.getConfig().getBoolean("sidebar.enabled", true)) {
            hide(p);
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("skillsmp", Criteria.DUMMY, Util.c(pl.serverName()));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.numberFormat(NumberFormat.blank());   // hides the red numbers
        boards.put(p.getUniqueId(), sb);
        last.remove(p.getUniqueId());
        p.setScoreboard(sb);
        update(p);
    }

    public void hide(Player p) {
        remove(p);
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
        last.remove(p.getUniqueId());
    }

    public void updateAll() {
        if (!pl.getConfig().getBoolean("sidebar.enabled", true)) return;
        if (tick++ % 15 == 0) refreshTop();     // leaderboard leaders only every ~30 seconds
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!boards.containsKey(p.getUniqueId())) show(p); else update(p);
        }
    }

    private void refreshTop() {
        List<UUID> m = pl.data.top("money", 1);
        topMoney = m.isEmpty() ? "-" : pl.data.name(m.get(0)) + " (" + pl.cur(pl.data.value(m.get(0), "money")) + ")";
        List<UUID> k = pl.data.top("kills", 1);
        topKills = k.isEmpty() ? "-" : pl.data.name(k.get(0)) + " (" + (long) pl.data.value(k.get(0), "kills") + ")";
    }

    private void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        Objective o = sb.getObjective("skillsmp");
        if (o == null) return;
        o.displayName(Util.c(pl.serverName()));

        // read every value once per update, not once per line
        UUID id = p.getUniqueId();
        int k = pl.data.kills(id), d = pl.data.deaths(id);
        String[] kv = {
                "%server%", pl.serverName(),
                "%player%", p.getName(),
                "%money%", pl.cur(pl.data.bal(id)),
                "%money_short%", pl.curShort(pl.data.bal(id)),
                "%kills%", String.valueOf(k),
                "%deaths%", String.valueOf(d),
                "%kdr%", d == 0 ? String.valueOf(k) : String.format(Locale.US, "%.2f", (double) k / d),
                "%playtime%", Util.duration(pl.extras.playtime(p)),
                "%bounty%", pl.cur(pl.data.bounty(id)),
                "%online%", String.valueOf(Bukkit.getOnlinePlayers().size()),
                "%top_money%", topMoney,
                "%top_kills%", topKills
        };

        List<String> lines = new ArrayList<>();
        Set<String> used = new HashSet<>();
        String key = pl.getConfig().getBoolean("sidebar.compact", true) ? "sidebar.lines" : "sidebar.full-lines";
        for (String raw : pl.getConfig().getStringList(key)) {
            String e = Util.sec(Util.rep(raw, kv));
            if (e.isEmpty()) e = S + "r";
            while (!used.add(e)) e += S + "r";
            lines.add(e);
        }
        if (lines.equals(last.get(id))) return;
        last.put(id, lines);

        for (String e : new ArrayList<>(sb.getEntries())) sb.resetScores(e);
        int score = lines.size();
        for (String e : lines) o.getScore(e).setScore(score--);
    }
}
