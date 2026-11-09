package com.tricrotism.modules.inventory;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.stream.IntStream;

/**
 * Throws every non-empty inventory stack, one per {@link #delayMs}, then switches itself off.
 * Slots are player-menu indices: hotbar 36-44, main 9-35, armour 5-8, offhand 45.
 */
public final class DropAll extends Module {

    public static final DropAll instance = new DropAll();

    private static final int THROW_STACK = 1;

    private final Settings.Int delayMs =
        integer("Delay (ms)", "delayMs", "Delay between drops, 0 throws everything in one tick", 100, 0, 1000);
    private final Settings.Bool hotbarOnly =
        bool("Hotbar Only", "hotbarOnly", "Only throw the hotbar", false);
    private final Settings.Bool dropArmour =
        bool("Drop Armour", "dropArmour", "Also throw worn armour", false);
    private final Settings.Bool dropOffhand =
        bool("Drop Offhand", "dropOffhand", "Also throw the offhand stack", false);

    private int[] plan = new int[0];
    private int cursor;
    private long lastDropMs;

    private DropAll() {
        super("dropall", "Drop All", "Throw every inventory stack on a delay, then turn off.", Category.UTILITY);
    }

    @Override
    public void onActivate() {
        // One-shot. A run left on by a crash must not pick up again at startup.
        if (mc.player == null) {
            toggle();
            return;
        }
        IntStream slots = IntStream.range(InventoryMenu.USE_ROW_SLOT_START, InventoryMenu.USE_ROW_SLOT_END);
        if (!hotbarOnly.get()) {
            slots = IntStream.concat(slots, IntStream.range(InventoryMenu.INV_SLOT_START, InventoryMenu.INV_SLOT_END));
        }
        if (dropArmour.get()) {
            slots = IntStream.concat(slots, IntStream.range(InventoryMenu.ARMOR_SLOT_START, InventoryMenu.ARMOR_SLOT_END));
        }
        if (dropOffhand.get()) {
            slots = IntStream.concat(slots, IntStream.of(InventoryMenu.SHIELD_SLOT));
        }
        plan = slots.toArray();
        cursor = 0;
        lastDropMs = 0L;
    }

    @Override
    public void onDeactivate() {
        plan = new int[0];
        cursor = 0;
        lastDropMs = 0L;
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        if (isActive()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.gameMode == null) return;

        long now = System.currentTimeMillis();
        if (now - lastDropMs < delayMs.get()) return;

        InventoryMenu menu = mc.player.inventoryMenu;
        // Vanilla ignores a click for a menu that isn't the open one, so wait until it closes.
        if (mc.player.containerMenu != menu) return;
        do {
            while (cursor < plan.length && menu.getSlot(plan[cursor]).getItem().isEmpty()) cursor++;
            if (cursor >= plan.length) {
                toggle();
                return;
            }
            mc.gameMode.handleContainerInput(menu.containerId, plan[cursor++], THROW_STACK, ContainerInput.THROW, mc.player);
        } while (delayMs.get() == 0);

        lastDropMs = now;
    }
}
