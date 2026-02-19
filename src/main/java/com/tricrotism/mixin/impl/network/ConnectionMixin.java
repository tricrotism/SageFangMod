package com.tricrotism.mixin.impl.network;

import com.tricrotism.SageFang;
import com.tricrotism.config.SageFangConfig;
import com.tricrotism.modules.blink.Blink;
import com.tricrotism.modules.packets.PacketManager;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
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

    // ── Outbound ────────────────────────────────────────────────────

    @Inject(at = @At("HEAD"), method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", cancellable = true)
    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bl, CallbackInfo ci) {
        // Only intercept serverbound packets — clientbound packets also pass through
        // send() on integrated server (singleplayer) and must not be captured.
        if (!packet.getClass().getSimpleName().startsWith("Serverbound")) return;

        // Blink — queue all serverbound packets while active
        if (Blink.instance.capturePacket(packet)) {
            ci.cancel();
            return;
        }

        // Packet Manager — delay outbound if enabled (skip if Blink is flushing to avoid double-queue)
        if (!PacketManager.instance.isFlushingOutbound() && PacketManager.instance.captureOutbound(packet)) {
            ci.cancel();
            return;
        }

        // Block UI packets entirely
        if (!SageFangConfig.isSendUIPackets() && (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerButtonClickPacket)) {
            ci.cancel();
            return;
        }

        // Delay UI packets to list
        if (SageFangConfig.isDelayUIPackets() && (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerButtonClickPacket)) {
            SageFang.delayedUIPackets.add(packet);
            ci.cancel();
            return;
        }

        // Block sign update packets if sign editing is disabled
        if (!SageFangConfig.isShouldEditSign() && (packet instanceof ServerboundSignUpdatePacket)) {
            SageFangConfig.setShouldEditSign(true);
            ci.cancel();
        }
    }

    // ── Inbound ─────────────────────────────────────────────────────

    @Inject(at = @At("HEAD"), method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V")
    protected void onPacketReceived(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketManager.instance.observeInbound(packet);
    }
}
