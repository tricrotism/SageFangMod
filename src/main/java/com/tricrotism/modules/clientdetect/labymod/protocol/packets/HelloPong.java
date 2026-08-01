package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

/**
 * Clientbound (ID 1): the server's reply to {@link HelloPing}, echoing the
 * client timestamp back.
 *
 * <p>Dispatched to {@code LabyConnectSession.handleHelloPong}, which then
 * begins the login sequence ({@link LoginVersion}, {@link LoginData},
 * {@link LoginOptions}).
 */
@Getter
public class HelloPong extends Packet {
    /**
     * The client timestamp originally sent in {@link HelloPing}.
     */
    private long timestamp;

    public HelloPong() {}

    /**
     * Reads the single 8-byte echoed timestamp.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.timestamp = buf.readLong();
    }

    /**
     * No-op: this packet is only ever received, never sent.
     */
    @Override
    public void write(PacketBuffer buf) {}
}
