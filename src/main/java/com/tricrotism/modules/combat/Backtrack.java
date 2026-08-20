package com.tricrotism.modules.combat;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Holds inbound entity movement back, so every other entity is rendered where it was rather than
 * where it is, and a hit lands on the stale box.
 * <p>
 * This one is aimed squarely at the latency compensation itself rather than at any geometric claim.
 * A rewinding check judges an attack against every position inside a window derived from the
 * player's own round trip, and accepts the hit if <em>any</em> of them makes it legal. That
 * generosity is deliberate and correct, but it is also a budget, and this module spends it.
 * {@link #delayMs} past the window is a hit on a position the server no longer offers as a
 * candidate; below it, the check has no way to tell this apart from an honest connection, which is
 * precisely the point of measuring where the line falls.
 * <p>
 * Only movement is held. Spawns, removals, damage and the player's own position all
 * pass straight through, because a queue that swallows those desynchronises the client into
 * producing flags that have nothing to do with the experiment.
 * <p>
 * Capture runs on the network thread; the release re-handles each packet on the client tick, since
 * the packet handlers touch level state that only the client thread may own.
 */
public final class Backtrack extends Module {

    public static final Backtrack instance = new Backtrack();

    private final Settings.Int delayMs =
        integer("Delay (ms)", "delayMs", "How long entity movement is held before being applied", 200, 0, 1000);

    private final Queue<Held> queue = new ConcurrentLinkedQueue<>();
    private int released;

    private record Held(Packet<?> packet, long receivedMs) {}

    private Backtrack() {
        super("backtrack", "Backtrack", "Delay inbound entity movement; sweeps the latency rewind window.",
            Category.COMBAT);
    }

    /**
     * Called on the network thread. Queues entity movement and reports it captured, so the caller
     * cancels its own handling.
     */
    public boolean captureInbound(Packet<?> packet) {
        if (!isActive() || delayMs.get() == 0) return false;
        if (!(packet instanceof ClientboundMoveEntityPacket
            || packet instanceof ClientboundEntityPositionSyncPacket
            || packet instanceof ClientboundTeleportEntityPacket)) {
            return false;
        }

        queue.add(new Held(packet, System.currentTimeMillis()));
        return true;
    }

    @Override
    public void onActivate() {
        released = 0;
        TestLog.event("backtrack_enable", "delayMs", delayMs.get());
    }

    @Override
    public void onDeactivate() {
        releaseUpTo(Long.MAX_VALUE);
        TestLog.event("backtrack_disable", "released", released);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (queue.isEmpty()) return;
        releaseUpTo(System.currentTimeMillis() - delayMs.get());
    }

    /**
     * Applies everything received at or before {@code cutoffMs}, oldest first.
     */
    @SuppressWarnings("unchecked")
    private void releaseUpTo(long cutoffMs) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            queue.clear();
            return;
        }

        Held head;
        while ((head = queue.peek()) != null && head.receivedMs() <= cutoffMs) {
            queue.poll();
            try {
                ((Packet<ClientGamePacketListener>) head.packet()).handle(connection);
                released++;
            } catch (Exception e) {
                SageFang.LOGGER.warn("[Backtrack] failed to apply {}",
                    head.packet().getClass().getSimpleName(), e);
            }
        }
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Held: " + queue.size());
        ImGui.text("Released: " + released);
    }
}
