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
 * Jumps higher than the jump power allows.
 * <p>
 * The cleanest single probe for a vertical prediction offset, and the reason to build it separately
 * from {@link Speed}: jump power is one value applied on one tick, so the whole violation is
 * concentrated rather than spread across a run, and the ceiling is trivially derivable from the
 * attribute. A check watching the vertical axis should catch this at a far smaller multiplier than
 * it catches horizontal speed, and if it does not, the vertical slack is too generous.
 * <p>
 * Applied on the tick the launch happens, by scaling the velocity vanilla just set, so the boost
 * inherits the jump-boost effect and the sprint bonus rather than replacing them. A fixed override
 * would read as a violation on flat ground and as nothing at all under a potion.
 */
public final class HighJump extends Module {

    public static final HighJump instance = new HighJump();

    private final Settings.Decimal multiplier =
        decimal("Multiplier", "multiplier", "Launch velocity relative to vanilla", 1.5, 1.0, 4.0);

    private boolean wasOnGround;
    private int jumps;
    private double lastLaunch;

    private HighJump() {
        super("highjump", "High Jump", "Jump higher than the jump power allows; sweeps vertical offset.",
            Category.EXPLOIT);
    }

    @Override
    public void onActivate() {
        jumps = 0;
        wasOnGround = mc.player != null && mc.player.onGround();
        TestLog.event("highjump_enable", "multiplier", multiplier.get());
    }

    @Override
    public void onDeactivate() {
        TestLog.event("highjump_disable", "jumps", jumps);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;

        boolean onGround = mc.player.onGround();
        Vec3 delta = mc.player.getDeltaMovement();
        boolean launching = wasOnGround && !onGround && delta.y > 0.0;
        wasOnGround = onGround;
        if (!launching) return;

        lastLaunch = delta.y * multiplier.get();
        mc.player.setDeltaMovement(delta.x, lastLaunch, delta.z);
        jumps++;

        TestLog.event("highjump_launch",
            "vanillaY", delta.y,
            "boostedY", lastLaunch,
            "multiplier", multiplier.get());
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Jumps: " + jumps);
        ImGui.text(String.format("Last launch: %.4f", lastLaunch));
    }
}
