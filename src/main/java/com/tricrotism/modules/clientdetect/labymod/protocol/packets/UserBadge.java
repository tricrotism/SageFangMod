package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UserBadge extends Packet {
    private UUID[] uuids;
    private byte[] ranks;

    public UserBadge() {}

    @Override
    public void read(PacketBuffer buf) {
        int count = buf.readVarInt();
        this.uuids = new UUID[count];
        for (int i = 0; i < count; i++) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            this.uuids[i] = new UUID(msb, lsb);
        }
        this.ranks = new byte[count];
        for (int i = 0; i < count; i++) {
            this.ranks[i] = buf.readByte();
        }
    }

    @Override
    public void write(PacketBuffer buf) {}
}
