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
 * Stays in the air without anything holding the player up.
 * <p>
 * Collision is left alone, so this is not {@link Clip}: the player still stops at walls and still
 * stands on floors. What is removed is gravity, which makes it the probe for the vertical half of a
 * prediction offset in its most obvious form, since every airborne tick should have subtracted
 * gravity and did not. The offset accumulates every tick rather than once per jump.
 * <p>
 * {@link #groundSpoof} is worth testing separately. Flight on its own is a prediction problem and
 * invisible until that check exists; flight that also claims ground is a ground-claim problem and
 * visible today, which is why the two are a toggle rather than one behaviour. Note the overlap with
 * {@code NoFall}. Running both claims ground twice for different reasons and makes an alert harder
 * to attribute, so use one at a time.
 */
public final class Fly extends Module {

    public static final Fly instance = new Fly();

    private final Settings.Decimal speed =
        decimal("Speed", "speed", "Blocks per tick while flying", 0.3, 0.05, 2.0);
    private final Settings.Bool groundSpoof =
        bool("Claim Ground", "groundSpoof", "Also report standing on the ground while airborne", false);

    private int ticksFlown;

    private Fly() {
        super("fly", "Fly", "Hover and fly without support; sweeps a vertical prediction offset.",
            Category.EXPLOIT);
    }

    @Override
    public void onActivate() {
        ticksFlown = 0;
        TestLog.event("fly_enable", "speed", speed.get(), "groundSpoof", groundSpoof.get());
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.setNoGravity(false);
        TestLog.event("fly_disable", "ticksFlown", ticksFlown);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;

        mc.player.setNoGravity(true);
        mc.player.fallDistance = 0.0;

        Vec3 horizontal = MovementInput.direction(mc).scale(speed.get());
        mc.player.setDeltaMovement(horizontal.x, MovementInput.vertical(mc) * speed.get(), horizontal.z);

        if (groundSpoof.get() && !mc.player.onGround()) mc.player.setOnGround(true);

        ticksFlown++;
        if (ticksFlown % 20 == 0) {
            TestLog.event("fly_tick", "y", mc.player.getY(), "ticks", ticksFlown,
                "groundSpoof", groundSpoof.get());
        }
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Ticks flown: " + ticksFlown);
    }
}
