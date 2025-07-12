package com.tricrotism.mixin.events;

import com.tricrotism.Main;
import com.tricrotism.events.world.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class EventsMinecraftMixin {
    @Inject(at = @At("HEAD"), method = "tick")
    private void onPreTick(CallbackInfo info) {
        Profiler.get().push(Main.MOD_ID + "_pre_update");
        Main.EVENT_BUS.post(TickEvent.Pre.get());
        Profiler.get().pop();
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void onTick(CallbackInfo info) {
        Profiler.get().push(Main.MOD_ID + "_post_update");
        Main.EVENT_BUS.post(TickEvent.Post.get());
        Profiler.get().pop();
    }
}
