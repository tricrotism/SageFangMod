package com.tricrotism.mixin.uiutilsimpl;

import com.tricrotism.Main;
import com.tricrotism.config.Config;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {

    @Inject(at = @At("HEAD"), method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V", cancellable = true)
    public void send(Packet<?> packet, @Nullable PacketSendListener packetSendListener, boolean bl, CallbackInfo ci) {
        // checks for if packets should be sent and if the packet is a gui related packet
        if (!Main.getConfig().sendUIPackets && (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerButtonClickPacket)) {
            ci.cancel();
            return;
        }

        // checks for if packets should be delayed and if the packet is a gui related packet and is added to a list
        if (Main.getConfig().delayUIPackets && (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerButtonClickPacket)) {
            Main.getConfig().delayedUIPackets.add(packet);
            Config.write();
            ci.cancel();
        }

        // cancels sign update packets if sign editing is disabled and re-enables sign editing
        if (!Main.getConfig().shouldEditSign && (packet instanceof ServerboundSignUpdatePacket)) {
            Main.getConfig().shouldEditSign = true;
            Config.write();
            ci.cancel();
        }
    }
}
