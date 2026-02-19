package com.tricrotism.mixin.impl.network;

import com.tricrotism.config.SageFangConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ResourcePackListenerMixin {

    @Shadow
    public abstract void send(Packet<?> packet);

    @Inject(at = @At("HEAD"), method = "handleResourcePackPush", cancellable = true)
    public void handleResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (SageFangConfig.isBypassResourcePack() && (!packet.isSkippable() && SageFangConfig.isResourcePackForceDeny())) {
            this.send(new ServerboundResourcePackPacket(Minecraft.getInstance().getUser().getProfileId(), ServerboundResourcePackPacket.Action.ACCEPTED));
            this.send(new ServerboundResourcePackPacket(Minecraft.getInstance().getUser().getProfileId(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            ci.cancel();
        }
    }
}
