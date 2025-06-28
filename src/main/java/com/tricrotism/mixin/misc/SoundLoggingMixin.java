package com.tricrotism.mixin.misc;

import com.tricrotism.Main;
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
        if (Main.getConfig().shouldLogSounds) {
            String sound = "";
            sound = sound + "Source: " + clientboundSoundPacket.getSource().getName() + "\t";
            sound = sound + "Source: " + clientboundSoundPacket.getSound().getRegisteredName() + "\t";
            sound = sound + "Volume: " + clientboundSoundPacket.getVolume() + "\t";
            sound = sound + "Pitch: " + clientboundSoundPacket.getPitch() + "\t";
            sound = sound + "Seed: " + clientboundSoundPacket.getSeed() + "\t";

            log.info(sound);
        }
    }

}
