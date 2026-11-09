package com.tricrotism.modules.testing;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.ImGuiUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

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
 * the world, over the 0.6 by 1.8 box the server tests movement with:
 * <ul>
 *   <li><b>Unloaded chunk</b>: a check rebuilding the world contributes no collision for chunks it
 *       never received, so it abstains rather than reading unknown space as air.</li>
 *   <li><b>Entity support</b>: standing on top of a boat or shulker is outside the block model, so
 *       the server skips the ground check. Mobs, players and items don't count. When the server
 *       can't identify an entity it assumes a 2x2 one of any type, which reaches much further than
 *       what this line shows.</li>
 *   <li><b>Approximated shape</b>: the server treats fences, walls, panes, beds, cauldrons and the
 *       rest of its list as full cubes. That's safe for a support test and wrong for a penetration
 *       test. Stairs and doors get exact shapes and don't count.</li>
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

    /**
     * The server checks movement against a 0.6 by 1.8 box, whatever pose the player is in.
     */
    private static final double WIDTH = 0.6;
    private static final double HEIGHT = 1.8;

    /**
     * Block name suffixes the server treats as full cubes when the block has collision. Copied from
     * Whiteout's {@code BlockShapes.APPROXIMATED}, so update both together.
     */
    private static final List<String> APPROXIMATED = List.of(
        "_fence", "_fence_gate", "_wall", "_pane", "_bars",
        "_bed", "cauldron", "composter", "hopper", "anvil",
        "_sign", "scaffolding", "lantern", "end_rod", "chain", "conduit",
        "brewing_stand", "enchanting_table", "grindstone", "stonecutter", "lectern",
        "bell", "_pot", "cake", "turtle_egg", "_amethyst_bud", "amethyst_cluster");

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

        int previousReasons = reasons();

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        AABB box = new AABB(x - WIDTH / 2, y, z - WIDTH / 2, x + WIDTH / 2, y + HEIGHT, z + WIDTH / 2);

        unloadedChunk = !fullyKnown(box);
        unknownBoxOnly = false;
        entitySupport = findSupportingEntity(x, y, z);
        approximatedShape = touchingApproximatedShape(box);

        boolean refused = anyRefusal();
        if (refused) refusedTicks++;

        // Log changes only. The state holds for long stretches, and a line per tick would bury the
        // moment it flipped. A new reason counts as a change, or the logged reasons go stale.
        if (reasons() != previousReasons) {
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

    private int reasons() {
        return (unloadedChunk ? 1 : 0) | (entitySupport ? 2 : 0) | (approximatedShape ? 4 : 0);
    }

    private boolean fullyKnown(AABB box) {
        int maxX = Mth.floor(box.maxX) >> 4;
        int maxZ = Mth.floor(box.maxZ) >> 4;
        for (int cx = Mth.floor(box.minX) >> 4; cx <= maxX; cx++) {
            for (int cz = Mth.floor(box.minZ) >> 4; cz <= maxZ; cz++) {
                if (!mc.level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    private boolean findSupportingEntity(double x, double y, double z) {
        double reach = UNKNOWN_WIDTH / 2.0 + SUPPORT_MARGIN;
        AABB area = new AABB(x - reach, y - UNKNOWN_HEIGHT - SUPPORT_MARGIN, z - reach,
            x + reach, y + SUPPORT_MARGIN, z + reach);

        for (Entity entity : mc.level.getEntities(mc.player, area)) {
            if (entity.isRemoved()) continue;

            if (supportsPlayers(entity) && onTop(entity, x, y, z)) {
                unknownBoxOnly = false;
                supportingEntity = entity.getType().toShortString();
                return true;
            }

            // The wider envelope the server falls back to when it cannot identify the entity.
            // Reported separately rather than folded in, because assuming the fallback always
            // applies would claim a refusal that usually is not there.
            if (withinUnknownBox(entity, x, y, z)) {
                unknownBoxOnly = true;
                supportingEntity = entity.getType().toShortString() + " (only if untracked)";
            }
        }
        if (!unknownBoxOnly) supportingEntity = "none";
        return false;
    }

    private static boolean supportsPlayers(Entity entity) {
        return entity instanceof AbstractBoat || entity instanceof Shulker;
    }

    /**
     * Whiteout's support test for an entity whose size it knows. The player's feet have to be within
     * the margin of the entity's top.
     */
    private static boolean onTop(Entity entity, double x, double y, double z) {
        double radius = entity.getBbWidth() / 2.0 + SUPPORT_MARGIN;
        if (Math.abs(entity.getX() - x) > radius || Math.abs(entity.getZ() - z) > radius) return false;
        return Math.abs(y - (entity.getY() + entity.getBbHeight())) <= SUPPORT_MARGIN;
    }

    /**
     * Whiteout's fallback for an entity of unknown size. Anywhere inside a 2x2 box counts.
     */
    private static boolean withinUnknownBox(Entity entity, double x, double y, double z) {
        double radius = UNKNOWN_WIDTH / 2.0 + SUPPORT_MARGIN;
        if (Math.abs(entity.getX() - x) > radius || Math.abs(entity.getZ() - z) > radius) return false;
        return y >= entity.getY() - SUPPORT_MARGIN && y <= entity.getY() + UNKNOWN_HEIGHT + SUPPORT_MARGIN;
    }

    private boolean touchingApproximatedShape(AABB box) {
        for (BlockPos pos : BlockPos.betweenClosed(box)) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.blocksMotion() && isApproximated(state.getBlock())) return true;
        }
        return false;
    }

    private static boolean isApproximated(Block block) {
        String name = block.builtInRegistryHolder().key().identifier().getPath();
        for (String suffix : APPROXIMATED) {
            if (name.endsWith(suffix)) return true;
        }
        return false;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        line("Unloaded chunk nearby", unloadedChunk);
        line("Entity could be support", entitySupport);
        if (entitySupport || unknownBoxOnly) ImGui.textUnformatted("    " + supportingEntity);
        line("Approximated shape nearby", approximatedShape);

        ImGui.separator();
        ImGui.textUnformatted(anyRefusal() ? "REFUSED, a silent check may not have looked" : "Judgeable");
        ImGui.textUnformatted("Refused ticks: " + refusedTicks);
        ImGuiUtil.textDisabled("Client-side estimate; confirm against the server.");
    }

    private static void line(String label, boolean active) {
        ImGui.textUnformatted((active ? "[x] " : "[ ] ") + label);
    }
}
