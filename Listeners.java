package dev.skillsmp;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class Listeners implements Listener {
    private final SkillSMP pl;
    private final Map<UUID, Long> lastOpen = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Long> lastCompassDestroy = new HashMap<>();

    public Listeners(SkillSMP pl) { this.pl = pl; }

    /* ---------------- join / quit / respawn ---------------- */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String jm = pl.getConfig().getString("welcome.join-message", "default");
        if (!jm.equalsIgnoreCase("default")) e.joinMessage(jm.isEmpty() ? null : Util.c(pl.ph(jm, p)));
        pl.onJoin(p, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        String qm = pl.getConfig().getString("welcome.quit-message", "default");
        if (!qm.equalsIgnoreCase("default")) e.quitMessage(qm.isEmpty() ? null : Util.c(pl.ph(qm, p)));
        pl.extras.sessionEnd(p);
        pl.sidebar.remove(p);
        pl.tp.forget(p.getUniqueId());
        pl.clearPrompt(p.getUniqueId());
        lastOpen.remove(p.getUniqueId());
        lastClick.remove(p.getUniqueId());
        lastCompassDestroy.remove(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && pl.getConfig().getBoolean("compass.enabled", true)) pl.ensureCompass(p);
        }, 2L);
    }

    /* ---------------- kills / deaths / compass never drops ---------------- */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(pl::isCompass);
        Player victim = e.getEntity();
        pl.data.addDeath(victim.getUniqueId());
        pl.tp.setBack(victim.getUniqueId(), victim.getLocation());
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            pl.data.addKill(killer.getUniqueId());
            pl.extras.claimBounty(killer, victim);
            double reward = pl.getConfig().getDouble("economy.kill-reward", 0);
            if (reward > 0) {
                pl.data.add(killer.getUniqueId(), reward);
                pl.msg(killer, "kill-reward", "%amount%", pl.cur(reward));
            }
        }
    }

    /* ---------------- compass: open menu on right-click, allow destroy on left-click ---------------- */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!pl.isCompass(e.getItem())) return;

        // MCPE Destroy (Left-Click): let block break normally, block any plugin/WorldEdit crosshair teleport
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            lastCompassDestroy.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
            return;
        }

        if (!e.getAction().isRightClick()) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (now - lastOpen.getOrDefault(p.getUniqueId(), 0L) < 600) return;   // Bedrock can fire it twice
        lastOpen.put(p.getUniqueId(), now);
        pl.menus.main(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompassCrosshairTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.PLUGIN && cause != PlayerTeleportEvent.TeleportCause.COMMAND) {
            return;
        }
        Long t = lastCompassDestroy.get(e.getPlayer().getUniqueId());
        if (t != null && System.currentTimeMillis() - t < 400L) {
            e.setCancelled(true);
        }
    }

    private void interactEntity(Player p, EquipmentSlot hand, org.bukkit.event.Cancellable e) {
        if (hand == null) return;
        if (!pl.isCompass(p.getInventory().getItem(hand))) return;
        e.setCancelled(true);   // stops the compass from being put into item frames / armor stands
        long now = System.currentTimeMillis();
        if (now - lastOpen.getOrDefault(p.getUniqueId(), 0L) < 600) return;
        lastOpen.put(p.getUniqueId(), now);
        pl.menus.main(p);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) { interactEntity(e.getPlayer(), e.getHand(), e); }

    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent e) { interactEntity(e.getPlayer(), e.getHand(), e); }

    /* ---------------- compass can't be dropped, moved or crafted ---------------- */
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (pl.isCompass(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (pl.isCompass(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (pl.isCompass(it)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /* ---------------- menus + compass protection ---------------- */
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();

        if (top.getHolder(false) instanceof Menu m && m.locked) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) {
                long now = System.currentTimeMillis();
                // the finger that tapped the compass is still on the screen when the menu opens:
                // on mobile that tap lands on a button (this was the "random teleport"). Ignore it.
                if (now - m.openedAt < m.guardMs) return;
                Long lc = lastClick.get(p.getUniqueId());
                if (lc != null && now - lc < 200) return;            // double tap
                lastClick.put(p.getUniqueId(), now);
                int slot = e.getRawSlot();
                ClickType type = e.getClick();
                // run next tick: opening another menu inside the click event is flaky (especially with Geyser)
                Bukkit.getScheduler().runTask(pl, () -> m.handle(p, slot, type));
            }
            return;
        }

        ItemStack cur = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        boolean involves = pl.isCompass(cur) || pl.isCompass(cursor);
        if (!involves && e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
            involves = pl.isCompass(p.getInventory().getItem(e.getHotbarButton()));
        }
        if (!involves) return;
        boolean ownInventory = e.getClickedInventory() instanceof PlayerInventory;
        if (!ownInventory || e.isShiftClick()) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder(false) instanceof Menu m && m.locked) {
            e.setCancelled(true);
            return;
        }
        if (pl.isCompass(e.getOldCursor())) {
            for (int slot : e.getRawSlots()) {
                if (slot < top.getSize()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu m && m.onClose != null
                && e.getPlayer() instanceof Player p) {
            m.onClose.accept(p);
        }
    }

    /* ---------------- chat prompts (admin panel, set home, auction price ...) ---------------- */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> handler = pl.takePrompt(e.getPlayer().getUniqueId());
        if (handler == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (text.equalsIgnoreCase("cancel")) {
                pl.msg(p, "prompt-cancelled");
                return;
            }
            handler.accept(text);
        });
    }
}
