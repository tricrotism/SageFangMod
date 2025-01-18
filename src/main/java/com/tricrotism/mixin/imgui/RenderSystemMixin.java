package com.tricrotism.mixin.imgui;

import com.tricrotism.InfoOverlay;
import com.tricrotism.imgui.ImGuiImpl;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {
    @Inject(method = "flipFrame", at = @At("HEAD"))
    private static void injectImGuiRender(CallbackInfo ci) {
        Profiler profiler = Profilers.get();
        profiler.push("ImGui");
        ImGuiImpl.INSTANCE.draw(io -> InfoOverlay.renderImGui());
        profiler.pop();
    }
}
