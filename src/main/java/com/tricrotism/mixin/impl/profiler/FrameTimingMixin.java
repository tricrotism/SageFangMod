package com.tricrotism.mixin.impl.profiler;

import com.tricrotism.modules.profiler.FrameProfiler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the frame boundary for {@link FrameProfiler}.
 * <p>
 * {@code runTick} is the render loop's body, so its head is where one frame ends and the next
 * begins. The callback is deliberately the cheapest thing that can still measure: two volatile
 * writes and a subtraction, with every map and allocation left to the sampler thread. Anything
 * heavier here would be measured by the profiler it feeds.
 */
@Mixin(Minecraft.class)
public class FrameTimingMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void sagefang$frameStart(boolean renderLevel, CallbackInfo ci) {
        FrameProfiler.instance.onFrameStart();
    }
}
