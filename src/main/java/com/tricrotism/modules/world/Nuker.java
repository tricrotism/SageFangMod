package com.tricrotism.modules.world;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.rotation.RotationManager;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Breaks the blocks around the player by packet, without raytracing to any of them.
 * <p>
 * One module against three separate claims, each with its own slider, because they fail
 * independently and a run wants to move one at a time:
 * <ul>
 *   <li>{@link #radius} past the block interaction range makes the dig unreachable.</li>
 *   <li>{@link #perTick} past what a client can do in one tick makes the burst impossible.</li>
 *   <li>{@link #rotate} off makes every dig name a block the transmitted rotation was not on.</li>
 * </ul>
 * With all three at their conservative end this is an ordinary, if fast, miner, which is the
 * false-positive case worth having: a check that speaks here speaks about the wrong thing.
 * <p>
 * The dig packets go straight to the connection rather than through {@code MultiPlayerGameMode},
 * whose own range and raytrace gates would refuse exactly the packets this exists to send.
 */
public final class Nuker extends Module {

    public static final Nuker instance = new Nuker();

    /**
     * Above the aura so a mining run keeps its rotation while entities wander past.
     */
    private static final int ROTATION_PRIORITY = 120;

    private final Settings.Decimal radius =
        decimal("Radius", "radius", "Blocks from the eye to dig within", 4.0, 1.0, 8.0);
    private final Settings.Int perTick =
        integer("Blocks/Tick", "perTick", "Dig starts to send inside one client tick", 1, 1, 10);
    private final Settings.Bool rotate =
        bool("Server Rotations", "rotate", "Aim the transmitted rotation at the block being dug", false);
    private final Settings.Bool finish =
        bool("Send Finish", "finish", "Follow each start with a finish rather than starting only", true);
    private final Settings.Bool swing =
        bool("Swing", "swing", "Send the arm swing alongside each dig", true);

    private final List<BlockPos> candidates = new ArrayList<>();
    private int digs;

    private Nuker() {
        super("nuker", "Nuker", "Break surrounding blocks by packet; sweeps block reach, rate and aim.",
            Category.WORLD);
    }

    @Override
    public void onActivate() {
        digs = 0;
        TestLog.event("nuker_enable", "radius", radius.get(), "perTick", perTick.get(),
            "rotate", rotate.get(), "finish", finish.get());
    }

    @Override
    public void onDeactivate() {
        candidates.clear();
        TestLog.event("nuker_disable", "digs", digs);
    }

    /**
     * Runs on the pre-tick so a rotation request lands before {@code sendPosition()} transmits it,
     * which matters here: a dig sent this tick is judged against the rotation of this tick's
     * movement packet.
     */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.level == null || mc.getConnection() == null) return;

        Vec3 eye = mc.player.getEyePosition();
        collectTargets(eye);
        if (candidates.isEmpty()) return;

        if (rotate.get()) {
            Vec3 centre = Vec3.atCenterOf(candidates.getFirst());
            RotationManager.request(RotationManager.yawTo(eye, centre), RotationManager.pitchTo(eye, centre),
                ROTATION_PRIORITY);
        }

        int sent = Math.min(perTick.get(), candidates.size());
        for (int i = 0; i < sent; i++) {
            dig(candidates.get(i), eye);
        }
    }

    private void dig(BlockPos pos, Vec3 eye) {
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
        if (finish.get()) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
        }
        if (swing.get()) {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
        digs++;

        TestLog.event("dig",
            "x", pos.getX(), "y", pos.getY(), "z", pos.getZ(),
            "eyeDistance", Math.sqrt(pos.distToCenterSqr(eye)),
            "rotated", rotate.get(),
            "sentYaw", rotate.get() ? RotationManager.yaw() : mc.player.getYRot(),
            "sentPitch", rotate.get() ? RotationManager.pitch() : mc.player.getXRot());
    }

    /**
     * Solid blocks inside the radius, nearest first, so the rotation aims at the one we start on.
     */
    private void collectTargets(Vec3 eye) {
        candidates.clear();

        double reach = radius.get();
        double reachSqr = reach * reach;
        int limit = (int) Math.ceil(reach);
        BlockPos origin = mc.player.blockPosition();

        for (int dx = -limit; dx <= limit; dx++) {
            for (int dy = -limit; dy <= limit; dy++) {
                for (int dz = -limit; dz <= limit; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (pos.distToCenterSqr(eye) > reachSqr) continue;

                    var state = mc.level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;

                    candidates.add(pos);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(eye)));
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Digs: " + digs);
        ImGui.text("In range: " + candidates.size());
    }
}
