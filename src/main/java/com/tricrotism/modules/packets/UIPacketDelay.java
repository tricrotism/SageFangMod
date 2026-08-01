package com.tricrotism.modules.packets;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Holds serverbound UI (container click/button) packets while the "Delay UI
 * Packets" option is on, then sends them all at once via {@link #release()}.
 * <p>
 * Captured from the network thread in {@code ConnectionMixin}; released and
 * cleared from the render thread via the settings menu — hence the concurrent
 * queue. {@link #isReleasing()} guards the mixin so released packets aren't
 * re-captured, mirroring Blink's flush flag.
 */
public final class UIPacketDelay {

    private static final Queue<Packet<?>> QUEUE = new ConcurrentLinkedQueue<>();
    @Getter private static volatile boolean releasing;

    private UIPacketDelay() {}

    public static void queue(Packet<?> packet) {
        QUEUE.add(packet);
    }

    public static int size() {
        return QUEUE.size();
    }

    /**
     * Sends every queued packet to the server in capture order, then empties the
     * queue. No-op (queue preserved) if not connected. Returns the number sent.
     */
    public static int release() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return 0;

        int count = 0;
        releasing = true;
        try {
            Packet<?> pkt;
            while ((pkt = QUEUE.poll()) != null) {
                conn.send(pkt);
                count++;
            }
        } finally {
            releasing = false;
        }
        return count;
    }

    /**
     * Discards all queued packets. Returns the number dropped.
     */
    public static int clear() {
        int count = QUEUE.size();
        QUEUE.clear();
        return count;
    }
}
