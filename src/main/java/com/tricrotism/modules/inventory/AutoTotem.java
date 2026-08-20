package com.tricrotism.modules.inventory;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/**
 * Replaces a popped totem, on a delay you choose.
 * <p>
 * Two checks meet here and the {@link #method} switch is which one is under test. A window click
 * replaces the totem the way every shipped module does, and does it without a screen ever being
 * open, so it is simultaneously the fastest possible answer to the pop and a click sent while the
 * view is still turning. An offhand swap travels on the digging packet instead, which the reaction
 * measurement also reads but the screen-state one does not, so the two modes separate a check that
 * keys on the timing from one that keys on the rotation.
 * <p>
 * {@link #reactionMs} is the knob to sweep, and it is a client-side delay on purpose. The
 * server-side measurement starts at the acknowledgement of the pop rather than at the pop itself, so
 * network latency is already outside the interval and this slider moves the measured number
 * one-for-one. Walking it up from zero finds the floor directly.
 */
public final class AutoTotem extends Module {

    public static final AutoTotem instance = new AutoTotem();

    /**
     * Vanilla's swap button on a window click; the same one the offhand key produces.
     */
    private static final int OFFHAND_BUTTON = 40;

    /**
     * Past this the pop is stale and answering it measures something else.
     */
    private static final long ABANDON_AFTER_MS = 3_000L;

    private final Settings.Int reactionMs =
        integer("Reaction (ms)", "reactionMs", "Delay between the pop and the replacement", 0, 0, 500);
    private final Settings.Bool closeWithClick =
        bool("Close With Click", "closeWithClick",
            "Send a container close alongside the click, which the silent-click check treats as a "
                + "legitimate boundary and refuses to judge", false);
    private final Settings.Mode method =
        mode("Method", "method", "Which packet carries the replacement", 0,
            "Window Click", "Offhand Swap");

    private boolean hadTotem;
    private long poppedMs;
    private int replacements;
    private long lastReactionMs = -1L;

    private AutoTotem() {
        super("autototem", "Auto Totem", "Replace a popped totem; sweeps the totem reaction floor.",
            Category.COMBAT);
    }

    @Override
    public void onActivate() {
        replacements = 0;
        poppedMs = 0L;
        lastReactionMs = -1L;
        hadTotem = holdingTotem();
        TestLog.event("autototem_enable", "reactionMs", reactionMs.get(), "method", method.option());
    }

    @Override
    public void onDeactivate() {
        poppedMs = 0L;
        TestLog.event("autototem_disable", "replacements", replacements);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;

        boolean holding = holdingTotem();
        long now = System.currentTimeMillis();

        if (hadTotem && !holding && poppedMs == 0L) {
            poppedMs = now;
            TestLog.event("totem_pop", "health", mc.player.getHealth());
        }
        hadTotem = holding;

        if (poppedMs == 0L) return;
        if (now - poppedMs > ABANDON_AFTER_MS) {
            poppedMs = 0L;
            return;
        }
        if (now - poppedMs < reactionMs.get()) return;

        int slot = findTotemSlot();
        if (slot == -1) return;

        if (method.get() == 0) {
            // Shutting a screen and then looking around is ordinary, so a tick containing a close is
            // exempt. The close costs nothing here and buys the whole tick.
            if (closeWithClick.get()) {
                mc.getConnection().send(new ServerboundContainerClosePacket(
                    mc.player.containerMenu.containerId));
            }
            windowClick(slot);
        } else if (!offhandSwap(slot)) {
            return;
        }

        lastReactionMs = now - poppedMs;
        poppedMs = 0L;
        replacements++;

        TestLog.event("totem_replace",
            "reactionMs", lastReactionMs,
            "configuredMs", reactionMs.get(),
            "method", method.option(),
            "slot", slot,
            "yaw", mc.player.getYRot(),
            "pitch", mc.player.getXRot());
    }

    /**
     * The window click, sent with no screen open. Routed through the game mode so the packet carries
     * the menu's own state id and slot deltas rather than a hand-built approximation of them.
     */
    private void windowClick(int slot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, containerSlot(slot),
            OFFHAND_BUTTON, ContainerInput.SWAP, mc.player);
    }

    /**
     * The digging-packet route. It swaps whatever is in hand, so it can only run from the hotbar and
     * has to put the held slot back afterwards.
     */
    private boolean offhandSwap(int slot) {
        if (slot >= Inventory.SELECTION_SIZE) return false;

        int held = mc.player.getInventory().getSelectedSlot();
        if (slot != held) mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        if (slot != held) mc.getConnection().send(new ServerboundSetCarriedItemPacket(held));
        return true;
    }

    private boolean holdingTotem() {
        return mc.player != null
            && mc.player.getItemInHand(InteractionHand.OFF_HAND).is(Items.TOTEM_OF_UNDYING);
    }

    private int findTotemSlot() {
        Inventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            if (inventory.getItem(slot).is(Items.TOTEM_OF_UNDYING)) return slot;
        }
        return -1;
    }

    /**
     * Inventory index to player-menu slot: the hotbar sits after the main grid in the menu.
     */
    private static int containerSlot(int slot) {
        return slot < Inventory.SELECTION_SIZE ? slot + 36 : slot;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Replacements: " + replacements);
        ImGui.text("Last reaction: " + (lastReactionMs < 0 ? "none" : lastReactionMs + " ms"));
    }
}
