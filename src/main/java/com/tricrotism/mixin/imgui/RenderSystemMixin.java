package com.tricrotism.mixin.imgui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tricrotism.utils.event.menu.MenuRegistrationEvent;
import com.tricrotism.utils.event.menu.MenuRegistrationEventArgs;
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

        MenuRegistrationEventArgs eventArgs = new MenuRegistrationEventArgs();
        MenuRegistrationEvent.INSTANCE.invoke(eventArgs);
        ImGuiUtil.INSTANCE.draw(eventArgs.getMenus());

        profiler.pop();
    }

}
