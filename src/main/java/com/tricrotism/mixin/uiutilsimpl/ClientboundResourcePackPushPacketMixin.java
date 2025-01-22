package com.tricrotism.mixin.uiutilsimpl;

import com.tricrotism.Main;
import com.tricrotism.utils.SharedVariables;
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
public abstract class ClientboundResourcePackPushPacketMixin {

    @Shadow
    public abstract void send(Packet<?> packet);

    @Inject(at = @At("HEAD"), method = "handleResourcePackPush", cancellable = true)
    public void handleResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (SharedVariables.bypassResourcePack && (!packet.isSkippable() && SharedVariables.resourcePackForceDeny)) {
            this.send(new ServerboundResourcePackPacket(Minecraft.getInstance().getUser().getProfileId(), ServerboundResourcePackPacket.Action.ACCEPTED));
            this.send(new ServerboundResourcePackPacket(Minecraft.getInstance().getUser().getProfileId(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            Main.LOGGER.info(
                    "[SageFang - UI Utils Impl]: Required Resource Pack Bypassed! Message: " +
                            (packet.prompt().isEmpty() ? "No message" : packet.prompt().toString()) +
                            ", URL: " + (packet.url().isEmpty() ? "<no url>" : packet.url()) +
                            ", Hash: " + packet.hash() +
                            ", Required?: " + packet.required()
            );

            ci.cancel();
        }

    }
}
