package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

import java.util.TimeZone;

/**
 * Serverbound (ID 6): the player's initial visibility/status preferences,
 * sent during login by {@code LabyConnectSession.handleHelloPong}.
 *
 * <p>The serverbound runtime equivalent for later changes is
 * {@link PlayChangeOptions} (ID 21).
 */
public class LoginOptions extends Packet {

    public LoginOptions() {}

    /**
     * No-op: this packet is only ever sent, never received.
     */
    @Override
    public void read(PacketBuffer buf) {}

    /**
     * Writes a boolean {@code showServer} (hardcoded {@code true} so friends can
     * see our current server), a status byte ({@code 0} = ONLINE), and the
     * default {@code writeString} time-zone id.
     */
    @Override
    public void write(PacketBuffer buf) {
        //writeBoolean | ShowServer, lets friends see the server we're on
        buf.writeBoolean(true);
        //writeByte | status ONLINE
        buf.writeByte(0);
        buf.writeString(TimeZone.getDefault().getID());
    }
}
