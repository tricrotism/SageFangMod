package com.tricrotism.modules.testing;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Says, live, which movement checks currently cannot judge this player.
 * <p>
 * The other modules manufacture a refusal; this one reports whether the conditions for it happen to
 * hold, which turns out to be the more useful half. A check that stays silent during a run is
 * ambiguous. It may have been fooled, or it may have declined to look, and those two
 * outcomes want opposite responses. Without this the difference is invisible from the client side
 * and has to be inferred from the absence of an alert, which is not evidence of anything.
 * <p>
 * Each line mirrors a refusal in the server's own logic and is computed from the client's view of
 * the world:
 * <ul>
 *   <li><b>Unloaded chunk</b>: a check rebuilding the world contributes no collision for chunks it
 *       never received, so it abstains rather than reading unknown space as air.</li>
 *   <li><b>Entity support</b>: anything the player could be standing on rather than in is outside
 *       the block model, so a nearby entity abandons the ground question. Computed from the real
 *       box, which is the server's common case; where the server cannot identify an entity it
 *       assumes a 2x2 one instead, and the reach is then far wider than shown here.</li>
 *   <li><b>Approximated shape</b>: stairs, fences, walls, panes and doors are modelled as cubes,
 *       which is safe for a support test and wrong for a penetration one.</li>
 * </ul>
 * <p>
 * <b>An approximation, and deliberately so.</b> This is the client's view, not the server's: it
 * cannot see which chunks the server believes it sent, nor the recently-changed set, nor how the
 * server rounds the box. Treat a line as "this refusal is probably active", never as proof. The
 * value is in noticing that a silent run was a refused run, then confirming against the server.
 */
public final class RefusalMonitor extends Module {

    public static final RefusalMonitor instance = new RefusalMonitor();

    /**
     * The margin the server's support test allows around an entity. Mirrors its constant.
     */
    private static final double SUPPORT_MARGIN = 0.15;

    /**
     * The box the server assumes for an entity whose dimensions it does not know. Far larger than
     * most real entities, so an entity it fails to identify covers a much wider area than this
     * monitor's default reading suggests.
     */
    private static final double UNKNOWN_WIDTH = 2.0;
    private static final double UNKNOWN_HEIGHT = 2.0;

    private boolean unloadedChunk;
    private boolean entitySupport;
    private boolean unknownBoxOnly;
    private boolean approximatedShape;
    private String supportingEntity = "none";
    private int refusedTicks;

    private RefusalMonitor() {
        super("refusalmonitor", "Refusal Monitor",
            "Report which movement checks currently cannot judge you.", Category.LOGGING);
    }

    @Override
    public void onActivate() {
        refusedTicks = 0;
        TestLog.event("refusalmonitor_enable");
    }

    @Override
    public void onDeactivate() {
        TestLog.event("refusalmonitor_disable", "refusedTicks", refusedTicks);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.level == null) return;

        boolean wasRefused = anyRefusal();

        unloadedChunk = !chunkLoadedAround();
        unknownBoxOnly = false;
        entitySupport = findSupportingEntity();
        approximatedShape = touchingApproximatedShape();

        boolean refused = anyRefusal();
        if (refused) refusedTicks++;

        // Only the transitions, since the state holds for long stretches and a line per tick would
        // bury the moment it changed.
        if (refused != wasRefused) {
            TestLog.event("refusal_change",
                "refused", refused,
                "unloadedChunk", unloadedChunk,
                "entitySupport", entitySupport,
                "approximatedShape", approximatedShape,
                "entity", supportingEntity);
        }
    }

    private boolean anyRefusal() {
        return unloadedChunk || entitySupport || approximatedShape;
    }

    private boolean chunkLoadedAround() {
        BlockPos feet = mc.player.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos probe = feet.offset(dx, 0, dz);
                if (!mc.level.hasChunk(probe.getX() >> 4, probe.getZ() >> 4)) return false;
            }
        }
        return true;
    }

    private boolean findSupportingEntity() {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || entity.isRemoved()) continue;

            if (!within(entity, x, y, z, entity.getBbWidth(), entity.getBbHeight())) {
                // The wider envelope the server falls back to when it cannot identify the entity.
                // Reported separately rather than folded in, because assuming the fallback always
                // applies would claim a refusal that usually is not there.
                if (within(entity, x, y, z, UNKNOWN_WIDTH, UNKNOWN_HEIGHT)) {
                    unknownBoxOnly = true;
                    supportingEntity = entity.getType().toShortString() + " (only if untracked)";
                }
                continue;
            }

            unknownBoxOnly = false;
            supportingEntity = entity.getType().toShortString();
            return true;
        }
        if (!unknownBoxOnly) supportingEntity = "none";
        return false;
    }

    /**
     * The server's own support test, parameterised by which box it is using.
     */
    private static boolean within(Entity entity, double x, double y, double z,
                                  double width, double height) {
        double radius = width / 2.0 + SUPPORT_MARGIN;
        if (Math.abs(entity.getX() - x) > radius || Math.abs(entity.getZ() - z) > radius) return false;
        return y >= entity.getY() - SUPPORT_MARGIN && y <= entity.getY() + height + SUPPORT_MARGIN;
    }

    private boolean touchingApproximatedShape() {
        BlockPos feet = mc.player.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState state = mc.level.getBlockState(feet.offset(dx, dy, dz));
                    if (isApproximated(state.getBlock())) return true;
                }
            }
        }
        return false;
    }

    private static boolean isApproximated(Block block) {
        return block instanceof StairBlock
            || block instanceof FenceBlock
            || block instanceof WallBlock
            || block instanceof IronBarsBlock
            || block instanceof DoorBlock;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        line("Unloaded chunk nearby", unloadedChunk);
        line("Entity could be support", entitySupport);
        if (entitySupport || unknownBoxOnly) ImGui.text("    " + supportingEntity);
        line("Approximated shape nearby", approximatedShape);

        ImGui.separator();
        ImGui.text(anyRefusal() ? "REFUSED, a silent check may not have looked" : "Judgeable");
        ImGui.text("Refused ticks: " + refusedTicks);
        ImGui.textDisabled("Client-side estimate; confirm against the server.");
    }

    private static void line(String label, boolean active) {
        ImGui.text((active ? "[x] " : "[ ] ") + label);
    }
}
