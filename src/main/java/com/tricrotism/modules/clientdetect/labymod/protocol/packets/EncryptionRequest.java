package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

@Getter
public class EncryptionRequest extends Packet {
    private String serverId;
    private byte[] publicKey;
    private byte[] verifyToken;

    public EncryptionRequest() {}

    @Override
    public void read(PacketBuffer buf) {
        this.serverId = buf.readString();
        this.publicKey = buf.readByteArray();
        this.verifyToken = buf.readByteArray();
    }

    @Override
    public void write(PacketBuffer buf) {}
}
