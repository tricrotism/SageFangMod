package com.mchub.infooverlay.mixin.imgui;

import imgui.ImGui;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    public void preventClick(long window, int button, int action, int mods, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    public void preventScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            ci.cancel();
        }
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    public void preventCursorPos(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureMouse()) {
            ci.cancel();
        }
    }
}
