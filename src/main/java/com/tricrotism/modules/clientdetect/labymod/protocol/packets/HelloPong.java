package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

@Getter
public class HelloPong extends Packet {
    private long timestamp;

    public HelloPong() {}

    @Override
    public void read(PacketBuffer buf) {
        this.timestamp = buf.readLong();
    }

    @Override
    public void write(PacketBuffer buf) {}
}
