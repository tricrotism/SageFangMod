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
 * Moves faster than the movement rules allow, by a factor you choose.
 * <p>
 * Aimed at a prediction engine rather than at any packet shape, so it says nothing until an offset
 * check is wired: nothing shipped compares a reported position against a simulated one, and until
 * something does, this is invisible by construction. It exists so that check has something to be
 * tuned against on the day it lands.
 * <p>
 * {@link #multiplier} is the sweep, and the interesting region is the bottom of it. A player at
 * three times the speed is caught by anything; the question a threshold has to answer is where
 * between 1.0 and about 1.3 the offset stops being explicable by ice, slabs, jump timing and the
 * slack an uncertainty handler grants, so start just above 1.0 and walk up until it speaks.
 * <p>
 * {@link #bunnyHop} matters more than it looks. Ground friction is the dominant term in the
 * simulation and it only applies while the player is on the ground, so a hopping player spends most
 * of their ticks in the air where the model is looser. It is the cheap way to buy tolerance without
 * moving any faster, and a check tuned only against flat-ground running will miss it.
 */
public final class Speed extends Module {

    public static final Speed instance = new Speed();

    private final Settings.Decimal multiplier =
        decimal("Multiplier", "multiplier", "Horizontal speed relative to vanilla", 1.15, 1.0, 3.0);
    private final Settings.Bool bunnyHop =
        bool("Bunny Hop", "bunnyHop", "Jump whenever grounded and moving, to stay off ground friction", false);

    private int hops;
    private double lastSpeed;

    private Speed() {
        super("speed", "Speed", "Move faster than the movement rules allow; sweeps a prediction offset.",
            Category.EXPLOIT);
    }

    @Override
    public void onActivate() {
        hops = 0;
        TestLog.event("speed_enable", "multiplier", multiplier.get(), "bunnyHop", bunnyHop.get());
    }

    @Override
    public void onDeactivate() {
        TestLog.event("speed_disable", "hops", hops);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;

        Vec3 direction = MovementInput.direction(mc);
        if (direction.lengthSqr() == 0.0) return;

        if (bunnyHop.get() && mc.player.onGround()) {
            mc.player.jumpFromGround();
            hops++;
        }

        Vec3 delta = mc.player.getDeltaMovement();
        // Scaled along the input direction rather than along the existing delta, so the boost does
        // not vanish in the tick after a jump when the horizontal component is briefly near zero.
        double horizontal = Math.hypot(delta.x, delta.z) * multiplier.get();
        lastSpeed = horizontal;
        mc.player.setDeltaMovement(direction.x * horizontal, delta.y, direction.z * horizontal);

        TestLog.event("speed_tick",
            "multiplier", multiplier.get(),
            "horizontal", horizontal,
            "onGround", mc.player.onGround(),
            "y", mc.player.getY());
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text(String.format("Horizontal: %.4f blocks/tick", lastSpeed));
        ImGui.text("Hops: " + hops);
    }
}
