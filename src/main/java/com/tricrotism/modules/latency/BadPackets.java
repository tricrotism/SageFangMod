package com.tricrotism.modules.latency;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Emits deliberately invalid serverbound traffic, one probe per category, to confirm the
 * corresponding validity checks actually fire.
 * <p>
 * These are not bypasses. Every probe here is something a vanilla client can never produce, so a
 * server that does not flag them is not validating that shape at all. Each probe is a manual button
 * rather than a loop: a validity check should trip on the first offending packet, so a single one is
 * the whole experiment, and firing continuously only muddies which packet caused which alert.
 */
public final class BadPackets extends Module {

    public static final BadPackets instance = new BadPackets();

    private final Settings.Int floodCount =
        integer("Flood Size", "floodCount", "Packets to send inside one tick", 60, 5, 300);

    private int pendingFlood;

    private BadPackets() {
        super("badpackets", "Bad Packets", "Send invalid serverbound packets to verify validity checks.", Category.NETWORK);
    }

    /**
     * Self-interact: an attack addressed to the sender's own entity id. A vanilla client cannot
     * target itself, so this should be rejected outright.
     */
    private void sendSelfInteract() {
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.send(
            new ServerboundInteractPacket(mc.player.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));
        TestLog.event("badpacket", "kind", "self_interact", "entityId", mc.player.getId());
    }

    /**
     * Held-item slot outside the 0-8 hotbar range. Vanilla clamps to the hotbar, so an out-of-range
     * slot is unreachable through legitimate input.
     */
    private void sendInvalidHeldItem() {
        if (mc.player == null || mc.player.connection == null) return;
        int slot = 99;
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        TestLog.event("badpacket", "kind", "invalid_held_item", "slot", slot);
    }

    /**
     * An attack addressed to an entity id nothing is tracking. Reach and aim checks both look the
     * target up and abandon the judgement when it is absent or its box is unknown, so this is the
     * shape that reaches a combat check and then gives it nothing to measure. A server should still
     * be able to say something about it, if only that the id was never sent to this client.
     */
    private void sendUnknownEntityAttack() {
        if (mc.player == null || mc.player.connection == null) return;
        int id = Integer.MAX_VALUE - 1;
        mc.player.connection.send(
            new ServerboundInteractPacket(id, InteractionHand.MAIN_HAND, Vec3.ZERO, false));
        TestLog.event("badpacket", "kind", "unknown_entity_attack", "entityId", id);
    }

    /**
     * Queues a burst of swings to land inside a single tick, well past any per-tick budget.
     */
    private void queueFlood() {
        pendingFlood = floodCount.get();
        TestLog.event("badpacket", "kind", "packets_per_tick_queued", "count", floodCount.get());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (pendingFlood <= 0) return;
        int count = pendingFlood;
        pendingFlood = 0;

        if (mc.player == null || mc.player.connection == null) return;
        for (int i = 0; i < count; i++) {
            mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
        TestLog.event("badpacket", "kind", "packets_per_tick_sent", "count", count);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        boolean armed = isActive() && mc.player != null;
        if (!armed) ImGui.beginDisabled();

        if (ImGui.button("Self Interact##bpSelf")) sendSelfInteract();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Attack our own entity id");

        if (ImGui.button("Invalid Held Item##bpHeld")) sendInvalidHeldItem();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Select hotbar slot 99");

        if (ImGui.button("Unknown Entity##bpUnknown")) sendUnknownEntityAttack();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Attack an entity id nothing is tracking");

        if (ImGui.button("Packets Per Tick##bpBurst")) queueFlood();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Send the whole burst inside one tick");

        if (!armed) ImGui.endDisabled();
        ImGui.textDisabled("One probe per press; check the alert log after each.");
    }
}
