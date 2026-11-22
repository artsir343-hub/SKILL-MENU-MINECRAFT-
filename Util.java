package dev.skillsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Util {
    private static final String S = "\u00a7";
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private Util() {}

    /** Colored component (no italic). Supports &9 style codes and &#RRGGBB. */
    public static Component c(String s) {
        return LEGACY.deserialize(s == null ? "" : s).decoration(TextDecoration.ITALIC, false);
    }

    /** Legacy section-sign string (for scoreboard entries). */
    public static String sec(String s) {
        if (s == null) return "";
        Matcher m = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder r = new StringBuilder(S + "x");
            for (char ch : m.group(1).toCharArray()) r.append(S).append(ch);
            m.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(sb);
        return sb.toString().replace("&", S);
    }

    public static String rep(String s, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace(kv[i], kv[i + 1]);
        return s;
    }

    public static Material mat(String name, Material def) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name);
        return (m == null || !m.isItem()) ? def : m;
    }

    public static ItemStack item(Material m, String name, List<String> lore, int amount) {
        if (m == null || !m.isItem()) m = Material.PAPER;
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(amount, 64)));
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (name != null) meta.displayName(c(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> l = new ArrayList<>();
                for (String s : lore) l.add(c(s));
                meta.lore(l);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Build an item from a config section (material, name, lore, amount). */
    public static ItemStack build(ConfigurationSection s, UnaryOperator<String> f) {
        Material m = mat(s.getString("material"), Material.PAPER);
        List<String> lore = new ArrayList<>();
        for (String l : s.getStringList("lore")) lore.add(f.apply(l));
        return item(m, f.apply(s.getString("name", "&f" + pretty(m.name()))), lore, s.getInt("amount", 1));
    }

    public static void addLore(ItemStack it, String... lines) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;
        List<Component> l = new ArrayList<>();
        List<Component> old = meta.lore();
        if (old != null) l.addAll(old);
        for (String s : lines) l.add(c(s));
        meta.lore(l);
        it.setItemMeta(meta);
    }

    public static String pretty(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String w : raw.toLowerCase(Locale.ROOT).split("[_\\s]+")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    public static String money(double d) { return MONEY.format(d); }

    /** 1500 -> 1.5K, 2300000 -> 2.3M ... keeps the sidebar narrow even with huge balances. */
    public static String shortNum(double v) {
        String[] suf = {"", "K", "M", "B", "T", "Qa", "Qi", "Sx"};
        double x = Math.abs(v);
        int i = 0;
        while (x >= 1000 && i < suf.length - 1) {
            x /= 1000;
            i++;
        }
        String s = (i == 0 || x >= 100) ? String.format(Locale.US, "%.0f", x) : String.format(Locale.US, "%.1f", x);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return (v < 0 ? "-" : "") + s + suf[i];
    }

    /** Parses a non-negative finite number, or returns null. */
    public static Double num(String s) {
        try {
            double d = Double.parseDouble(s.trim().replace(",", ""));
            return (Double.isNaN(d) || Double.isInfinite(d) || d < 0) ? null : d;
        } catch (Exception e) {
            return null;
        }
    }

    /** Safe id: a-z 0-9 _ - (max 24). Null if empty. */
    public static String id(String s) {
        if (s == null) return null;
        String r = s.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "_").replaceAll("[^a-z0-9_-]", "");
        if (r.length() > 24) r = r.substring(0, 24);
        return r.isEmpty() ? null : r;
    }

    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s < 60) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
