package dev.skillsmp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Shop buy/sell, sell chest and auction house logic. */
public final class Economy {
    private final SkillSMP pl;

    public Economy(SkillSMP pl) { this.pl = pl; }

    public static boolean plain(ItemStack it) {
        return it != null && !it.getType().isAir() && !it.hasItemMeta();
    }

    public int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (plain(it) && it.getType() == m) n += it.getAmount();
        }
        return n;
    }

    public void remove(Player p, Material m, int amount) {
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length && amount > 0; i++) {
            ItemStack it = c[i];
            if (!plain(it) || it.getType() != m) continue;
            int take = Math.min(amount, it.getAmount());
            amount -= take;
            if (take == it.getAmount()) c[i] = null; else it.setAmount(it.getAmount() - take);
        }
        p.getInventory().setStorageContents(c);
    }

    /** Gives an item; whatever doesn't fit is dropped at the player's feet. */
    public void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    /* ---------------- shop ---------------- */
    public void buy(Player p, Material m, int amt, double unit) {
        double cost = unit * amt;
        if (!pl.data.take(p.getUniqueId(), cost)) {
            pl.msg(p, "not-enough-money", "%cost%", pl.cur(cost));
            return;
        }
        give(p, new ItemStack(m, amt));
        pl.msg(p, "bought", "%amount%", String.valueOf(amt), "%item%", Util.pretty(m.name()), "%cost%", pl.cur(cost));
    }

    /** amt = Integer.MAX_VALUE sells everything of that material. */
    public void sell(Player p, Material m, int amt, double unit) {
        int n = Math.min(count(p, m), amt);
        if (n <= 0) {
            pl.msg(p, "nothing-to-sell");
            return;
        }
        remove(p, m, n);
        double gain = n * unit;
        pl.data.add(p.getUniqueId(), gain);
        pl.msg(p, "sold", "%amount%", String.valueOf(n), "%item%", Util.pretty(m.name()), "%money%", pl.cur(gain));
    }

    public void sellAll(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getStorageContents();
        double total = 0;
        int items = 0;
        for (int i = 0; i < c.length; i++) {
            ItemStack it = c[i];
            if (!plain(it)) continue;
            double price = pl.sellPrice(it.getType());
            if (price <= 0) continue;
            total += price * it.getAmount();
            items += it.getAmount();
            c[i] = null;
        }
        if (items == 0) {
            pl.msg(p, "nothing-to-sell");
            return;
        }
        inv.setStorageContents(c);
        pl.data.add(p.getUniqueId(), total);
        pl.msg(p, "sold-all", "%amount%", String.valueOf(items), "%money%", pl.cur(total));
    }

    public void sellHand(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        double price = plain(it) ? pl.sellPrice(it.getType()) : 0;
        if (price <= 0) {
            pl.msg(p, "nothing-to-sell");
            return;
        }
        double total = price * it.getAmount();
        int amount = it.getAmount();
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        pl.data.add(p.getUniqueId(), total);
        pl.msg(p, "sold", "%amount%", String.valueOf(amount), "%item%", Util.pretty(it.getType().name()), "%money%", pl.cur(total));
    }

    /** Called when the /sell chest is closed: sells what can be sold, returns the rest. */
    public void sellContents(Player p, Inventory inv) {
        double total = 0;
        int count = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            double price = plain(it) ? pl.sellPrice(it.getType()) : 0;
            if (price > 0) {
                total += price * it.getAmount();
                count += it.getAmount();
            } else {
                give(p, it);
            }
            inv.setItem(i, null);
        }
        if (count > 0) {
            pl.data.add(p.getUniqueId(), total);
            pl.msg(p, "sold-all", "%amount%", String.valueOf(count), "%money%", pl.cur(total));
        }
    }

    /* ---------------- auction house ---------------- */
    public void listItem(Player p, double price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            pl.msg(p, "hold-item");
            return;
        }
        if (price > pl.getConfig().getDouble("economy.auction-max-price", 1_000_000_000)) {
            pl.msg(p, "price-too-high");
            return;
        }
        int mine = 0;
        for (Data.Listing l : pl.data.listings()) if (l.seller().equals(p.getUniqueId())) mine++;
        if (mine >= pl.getConfig().getInt("economy.auction-max-listings", 5) && !pl.isAdmin(p)) {
            pl.msg(p, "listing-limit");
            return;
        }
        ItemStack copy = hand.clone();
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        pl.data.addListing(p.getUniqueId(), p.getName(), price, copy);
        pl.msg(p, "listed", "%money%", pl.cur(price));
    }

    /** Buys a listing, or cancels it when the buyer is the seller. */
    public void buyListing(Player p, int id) {
        Data.Listing l = pl.data.listing(id);
        if (l == null) {
            pl.msg(p, "listing-gone");
            return;
        }
        if (l.seller().equals(p.getUniqueId())) {
            pl.data.removeListing(id);
            give(p, l.item());
            pl.msg(p, "listing-cancelled");
            return;
        }
        if (!pl.data.take(p.getUniqueId(), l.price())) {
            pl.msg(p, "not-enough-money", "%cost%", pl.cur(l.price()));
            return;
        }
        pl.data.removeListing(id);
        double tax = Math.max(0, Math.min(100, pl.getConfig().getDouble("economy.auction-tax", 5)));
        double net = l.price() * (1 - tax / 100.0);
        pl.data.add(l.seller(), net);
        give(p, l.item());
        pl.msg(p, "listing-bought", "%money%", pl.cur(l.price()));
        Player seller = Bukkit.getPlayer(l.seller());
        if (seller != null) pl.msg(seller, "listing-sold", "%player%", p.getName(), "%money%", pl.cur(net));
    }
}
