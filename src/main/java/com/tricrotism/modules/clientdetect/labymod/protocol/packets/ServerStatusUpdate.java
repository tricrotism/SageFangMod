package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

/**
 * Server-bound packet (ID 68) that publishes the server address the player
 * is currently on. Without this, LabyMod friends see the player as "away"
 * instead of playing on a server.
 */
public class ServerStatusUpdate extends Packet {
    private String serverIp;
    private int port;

    public ServerStatusUpdate() {}

    public ServerStatusUpdate(String serverIp, int port) {
        this.serverIp = serverIp;
        this.port = port;
    }

    @Override
    public void read(PacketBuffer buf) {
        this.serverIp = buf.readString();
        this.port = buf.readInt();
        buf.readBoolean(); // viaServerlist
        if (buf.readBoolean()) {
            buf.readString(); // gamemode
        }
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(serverIp);
        buf.writeInt(port);
        buf.writeBoolean(false); // viaServerlist
        buf.writeBoolean(false); // no gamemode
    }
}
