package com.tricrotism.modules.latency;

import com.tricrotism.SageFang;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds back keep-alive and transaction acknowledgements by independently configurable amounts,
 * inflating the two latency measurements a server can take of a client.
 * <p>
 * The point of separate sliders is the divergence, not the magnitude. An anticheat that derives RTT
 * from both keep-alives and transactions can cross-check them, and compensating against the smaller
 * of the two makes uniform inflation useless to a cheater. Setting the two delays apart is what
 * probes whether that cross-check actually exists; setting them equal is the control.
 * <p>
 * Delayed packets are re-sent from a scheduler thread rather than hopped onto the client thread, so
 * the delay is what was configured and not the configured delay plus up to a tick of jitter.
 */
public final class PingSpoof extends Module {

    public static final PingSpoof instance = new PingSpoof();

    private final Settings.Int keepAliveDelayMs =
        integer("Keep-Alive Delay (ms)", "keepAliveDelayMs", "Hold keep-alive replies this long", 0, 0, 2000);
    private final Settings.Int pongDelayMs =
        integer("Transaction Delay (ms)", "pongDelayMs", "Hold transaction acks this long", 0, 0, 2000);

    /**
     * Packets we are re-sending, so the outbound hook lets them through the second time.
     */
    private static final Set<Packet<?>> passthrough = Collections.newSetFromMap(new ConcurrentHashMap<>());


    private final AtomicInteger heldKeepAlives = new AtomicInteger();
    private final AtomicInteger heldPongs = new AtomicInteger();

    private ScheduledExecutorService scheduler;

    private PingSpoof() {
        super("pingspoof", "Ping Spoof", "Delay keep-alive and transaction acks independently.", Category.NETWORK);
    }

    @Override
    public void onActivate() {
        heldKeepAlives.set(0);
        heldPongs.set(0);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sagefang-pingspoof");
            t.setDaemon(true);
            return t;
        });
        TestLog.event("pingspoof_enable", "keepAliveDelayMs", keepAliveDelayMs.get(), "pongDelayMs", pongDelayMs.get());
    }

    @Override
    public void onDeactivate() {
        ScheduledExecutorService current = scheduler;
        scheduler = null;
        if (current != null) current.shutdownNow();
        passthrough.clear();
        TestLog.event("pingspoof_disable",
            "heldKeepAlives", heldKeepAlives.get(), "heldPongs", heldPongs.get());
    }

    /**
     * Called from ConnectionMixin on the outbound path. Returns true when the packet has been taken
     * over and the original send should be cancelled.
     */
    public boolean onOutbound(Packet<?> packet) {
        if (!isActive()) return false;
        if (passthrough.remove(packet)) return false;

        int delay;
        String kind;
        if (packet instanceof ServerboundKeepAlivePacket) {
            delay = keepAliveDelayMs.get();
            kind = "keepalive";
        } else if (packet instanceof ServerboundPongPacket) {
            delay = pongDelayMs.get();
            kind = "pong";
        } else {
            return false;
        }
        if (delay <= 0) return false;

        ScheduledExecutorService current = scheduler;
        if (current == null || current.isShutdown()) return false;

        if (kind.equals("keepalive")) heldKeepAlives.incrementAndGet();
        else heldPongs.incrementAndGet();

        TestLog.event("ack_held", "kind", kind, "delayMs", delay);
        passthrough.add(packet);
        try {
            current.schedule(() -> resend(packet, kind, delay), delay, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            passthrough.remove(packet);
            SageFang.LOGGER.error("Ping spoof could not schedule a delayed ack", e);
            return false;
        }
        return true;
    }

    private void resend(Packet<?> packet, String kind, int delay) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            passthrough.remove(packet);
            return;
        }
        connection.send(packet);
        TestLog.event("ack_released", "kind", kind, "delayMs", delay);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Divergence: " + Math.abs(keepAliveDelayMs.get() - pongDelayMs.get()) + " ms");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Equal delays are the control case; a gap is what probes the cross-check");
        }
        ImGui.text("Held: " + heldKeepAlives.get() + " keep-alive, " + heldPongs.get() + " transaction");
    }
}
