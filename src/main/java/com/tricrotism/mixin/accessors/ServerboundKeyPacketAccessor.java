package com.tricrotism.mixin.accessors;

import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Setters for the (final) byte arrays of {@code ServerboundKeyPacket} so
 * {@code LoginKeySpoof} can overwrite the encrypted secret key ({@code keybytes})
 * and encrypted challenge/nonce ({@code encryptedChallenge}) before the packet is
 * sent.
 */
@Mixin(ServerboundKeyPacket.class)
public interface ServerboundKeyPacketAccessor {

    @Mutable
    @Accessor("keybytes")
    void sagefang$setKeybytes(byte[] keybytes);

    @Mutable
    @Accessor("encryptedChallenge")
    void sagefang$setEncryptedChallenge(byte[] encryptedChallenge);
}
