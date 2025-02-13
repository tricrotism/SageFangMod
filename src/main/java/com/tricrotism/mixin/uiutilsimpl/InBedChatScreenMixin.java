package com.tricrotism.mixin.uiutilsimpl;

import com.tricrotism.Main;
import com.tricrotism.config.Config;
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
        if (Main.getConfig().shouldForceWakeUp) {
            if (mc.player != null && mc.player.isSleeping()) {
                mc.player.stopSleeping();
                mc.setScreen(null);
                Main.getConfig().shouldForceWakeUp = false;
                Config.write();
            }
        }
    }
}
