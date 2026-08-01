package com.tricrotism.mixin.impl.network;

import com.tricrotism.config.SageFangConfig;
import com.tricrotism.features.commands.PluginsCommand;
import com.tricrotism.modules.blink.Blink;
import com.tricrotism.modules.exploit.BoatNoClip;
import com.tricrotism.modules.exploit.ElytraCast;
import com.tricrotism.modules.exploit.GrimEntityBlink;
import com.tricrotism.modules.exploit.GrimFishingBlink;
import com.tricrotism.modules.freecam.Freecam;
import com.tricrotism.modules.misc.ConnectionCut;
import com.tricrotism.modules.misc.GameStateBypass;
import com.tricrotism.modules.esp.BlockEntityChunkESP;
import com.tricrotism.modules.esp.BlockUpdateESP;
import com.tricrotism.modules.esp.BlockEntityDataESP;
import com.tricrotism.modules.esp.LightUpdateESP;
import com.tricrotism.modules.esp.SkyLightESP;
import com.tricrotism.modules.logger.ChannelLogger;
import com.tricrotism.modules.logger.PayloadLogger;
import com.tricrotism.modules.logger.TeamDetector;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import com.tricrotism.modules.misc.S2CPacketDelayer;
import com.tricrotism.modules.packets.PacketManager;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import com.tricrotism.modules.packets.UIPacketDelay;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {

    @Inject(at = @At("HEAD"), method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", cancellable = true)
    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bl, CallbackInfo ci) {
        try {
            if (packet.type().flow() != PacketFlow.SERVERBOUND) return;
        } catch (Exception e) {
            return; // can't determine flow, skip
        }

        if (ConnectionCut.instance.isActive()) {
            ci.cancel();
            return;
        }

        if (Blink.instance.capturePacket(packet)) {
            ci.cancel();
            return;
        }

        if (Freecam.instance.isEngaged() && packet instanceof ServerboundMovePlayerPacket) {
            ci.cancel();
            return;
        }

        // Elytra Cast — rewrite the jump bit of outbound input packets (does not cancel)
        ElytraCast.instance.onOutboundInput(packet);

        // Boat NoClip — spoof onGround / anti-kick Y on outbound vehicle moves (does not cancel)
        BoatNoClip.instance.onOutboundVehicleMove(packet);

        PayloadLogger.instance.onOutbound(packet);

        if (GrimEntityBlink.instance.captureOutbound(packet)) {
            ci.cancel();
            return;
        }

        if (GrimFishingBlink.instance.captureOutbound(packet)) {
            ci.cancel();
            return;
        }

        if (!PacketManager.instance.isFlushingOutbound() && PacketManager.instance.captureOutbound(packet)) {
            ci.cancel();
            return;
        }

        if (!SageFangConfig.isSendUIPackets() && (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerButtonClickPacket)) {
            ci.cancel();
            return;
        }

        if (SageFangConfig.isDelayUIPackets() && !UIPacketDelay.isReleasing()
            && (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerButtonClickPacket)) {
            UIPacketDelay.queue(packet);
            ci.cancel();
            return;
        }

        // Block sign update packets if sign editing is disabled
        if (!SageFangConfig.isShouldEditSign() && (packet instanceof ServerboundSignUpdatePacket)) {
            SageFangConfig.setShouldEditSign(true);
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", cancellable = true)
    protected void onPacketReceived(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (ConnectionCut.instance.isActive()) {
            ci.cancel();
            return;
        }

        // Game State Bypass — drop selected server game-event packets
        if (packet instanceof ClientboundGameEventPacket gameEvent && GameStateBypass.instance.shouldCancel(gameEvent)) {
            ci.cancel();
            return;
        }

        // S2C Packet Delayer — hold inbound packets while active, replayed on disable
        if (S2CPacketDelayer.instance.captureInbound(packet)) {
            ci.cancel();
            return;
        }

        if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
            BlockUpdateESP.instance.onBlockUpdate(blockUpdate);
        }
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            BlockEntityChunkESP.instance.onChunk(chunkPacket);
            SkyLightESP.instance.onChunkLight(chunkPacket.getX(), chunkPacket.getZ(), chunkPacket.getLightData());
        }
        if (packet instanceof ClientboundLightUpdatePacket lightPacket) {
            LightUpdateESP.instance.onLightUpdate(lightPacket);
            SkyLightESP.instance.onChunkLight(lightPacket.getX(), lightPacket.getZ(), lightPacket.getLightData());
        }
        if (packet instanceof ClientboundBlockEntityDataPacket blockEntityData) {
            BlockEntityDataESP.instance.onBlockEntityData(blockEntityData);
        }
        if (packet instanceof ClientboundSetPlayerTeamPacket teamPacket) {
            TeamDetector.instance.onTeamPacket(teamPacket);
        }
        PayloadLogger.instance.onInbound(packet);
        ChannelLogger.instance.onInbound(packet);

        PacketManager.instance.observeInbound(packet);
        if (packet instanceof ClientboundCommandSuggestionsPacket suggestions) {
            PluginsCommand.onSuggestionsPacket(suggestions);
        }
    }
}
