package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

@Getter
public class Kick extends Packet {
    private String reason;

    public Kick() {}

    @Override
    public void read(PacketBuffer buf) {
        this.reason = buf.readString();
    }

    @Override
    public void write(PacketBuffer buf) {}
}
