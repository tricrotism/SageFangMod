package com.tricrotism.mixin.impl.audio;

import com.tricrotism.config.SageFangConfig;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class SoundLoggingMixin {

    private static final Logger log = LoggerFactory.getLogger(SoundLoggingMixin.class);

    @Inject(method = "handleSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;)V", at = @At("HEAD"))
    public void handleSoundEvent(ClientboundSoundPacket clientboundSoundPacket, CallbackInfo ci) {
        if (SageFangConfig.isShouldLogSounds()) {
            String sound = "";
            sound = sound + "Source Name: " + clientboundSoundPacket.getSource().getName() + "\t";
            sound = sound + "Registered Name: " + clientboundSoundPacket.getSound().getRegisteredName() + "\t";
            sound = sound + "Volume: " + clientboundSoundPacket.getVolume() + "\t";
            sound = sound + "Pitch: " + clientboundSoundPacket.getPitch() + "\t";
            sound = sound + "Seed: " + clientboundSoundPacket.getSeed();

            log.info(sound);
        }
    }

}
