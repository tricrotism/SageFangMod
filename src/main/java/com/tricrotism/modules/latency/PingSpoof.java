package com.tricrotism.modules.latency;

import com.tricrotism.SageFang;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.utils.ImGuiUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
        // shutdown, not shutdownNow, so acks already held still go out on schedule. Dropping them would
        // leave the server waiting on pings nobody answers.
        if (current != null) current.shutdown();
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

        long heldAt = System.nanoTime();
        var owner = Minecraft.getInstance().getConnection();
        passthrough.add(packet);
        try {
            current.schedule(() -> resend(packet, kind, delay, heldAt, owner), delay, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            passthrough.remove(packet);
            SageFang.LOGGER.error("Ping spoof could not schedule a delayed ack", e);
            return false;
        }
        return true;
    }

    private void resend(Packet<?> packet, String kind, int delay, long heldAt, ClientPacketListener owner) {
        long heldMs = (System.nanoTime() - heldAt) / 1_000_000;
        var connection = Minecraft.getInstance().getConnection();
        // A keep-alive id from the previous session makes the new server disconnect with a timeout.
        if (connection == null || connection != owner) {
            passthrough.remove(packet);
            TestLog.event("ack_dropped", "kind", kind, "delayMs", delay, "heldMs", heldMs);
            return;
        }
        connection.send(packet);
        // With the module off, the outbound hook returns before it removes the passthrough entry.
        passthrough.remove(packet);
        TestLog.event("ack_released", "kind", kind, "delayMs", delay, "heldMs", heldMs);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textUnformatted("Divergence: " + Math.abs(keepAliveDelayMs.get() - pongDelayMs.get()) + " ms");
        if (ImGui.isItemHovered()) {
            ImGuiUtil.setTooltip("Equal delays are the control case; a gap is what probes the cross-check");
        }
        ImGui.textUnformatted("Held: " + heldKeepAlives.get() + " keep-alive, " + heldPongs.get() + " transaction");
    }
}
