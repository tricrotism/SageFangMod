package com.tricrotism.modules.misc;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameQuitEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

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
        // No player yet means login or configuration. Holding those packets hangs the join, and replay
        // only handles game packets.
        if (Minecraft.getInstance().player == null) return false;
        queue.add(packet);
        return true;
    }

    @Override
    public void onActivate() {
        queue.clear();
    }

    /**
     * Held packets belong to the session that produced them, so a disconnect drops them instead of
     * letting the next {@code onDeactivate} replay one server's world updates into another's
     * connection. Also the only bound on the queue, which otherwise grows at the full inbound packet
     * rate for as long as the module is on.
     */
    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        queue.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onDeactivate() {
        if (queue.isEmpty()) return;

        // Drain inside the task, so a packet captured just before the toggle gets replayed too.
        // Otherwise it sits in the queue until the next onActivate clears it.
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            var connection = mc.getConnection();
            if (connection == null) {
                queue.clear();
                return;
            }
            flushing = true;
            try {
                Packet<?> packet;
                while ((packet = queue.poll()) != null) {
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
        ImGui.textUnformatted("Queued: " + queue.size());
    }
}
