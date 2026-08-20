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
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Places blocks by packet, the placement-side counterpart to {@link Nuker}.
 * <p>
 * The three interaction checks all read the placement packet as well as the digging one, but until
 * now only digging was being driven, so half of each check was untested. The same three knobs apply
 * and fail independently: {@link #radius} past the block interaction range, {@link #perTick} past
 * what one client tick can produce, and {@link #rotate} off so the packet names a block the
 * transmitted rotation was never on.
 * <p>
 * A placement has to name a face of an existing block rather than a point in space, so each target
 * is paired with a solid neighbour to place against, which is also what separates this from
 * {@code GrimAirPlace}, whose whole trick is placing where no such neighbour exists.
 */
public final class Scaffold extends Module {

    public static final Scaffold instance = new Scaffold();

    /**
     * Matches {@link Nuker}, so a run driving both keeps one rotation rather than alternating.
     */
    private static final int ROTATION_PRIORITY = 120;

    private final Settings.Mode target =
        mode("Target", "target", "Which air blocks to fill", 0, "Under Feet", "Nearest Air");
    private final Settings.Decimal radius =
        decimal("Radius", "radius", "Blocks from the eye to place within", 4.0, 1.0, 8.0);
    private final Settings.Int perTick =
        integer("Blocks/Tick", "perTick", "Placements to send inside one client tick", 1, 1, 10);
    private final Settings.Bool rotate =
        bool("Server Rotations", "rotate", "Aim the transmitted rotation at the block being placed", false);
    private final Settings.Bool swing =
        bool("Swing", "swing", "Send the arm swing alongside each placement", true);

    private final List<BlockPos> candidates = new ArrayList<>();
    private int placements;

    private Scaffold() {
        super("scaffold", "Scaffold", "Place blocks by packet; sweeps block reach, rate and aim.",
            Category.WORLD);
    }

    @Override
    public void onActivate() {
        placements = 0;
        TestLog.event("scaffold_enable", "target", target.option(), "radius", radius.get(),
            "perTick", perTick.get(), "rotate", rotate.get());
    }

    @Override
    public void onDeactivate() {
        candidates.clear();
        TestLog.event("scaffold_disable", "placements", placements);
    }

    /**
     * Pre-tick, so a rotation request reaches this tick's movement packet rather than the next.
     */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.level == null || mc.getConnection() == null) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof BlockItem)) return;

        Vec3 eye = mc.player.getEyePosition();
        collectTargets(eye);
        if (candidates.isEmpty()) return;

        if (rotate.get()) {
            Vec3 centre = Vec3.atCenterOf(candidates.getFirst());
            RotationManager.request(RotationManager.yawTo(eye, centre), RotationManager.pitchTo(eye, centre),
                ROTATION_PRIORITY);
        }

        int wanted = Math.min(perTick.get(), candidates.size());
        for (int i = 0; i < wanted; i++) {
            place(candidates.get(i), eye);
        }
    }

    private void place(BlockPos pos, Vec3 eye) {
        Direction against = supportFace(pos);
        if (against == null) return;

        BlockPos neighbour = pos.relative(against);
        Vec3 hit = Vec3.atCenterOf(neighbour).add(against.getOpposite().getUnitVec3().scale(0.5));

        mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
            new BlockHitResult(hit, against.getOpposite(), neighbour, false),
            mc.player.containerMenu.getStateId() + 2));
        if (swing.get()) {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
        placements++;

        TestLog.event("place",
            "x", pos.getX(), "y", pos.getY(), "z", pos.getZ(),
            "againstX", neighbour.getX(), "againstY", neighbour.getY(), "againstZ", neighbour.getZ(),
            "eyeDistance", Math.sqrt(neighbour.distToCenterSqr(eye)),
            "rotated", rotate.get(),
            "sentYaw", rotate.get() ? RotationManager.yaw() : mc.player.getYRot(),
            "sentPitch", rotate.get() ? RotationManager.pitch() : mc.player.getXRot());
    }

    /**
     * The direction from {@code pos} toward a solid neighbour to place against, or null when the
     * block is floating in air and there is nothing to build off.
     */
    private Direction supportFace(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (mc.level.getBlockState(pos.relative(direction)).isSolid()) return direction;
        }
        return null;
    }

    private void collectTargets(Vec3 eye) {
        candidates.clear();

        if (target.get() == 0) {
            BlockPos below = mc.player.blockPosition().below();
            if (mc.level.getBlockState(below).canBeReplaced()) candidates.add(below);
            return;
        }

        double reach = radius.get();
        double reachSqr = reach * reach;
        int limit = (int) Math.ceil(reach);
        BlockPos origin = mc.player.blockPosition();

        for (int dx = -limit; dx <= limit; dx++) {
            for (int dy = -limit; dy <= limit; dy++) {
                for (int dz = -limit; dz <= limit; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (pos.distToCenterSqr(eye) > reachSqr) continue;
                    if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
                    if (supportFace(pos) == null) continue;

                    candidates.add(pos);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(eye)));
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Placements: " + placements);
        ImGui.text("In range: " + candidates.size());
    }
}
