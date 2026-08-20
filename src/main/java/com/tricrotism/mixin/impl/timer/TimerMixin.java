package com.tricrotism.mixin.impl.timer;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tricrotism.modules.latency.Timer;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scales the number of game ticks the client runs per frame. This is the single point where the
 * client decides how much simulated time has passed, so it is also where the outbound movement
 * packet rate is decided.
 */
@Mixin(DeltaTracker.Timer.class)
public class TimerMixin {

    @ModifyReturnValue(method = "advanceGameTime", at = @At("RETURN"))
    private int sagefang$scaleTicks(int original) {
        return Timer.instance.scale(original);
    }
}
