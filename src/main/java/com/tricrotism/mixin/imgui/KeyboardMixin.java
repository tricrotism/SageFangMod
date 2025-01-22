package com.tricrotism.mixin.imgui;

import imgui.ImGui;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    public void preventKeyboardInput(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureKeyboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    public void preventKeyboardInput(long window, int codepoint, int modifiers, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureKeyboard()) {
            ci.cancel();
        }
    }
}