package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

/**
 * Serverbound (ID 11): the client's reply to {@link EncryptionRequest},
 * completing the encryption handshake.
 *
 * <p>Built by {@code LabyConnectSession.handleEncryptionRequest} from the
 * RSA-encrypted shared secret, verify token, and a PIN blob. After this packet
 * the channel switches to AES.
 */
public class EncryptionResponse extends Packet {
    /**
     * RSA-encrypted AES shared secret.
     */
    private byte[] encryptedSecret;
    /**
     * RSA-encrypted copy of the request's verify token.
     */
    private byte[] encryptedToken;
    /**
     * RSA-encrypted dashboard PIN blob.
     */
    private byte[] encryptedPin;

    public EncryptionResponse() {}

    public EncryptionResponse(byte[] encryptedSecret, byte[] encryptedToken, byte[] encryptedPin) {
        this.encryptedSecret = encryptedSecret;
        this.encryptedToken = encryptedToken;
        this.encryptedPin = encryptedPin;
    }

    /**
     * No-op: this packet is only ever sent, never received.
     */
    @Override
    public void read(PacketBuffer buf) {}

    /**
     * Writes four {@code writeByteArray} fields: a leading single-byte marker
     * {@code {42}}, then the encrypted secret, token, and PIN in order.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeByteArray(new byte[]{42});
        buf.writeByteArray(encryptedSecret);
        buf.writeByteArray(encryptedToken);
        buf.writeByteArray(encryptedPin);
    }
}
