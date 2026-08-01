package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

/**
 * Serverbound (ID 9): the client's Minecraft version and protocol number,
 * sent during login by {@code LabyConnectSession.handleHelloPong}.
 */
public class LoginVersion extends Packet {
    /**
     * Human-readable Minecraft version string (e.g. {@code "1.21.10"}).
     */
    private String versionName;
    /**
     * Numeric Minecraft network protocol version.
     */
    private int mcProtocolVersion;

    public LoginVersion() {}

    public LoginVersion(String versionName, int mcProtocolVersion) {
        this.versionName = versionName;
        this.mcProtocolVersion = mcProtocolVersion;
    }

    /**
     * No-op: this packet is only ever sent, never received.
     */
    @Override
    public void read(PacketBuffer buf) {}

    /**
     * Writes an int32 LabyConnect version constant ({@code 29}), the
     * {@link #versionName}, a reserved empty {@code writeString}, and finally
     * the int32 {@link #mcProtocolVersion}.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeInt(29);
        buf.writeString(versionName);
        buf.writeString("");
        buf.writeInt(mcProtocolVersion);
    }
}
