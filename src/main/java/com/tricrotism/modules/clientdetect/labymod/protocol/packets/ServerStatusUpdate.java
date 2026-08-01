package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

/**
 * Server-bound packet (ID 68) that publishes the server address the player
 * is currently on. Without this, LabyMod friends see the player as "away"
 * instead of playing on a server.
 *
 * <p>Sent by {@code LabyConnectSession.handleLoginComplete} once the session is
 * authenticated.
 */
public class ServerStatusUpdate extends Packet {
    /**
     * Address of the Minecraft server the player is connected to.
     */
    private String serverIp;
    /**
     * Port of that server.
     */
    private int port;

    public ServerStatusUpdate() {}

    public ServerStatusUpdate(String serverIp, int port) {
        this.serverIp = serverIp;
        this.port = port;
    }

    /**
     * Reads the {@code writeString} address and int32 port, then skips the
     * optional trailing fields: a boolean {@code viaServerlist} flag and, when a
     * second boolean is set, a {@code writeString} gamemode.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.serverIp = buf.readString();
        this.port = buf.readInt();
        buf.readBoolean(); // viaServerlist
        if (buf.readBoolean()) {
            buf.readString(); // gamemode
        }
    }

    /**
     * Writes the address, port, a {@code false} {@code viaServerlist} flag, and
     * a {@code false} "has gamemode" flag (so no gamemode string follows).
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(serverIp);
        buf.writeInt(port);
        buf.writeBoolean(false); // viaServerlist
        buf.writeBoolean(false); // no gamemode
    }
}
