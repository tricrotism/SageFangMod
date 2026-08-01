package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

/**
 * Clientbound (ID 10): the encryption handshake challenge, mirroring vanilla
 * Minecraft's login encryption flow.
 *
 * <p>Dispatched to {@code LabyConnectSession.handleEncryptionRequest}, which
 * generates a shared secret, joins the Mojang session server with the hashed
 * {@link #serverId}, RSA-encrypts the secret/token under {@link #publicKey},
 * and replies with {@link EncryptionResponse} (ID 11).
 */
@Getter
public class EncryptionRequest extends Packet {
    /**
     * Server-id string hashed together with the shared secret for session auth.
     */
    private String serverId;
    /**
     * Server's X.509 RSA public key (DER-encoded) used to encrypt the secret.
     */
    private byte[] publicKey;
    /**
     * Random verify token echoed back (encrypted) to prove key ownership.
     */
    private byte[] verifyToken;

    public EncryptionRequest() {}

    /**
     * Decodes a {@code writeString} server id followed by two
     * {@code writeByteArray} fields (public key, then verify token).
     */
    @Override
    public void read(PacketBuffer buf) {
        this.serverId = buf.readString();
        this.publicKey = buf.readByteArray();
        this.verifyToken = buf.readByteArray();
    }

    /**
     * No-op: SageFang only receives the challenge and never sends it.
     */
    @Override
    public void write(PacketBuffer buf) {}
}
