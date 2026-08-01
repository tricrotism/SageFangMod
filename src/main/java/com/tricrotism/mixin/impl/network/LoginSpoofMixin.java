package com.tricrotism.mixin.impl.network;

import com.tricrotism.mixin.accessors.ServerboundKeyPacketAccessor;
import com.tricrotism.modules.login.HandshakeSpoofer;
import com.tricrotism.modules.login.LoginHelloSpoof;
import com.tricrotism.modules.login.LoginKeySpoof;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

/**
 * Rewrites outbound login packets on {@code Connection.send} when the matching
 * spoof module is active: the handshake {@link ClientIntentionPacket} (protocol /
 * address / port / intended state) and the {@link ServerboundHelloPacket} (name /
 * UUID). Records are immutable, so the intercept returns a replacement packet.
 */
@Mixin(Connection.class)
public class LoginSpoofMixin {

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet<?> sagefang$spoofHandshake(Packet<?> packet) {
        if (!(packet instanceof ClientIntentionPacket original)) return packet;
        HandshakeSpoofer s = HandshakeSpoofer.instance;
        if (!s.isActive()) return packet;

        int protocol = s.isModifyProtocol() ? s.getProtocolVersion() : original.protocolVersion();
        String host = s.isModifyAddress() ? s.getAddress() : original.hostName();
        int port = s.isModifyPort() ? s.getPort() : original.port();
        ClientIntent intent = s.isModifyIntendedState() ? s.getIntendedState() : original.intention();

        if (protocol == original.protocolVersion() && host.equals(original.hostName())
            && port == original.port() && intent == original.intention()) {
            return packet;
        }
        return new ClientIntentionPacket(protocol, host, port, intent);
    }

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet<?> sagefang$spoofHello(Packet<?> packet) {
        if (!(packet instanceof ServerboundHelloPacket original)) return packet;
        LoginHelloSpoof s = LoginHelloSpoof.instance;
        if (!s.isActive()) return packet;

        String name = s.getSpoofName();
        UUID uuid = s.getSpoofProfileId();
        String newName = name != null ? name : original.name();
        UUID newUuid = uuid != null ? uuid : original.profileId();

        if (newName.equals(original.name()) && newUuid.equals(original.profileId())) {
            return packet;
        }
        return new ServerboundHelloPacket(newName, newUuid);
    }

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet<?> sagefang$spoofKey(Packet<?> packet) {
        if (!(packet instanceof ServerboundKeyPacket key)) return packet;
        LoginKeySpoof s = LoginKeySpoof.instance;
        if (!s.isActive()) return packet;

        ServerboundKeyPacketAccessor accessor = (ServerboundKeyPacketAccessor) key;
        if (s.shouldModifyKey()) accessor.sagefang$setKeybytes(s.getKeyBytes());
        if (s.shouldModifyNonce()) accessor.sagefang$setEncryptedChallenge(s.getNonceBytes());
        return key;
    }
}
