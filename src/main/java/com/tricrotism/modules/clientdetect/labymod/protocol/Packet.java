package com.tricrotism.modules.clientdetect.labymod.protocol;

public abstract class Packet {
    public abstract void read(PacketBuffer buf);

    public abstract void write(PacketBuffer buf);
}
