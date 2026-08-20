package com.tricrotism.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * The direction the movement keys are asking for, in world space.
 * <p>
 * Shared by the modules that drive the player themselves rather than letting vanilla do it. Derived
 * from the real camera yaw and never from {@code RotationManager}: the transmitted rotation is a
 * claim made to the server, while this is about where the person at the keyboard wants to go, and
 * tying the two together would make every spoofed rotation yank the player sideways.
 */
final class MovementInput {

    private MovementInput() {}

    /**
     * Unit vector in the input direction, or zero when no movement key is held.
     */
    static Vec3 direction(Minecraft mc) {
        if (mc.player == null) return Vec3.ZERO;

        float forward = (mc.options.keyUp.isDown() ? 1f : 0f) - (mc.options.keyDown.isDown() ? 1f : 0f);
        float strafe = (mc.options.keyLeft.isDown() ? 1f : 0f) - (mc.options.keyRight.isDown() ? 1f : 0f);
        if (forward == 0f && strafe == 0f) return Vec3.ZERO;

        double yaw = Math.toRadians(mc.player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        return new Vec3(forward * -sin + strafe * cos, 0.0, forward * cos + strafe * sin).normalize();
    }

    /**
     * Vertical component of the jump and sneak keys, in the range -1 to 1.
     */
    static double vertical(Minecraft mc) {
        return (mc.options.keyJump.isDown() ? 1.0 : 0.0) - (mc.options.keyShift.isDown() ? 1.0 : 0.0);
    }
}
