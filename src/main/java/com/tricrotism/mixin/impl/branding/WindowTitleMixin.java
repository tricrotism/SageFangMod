package com.tricrotism.mixin.impl.branding;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class WindowTitleMixin {

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    public void createTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("Minecraft - SageFang");
    }

}
