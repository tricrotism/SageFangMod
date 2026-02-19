package com.tricrotism.mixin.impl.screen;

import com.tricrotism.config.SageFangConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SignEditScreen.class)
public class SignEditScreenMixin {

    @Unique
    private static final Minecraft mc = Minecraft.getInstance();

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci) {
        if (!SageFangConfig.isShouldEditSign()) {
            mc.setScreen(null);
        }
    }

}
