package com.tricrotism.mixin.impl.imgui;

import imgui.ImGui;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    public void preventKeyboardInput(long window, int key, KeyEvent keyEvent, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureKeyboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    public void preventKeyboardInput(long window, CharacterEvent characterEvent, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureKeyboard()) {
            ci.cancel();
        }
    }
}