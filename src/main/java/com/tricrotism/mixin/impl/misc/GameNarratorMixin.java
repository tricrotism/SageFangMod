package com.tricrotism.mixin.impl.misc;

import net.minecraft.client.GameNarrator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameNarrator.class)
public class GameNarratorMixin {
    @Inject(at = @At("HEAD"), method = "narrateMessage(Ljava/lang/String;Z)V", cancellable = true)
    private void onNarrateMessage(String message, boolean interrupt, CallbackInfo ci) {
        ci.cancel();
    }
}
