package com.tricrotism.mixin.impl.imgui;

import imgui.ImGui;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    public void preventClick(long window, MouseButtonInfo mouseButtonInfo, int action, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    public void preventScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    public void preventCursorPos(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            ci.cancel();
        }
    }
}