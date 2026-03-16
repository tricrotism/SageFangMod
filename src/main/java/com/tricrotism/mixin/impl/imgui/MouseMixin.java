package com.tricrotism.mixin.impl.imgui;

import imgui.ImGui;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents mouse events from reaching the game when ImGui wants the mouse.
 * <p>
 * Only active when the cursor is free (screen/chat open). When the mouse is
 * grabbed for in-game camera control, all events pass through unconditionally
 * so that yaw/pitch rotation is never interrupted by overlay hover state.
 */
@Mixin(MouseHandler.class)
public class MouseMixin {

    @Shadow private boolean mouseGrabbed;

    private boolean shouldCapture() {
        return !mouseGrabbed && ImGui.getIO().getWantCaptureMouse();
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    public void preventClick(long window, MouseButtonInfo mouseButtonInfo, int action, CallbackInfo ci) {
        if (shouldCapture()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    public void preventScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (shouldCapture()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    public void preventCursorPos(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (shouldCapture()) {
            ci.cancel();
        }
    }
}