package com.tricrotism.mixin.impl.screen;

import com.tricrotism.modules.misc.AutoReconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void sagefang$rememberServer(Screen parent, Minecraft minecraft, ServerAddress address, ServerData server,
                                                boolean quickPlay, TransferState transferState, CallbackInfo ci) {
        AutoReconnect.instance.remember(address, server);
    }
}
