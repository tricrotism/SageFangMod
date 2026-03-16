package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

public class EncryptionResponse extends Packet {
    private byte[] encryptedSecret;
    private byte[] encryptedToken;
    private byte[] encryptedPin;

    public EncryptionResponse() {}

    public EncryptionResponse(byte[] encryptedSecret, byte[] encryptedToken, byte[] encryptedPin) {
        this.encryptedSecret = encryptedSecret;
        this.encryptedToken = encryptedToken;
        this.encryptedPin = encryptedPin;
    }

    @Override
    public void read(PacketBuffer buf) {}

    @Override
    public void write(PacketBuffer buf) {
        buf.writeByteArray(new byte[]{42});
        buf.writeByteArray(encryptedSecret);
        buf.writeByteArray(encryptedToken);
        buf.writeByteArray(encryptedPin);
    }
}
