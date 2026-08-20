package com.tricrotism.mixin.impl.imgui;

import imgui.ImGui;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors {@code MouseMixin}: the overlay only swallows input while the cursor is free.
 * With the mouse grabbed for gameplay a stale ImGui text field would otherwise eat movement keys.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Unique
    private static boolean sagefang$shouldCapture() {
        return !Minecraft.getInstance().mouseHandler.isMouseGrabbed() && ImGui.getIO().getWantCaptureKeyboard();
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    public void preventKeyboardInput(long window, int key, KeyEvent keyEvent, CallbackInfo ci) {
        if (sagefang$shouldCapture()) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    public void preventKeyboardInput(long window, CharacterEvent characterEvent, CallbackInfo ci) {
        if (sagefang$shouldCapture()) {
            ci.cancel();
        }
    }
}