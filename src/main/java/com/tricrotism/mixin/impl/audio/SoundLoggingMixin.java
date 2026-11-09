package com.tricrotism.mixin.impl.audio;

import com.tricrotism.config.SageFangConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Logs every positioned sound the server plays, including the ones a resource pack supplies.
 */
@Mixin(ClientPacketListener.class)
public class SoundLoggingMixin {

    private static final Logger log = LoggerFactory.getLogger(SoundLoggingMixin.class);

    @Inject(method = "handleSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;)V", at = @At("HEAD"))
    private void sagefang$logSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        // HEAD runs once on netty and again after the packet is rescheduled onto the render thread.
        if (!SageFangConfig.isShouldLogSounds() || !Minecraft.getInstance().isSameThread()) return;

        log.info("Source Name: {}\tName: {}\tVolume: {}\tPitch: {}\tSeed: {}",
            packet.getSource().getName(),
            name(packet.getSound()),
            packet.getVolume(),
            packet.getPitch(),
            packet.getSeed());
    }

    /**
     * A sound the client's registry knows arrives as a reference and has a registered name. One that
     * only a resource pack defines is not in the registry, so the server inlines the whole
     * {@link SoundEvent} and the holder is direct. {@code getRegisteredName} answers the literal
     * string "[unregistered]" for those, which is why every custom pack sound logged as that rather
     * than as itself. The identifier is carried on the inlined event, so unwrap and fall back to it.
     */
    private static String name(Holder<SoundEvent> sound) {
        return sound.unwrapKey()
            .map(key -> key.identifier().toString())
            .orElseGet(() -> sound.value().location().toString());
    }

}
