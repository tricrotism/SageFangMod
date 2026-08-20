package com.tricrotism.modules.misc;

import com.tricrotism.SageFang;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Holds back all inbound (server-to-client) packets while active, then replays
 * them in order on disable. The inbound counterpart to Blink. Capture happens in
 * {@code ConnectionMixin}'s inbound hook; replay re-handles each packet on the
 * client thread. Ported from the Meteor addon's ClientBoundPacketDelayer (the
 * per-type packet picker is dropped, so it delays everything while on).
 */
public final class S2CPacketDelayer extends Module {

    public static final S2CPacketDelayer instance = new S2CPacketDelayer();

    private final Queue<Packet<?>> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean flushing;

    private S2CPacketDelayer() {
        super("s2cpacketdelayer", "S2C Packet Delayer", "Delay incoming packets, replay them on disable.", Category.NETWORK);
    }

    /**
     * Called on the network thread. Queues the inbound packet and reports it captured.
     */
    public boolean captureInbound(Packet<?> packet) {
        if (!isActive() || flushing) return false;
        queue.add(packet);
        return true;
    }

    @Override
    public void onActivate() {
        queue.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onDeactivate() {
        if (queue.isEmpty()) return;
        List<Packet<?>> pending = new ArrayList<>(queue);
        queue.clear();

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            var connection = mc.getConnection();
            if (connection == null) return;
            flushing = true;
            try {
                for (Packet<?> packet : pending) {
                    try {
                        ((Packet<ClientGamePacketListener>) packet).handle(connection);
                    } catch (Exception e) {
                        SageFang.LOGGER.warn("[S2CPacketDelayer] failed to replay {}", packet.getClass().getSimpleName(), e);
                    }
                }
            } finally {
                flushing = false;
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Queued: " + queue.size());
    }
}
