package com.tricrotism.mixin.impl.screen;

import com.tricrotism.config.SageFangConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InBedChatScreen.class)
public class InBedChatScreenMixin {

    @Unique
    private static final Minecraft mc = Minecraft.getInstance();

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci) {
        if (SageFangConfig.isShouldForceWakeUp()) {
            if (mc.player != null && mc.player.isSleeping()) {
                mc.player.stopSleeping();
                mc.setScreen(null);
                SageFangConfig.setShouldForceWakeUp(false);
            }
        }
    }
}
