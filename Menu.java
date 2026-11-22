package dev.skillsmp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** A chest GUI. Works for Java players and for Bedrock players through Geyser (tap = click). */
public final class Menu implements InventoryHolder {
    @FunctionalInterface
    public interface Click { void on(Player who, ClickType type); }

    private final Inventory inv;
    private final Map<Integer, Click> actions = new HashMap<>();
    public boolean locked = true;           // false = players may move items (sell chest)
    public Consumer<Player> onClose;
    public long openedAt = 0L;              // when the menu was opened
    public long guardMs = 250L;             // clicks in the first guardMs are ignored (mobile tap-through fix)

    public Menu(Component title, int rows) {
        this.inv = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, title);
    }

    public Menu set(int slot, ItemStack it) { return set(slot, it, null); }

    public Menu set(int slot, ItemStack it, Click c) {
        if (slot < 0 || slot >= inv.getSize()) return this;
        inv.setItem(slot, it);
        if (c == null) actions.remove(slot); else actions.put(slot, c);
        return this;
    }

    public Menu fill(ItemStack filler) {
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
        return this;
    }

    public int size() { return inv.getSize(); }

    public void handle(Player who, int slot, ClickType type) {
        Click c = actions.get(slot);
        if (c != null) c.on(who, type);
    }

    public void open(Player p) {
        openedAt = System.currentTimeMillis();
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
