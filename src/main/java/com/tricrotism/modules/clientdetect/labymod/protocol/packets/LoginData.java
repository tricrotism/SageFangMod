package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

import java.util.UUID;

/**
 * Serverbound (ID 3): the player's identity, sent during login.
 *
 * <p>Issued by {@code LabyConnectSession.handleHelloPong} alongside
 * {@link LoginVersion} and {@link LoginOptions}.
 */
public class LoginData extends Packet {
    /**
     * The player's Mojang profile UUID.
     */
    private UUID uuid;
    /**
     * The player's Minecraft username.
     */
    private String username;

    public LoginData() {}

    public LoginData(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    /**
     * No-op: this packet is only ever sent, never received.
     */
    @Override
    public void read(PacketBuffer buf) {}

    /**
     * Writes three {@code writeString} fields: the UUID rendered via
     * {@link UUID#toString()}, the username, and a trailing empty string
     * (a reserved/unused slot in the LabyConnect login form).
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(uuid.toString());
        buf.writeString(username);
        buf.writeString("");
    }
}
