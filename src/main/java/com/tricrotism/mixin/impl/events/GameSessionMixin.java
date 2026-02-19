package com.tricrotism.mixin.impl.events;

import com.tricrotism.SageFang;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.game.GameQuitEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class GameSessionMixin extends ClientCommonPacketListenerImpl {
    @Shadow
    private ClientLevel level;

    @Unique
    private boolean worldNotNull;

    protected GameSessionMixin(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void onGameJoinHead(ClientboundLoginPacket packet, CallbackInfo info) {
        worldNotNull = level != null;
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onGameJoinTail(ClientboundLoginPacket packet, CallbackInfo info) {
        if (worldNotNull) {
            SageFang.EVENT_BUS.post(GameQuitEvent.get());
        }

        SageFang.EVENT_BUS.post(GameJoinedEvent.get());
    }

    @Inject(method = "handleConfigurationStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void onConfigurationStart(ClientboundStartConfigurationPacket packet, CallbackInfo info) {
        SageFang.EVENT_BUS.post(GameQuitEvent.get());
    }
}
