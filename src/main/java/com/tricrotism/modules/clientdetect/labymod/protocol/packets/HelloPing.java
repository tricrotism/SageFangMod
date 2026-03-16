package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

public class HelloPing extends Packet {
    private long timestamp;

    public HelloPing() {}

    public HelloPing(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public void read(PacketBuffer buf) {}

    @Override
    public void write(PacketBuffer buf) {
        buf.writeLong(timestamp);
        buf.writeInt(29);
    }
}
