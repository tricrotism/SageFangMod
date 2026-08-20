package com.tricrotism.modules.latency;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replies to server transactions incorrectly, on purpose, to confirm that acknowledgement integrity
 * is enforced rather than assumed.
 * <p>
 * A server that tracks outstanding transaction ids in a set and rejects anything unexpected should
 * reject every probe here. One that merely records the most recent id, or treats any pong as
 * satisfying any outstanding transaction, will accept them; that gap is the primitive several
 * shipping cheats are built on, so it is worth proving closed rather than inferring it.
 */
public final class ForgedAck extends Module {

    public static final ForgedAck instance = new ForgedAck();

    private final Settings.Bool duplicate =
        bool("Duplicate", "duplicate", "Answer every transaction twice", false);
    private final Settings.Bool reorder =
        bool("Reorder", "reorder", "Hold a pair of acks and release them backwards", false);
    private final Settings.Int unknownId =
        integer("Unknown Id", "unknownId", "Id used by the forged-id probe", 31337, 1, 100000);

    private final Deque<Integer> pendingReorder = new ArrayDeque<>();
    private final AtomicInteger forged = new AtomicInteger();
    private volatile int lastSeenId;

    private ForgedAck() {
        super("forgedack", "Forged Ack", "Reply to transactions with duplicate, reordered or invented ids.",
            Category.NETWORK);
    }

    @Override
    public void onDeactivate() {
        pendingReorder.clear();
    }

    /**
     * Called from ConnectionMixin on the network thread when a transaction arrives. Vanilla answers
     * it immediately, so anything emitted here is strictly additional traffic.
     */
    public void onPing(ClientboundPingPacket packet) {
        if (!isActive()) return;
        lastSeenId = packet.getId();

        if (duplicate.get()) {
            send(packet.getId(), "duplicate");
        }

        if (reorder.get()) {
            pendingReorder.add(packet.getId());
            if (pendingReorder.size() >= 2) {
                Integer first = pendingReorder.poll();
                Integer second = pendingReorder.poll();
                if (second != null) send(second, "reorder");
                if (first != null) send(first, "reorder");
            }
        }
    }

    private void send(int id, String kind) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        connection.send(new ServerboundPongPacket(id));
        forged.incrementAndGet();
        TestLog.event("forged_ack", "kind", kind, "id", id);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        boolean armed = isActive() && Minecraft.getInstance().getConnection() != null;
        if (!armed) ImGui.beginDisabled();
        if (ImGui.button("Send Unknown Id##faUnknown")) send(unknownId.get(), "unknown_id");
        if (ImGui.isItemHovered()) ImGui.setTooltip("Answer a transaction that was never issued");
        if (!armed) ImGui.endDisabled();

        ImGui.text("Last transaction id: " + lastSeenId);
        ImGui.text("Forged acks sent: " + forged.get());
        ImGui.textDisabled("Vanilla still answers normally; these are extra.");
    }
}
