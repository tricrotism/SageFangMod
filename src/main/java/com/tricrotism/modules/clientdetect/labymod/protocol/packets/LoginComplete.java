package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

@Getter
public class LoginComplete extends Packet {
    private String dashboardPin;

    public LoginComplete() {}

    @Override
    public void read(PacketBuffer buf) {
        this.dashboardPin = buf.readString();
    }

    @Override
    public void write(PacketBuffer buf) {}
}
