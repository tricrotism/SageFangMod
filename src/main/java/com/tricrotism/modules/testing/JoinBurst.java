package com.tricrotism.modules.testing;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;

/**
 * Measures how much a player is given for free immediately after joining.
 * <p>
 * A server has to ignore the first moments of a connection. The login burst, the resource pack and
 * the first chunk batch all produce traffic nothing should be judged on, so checks are suspended
 * until a configured wait has elapsed. That suspension is correct and it is also a window, and a
 * window that is the same length every time and starts on an event the player controls is one they
 * can schedule against.
 * <p>
 * This fires deliberately invalid packets on a timer from the moment the world arrives, each stamped
 * with how long after the join it went out. Diffing the stamps against which alerts appeared gives
 * the window's real width as observed from outside, which is the number that matters, not the one
 * in the config, since the clock starts at whatever moment the server considers the join.
 * <p>
 * The point is not that the window should be removed. It is that a fixed grace period is a resource
 * to be spent, and the interesting mitigations are the ones that make it not worth spending: a
 * shorter wait that ends on the first legitimate movement rather than on a timer, or evidence
 * gathered during the window and judged retroactively once it closes.
 */
public final class JoinBurst extends Module {

    public static final JoinBurst instance = new JoinBurst();

    private final Settings.Int duration =
        integer("Duration (ticks)", "duration", "How long after join to keep probing", 120, 20, 600);
    private final Settings.Int every =
        integer("Interval (ticks)", "every", "Ticks between probes", 5, 1, 40);

    private int ticksSinceJoin = -1;
    private int nextProbeAt;
    private int probes;

    private JoinBurst() {
        super("joinburst", "Join Burst", "Probe the post-join exemption window and time its real width.",
            Category.LOGGING);
    }

    @Override
    public void onActivate() {
        probes = 0;
        ticksSinceJoin = -1;
        TestLog.event("joinburst_enable", "duration", duration.get(), "every", every.get());
    }

    @Override
    public void onDeactivate() {
        ticksSinceJoin = -1;
        TestLog.event("joinburst_disable", "probes", probes);
    }

    @EventHandler
    private void onJoin(GameJoinedEvent event) {
        if (!isActive()) return;
        ticksSinceJoin = 0;
        nextProbeAt = 0;
        probes = 0;
        TestLog.event("joinburst_join");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || ticksSinceJoin < 0) return;
        if (mc.player == null || mc.getConnection() == null) return;

        int elapsed = ticksSinceJoin++;
        if (elapsed > duration.get()) {
            ticksSinceJoin = -1;
            TestLog.event("joinburst_done", "probes", probes);
            return;
        }
        if (elapsed < nextProbeAt) return;
        nextProbeAt = elapsed + every.get();

        probe(elapsed);
    }

    /**
     * One round of packets a vanilla client cannot produce. Three shapes rather than one, so a
     * window that closes for some checks before others is visible as a gap between them.
     */
    private void probe(int elapsed) {
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(99));
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            mc.player.blockPosition().above(64), Direction.UP));
        for (int i = 0; i < 8; i++) {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
        probes++;

        TestLog.event("joinburst_probe",
            "ticksSinceJoin", elapsed,
            "millisSinceJoin", elapsed * 50,
            "probe", probes);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        if (ticksSinceJoin < 0) {
            ImGui.text("Idle, fires on the next world join");
        } else {
            ImGui.text("Tick " + ticksSinceJoin + " since join (" + ticksSinceJoin * 50 + " ms)");
        }
        ImGui.text("Probes sent: " + probes);
    }
}
