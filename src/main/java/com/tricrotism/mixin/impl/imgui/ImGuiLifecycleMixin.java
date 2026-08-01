package com.tricrotism.mixin.impl.imgui;

import com.mojang.blaze3d.platform.Window;
import com.tricrotism.utils.ImGuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ImGuiLifecycleMixin {

    @Shadow
    @Final
    private Window window;

    @Inject(method = "<init>", at = @At("RETURN"))
    void onInitRenders(GameConfig gameConfig, CallbackInfo ci) {
        ImGuiUtil.INSTANCE.create(window.handle());
    }

    @Inject(method = "close", at = @At("RETURN"))
    void onDisableRenders(CallbackInfo ci) {
        ImGuiUtil.INSTANCE.shutdown();
    }
}