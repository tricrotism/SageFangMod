package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

import java.util.UUID;

public class LoginData extends Packet {
    private UUID uuid;
    private String username;

    public LoginData() {}

    public LoginData(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    @Override
    public void read(PacketBuffer buf) {}

    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(uuid.toString());
        buf.writeString(username);
        buf.writeString("");
    }
}
