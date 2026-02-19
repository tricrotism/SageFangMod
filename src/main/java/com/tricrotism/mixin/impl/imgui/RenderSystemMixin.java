package com.tricrotism.mixin.impl.imgui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tricrotism.SageFang;
import com.tricrotism.events.ui.MenuRegistrationEvent;
import com.tricrotism.utils.ImGuiUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {

    @Inject(method = "flipFrame", at = @At("HEAD"))
    private static void injectImGuiRender(CallbackInfo ci) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("ImGui");

        MenuRegistrationEvent event = SageFang.EVENT_BUS.post(MenuRegistrationEvent.get());
        ImGuiUtil.INSTANCE.draw(event.getMenus());

        profiler.pop();
    }

}
