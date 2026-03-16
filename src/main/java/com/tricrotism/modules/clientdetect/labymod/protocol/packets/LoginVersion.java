package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

public class LoginVersion extends Packet {
    private String versionName;
    private int mcProtocolVersion;

    public LoginVersion() {}

    public LoginVersion(String versionName, int mcProtocolVersion) {
        this.versionName = versionName;
        this.mcProtocolVersion = mcProtocolVersion;
    }

    @Override
    public void read(PacketBuffer buf) {}

    @Override
    public void write(PacketBuffer buf) {
        buf.writeInt(29);
        buf.writeString(versionName);
        buf.writeString("");
        buf.writeInt(mcProtocolVersion);
    }
}
