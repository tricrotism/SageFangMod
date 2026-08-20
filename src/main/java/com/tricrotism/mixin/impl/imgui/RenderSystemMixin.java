package com.tricrotism.mixin.impl.imgui;

import com.tricrotism.SageFang;
import com.tricrotism.events.ui.MenuRegistrationEvent;
import com.tricrotism.utils.ImGuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the ImGui overlay as the last thing in the frame. 26.2 removed
 * {@code RenderSystem.flipFrame}; the equivalent point is now immediately before
 * {@code GpuSurface.present()} in {@link Minecraft#renderFrame(boolean)}.
 */
@Mixin(Minecraft.class)
public class RenderSystemMixin {

    @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"))
    private void injectImGuiRender(boolean tick, CallbackInfo ci) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("ImGui");

        MenuRegistrationEvent event = SageFang.EVENT_BUS.post(MenuRegistrationEvent.get());
        ImGuiUtil.INSTANCE.draw(event.getMenus());

        profiler.pop();
    }

}
