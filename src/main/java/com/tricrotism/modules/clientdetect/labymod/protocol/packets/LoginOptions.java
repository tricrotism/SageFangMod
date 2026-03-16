package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

import java.util.TimeZone;

public class LoginOptions extends Packet {

    public LoginOptions() {}

    @Override
    public void read(PacketBuffer buf) {}

    @Override
    public void write(PacketBuffer buf) {
        buf.writeBoolean(false);
        buf.writeByte(1);
        buf.writeString(TimeZone.getDefault().getID());
    }
}
