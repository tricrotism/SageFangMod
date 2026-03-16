package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

/**
 * Server-bound or client-bound addon message (packet 32).
 * Carries a string key and a raw data payload (often GZIP-compressed JSON).
 * We only need to read it; no response required.
 */
@Getter
public class AddonMessage extends Packet {
    private String key;
    private byte[] data;

    public AddonMessage() {}

    @Override
    public void read(PacketBuffer buf) {
        this.key = buf.readString();
        this.data = buf.readByteArray();
    }

    @Override
    public void write(PacketBuffer buf) {}
}
