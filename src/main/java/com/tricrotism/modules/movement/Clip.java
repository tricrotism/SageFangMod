package com.tricrotism.modules.movement;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.world.phys.Vec3;

/**
 * Moves through blocks instead of into them.
 * <p>
 * The direct exercise for the block-penetration check, which reads the position a client reports and
 * asks whether the blocks it was shown would have allowed it. Vanilla clears {@code noPhysics} for
 * everything but spectators, so a reported position with the feet inside a full cube is not a fast
 * player or a lagging one. It is a client that skipped the collision resolve.
 * <p>
 * Gravity is skipped along with the collision, since a player who falls through the floor leaves the
 * blocks under test immediately; the jump and sneak keys drive height instead. {@link #speed} is the
 * knob, and the useful sweep is slow: at a crawl the feet sit inside a block for many consecutive
 * ticks, which is the case a per-tick check should catch every time, while a fast pass through a
 * one-block wall may only be reported from inside it once.
 * <p>
 * The check refuses to judge approximated shapes (stairs, fences, walls, panes, doors), so a run
 * that produces nothing may be testing the refusal list rather than the geometry. Full cubes first.
 */
public final class Clip extends Module {

    public static final Clip instance = new Clip();

    private final Settings.Decimal speed =
        decimal("Speed", "speed", "Blocks per tick while clipping", 0.15, 0.02, 1.0);

    private int ticksInside;

    private Clip() {
        super("clip", "Clip", "Move through blocks; drives the block-penetration check.",
            Category.EXPLOIT);
    }

    @Override
    public void onActivate() {
        ticksInside = 0;
        TestLog.event("clip_enable", "speed", speed.get());
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.noPhysics = false;
        TestLog.event("clip_disable", "ticksInside", ticksInside);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.level == null) return;

        mc.player.noPhysics = true;
        mc.player.setDeltaMovement(MovementInput.direction(mc).scale(speed.get())
            .add(0.0, MovementInput.vertical(mc) * speed.get(), 0.0));
        mc.player.fallDistance = 0.0;

        boolean inside = mc.level.getBlockState(mc.player.blockPosition()).isSolid();
        if (!inside) return;

        ticksInside++;
        Vec3 position = mc.player.position();
        TestLog.event("clip_inside",
            "x", position.x, "y", position.y, "z", position.z,
            "block", mc.level.getBlockState(mc.player.blockPosition()).getBlock().toString());
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Ticks inside a block: " + ticksInside);
    }
}
