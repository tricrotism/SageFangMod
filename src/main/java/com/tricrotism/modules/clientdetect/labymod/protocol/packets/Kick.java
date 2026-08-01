package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

/**
 * Clientbound (ID 61): the server forcibly disconnected the client.
 *
 * <p>Dispatched to {@code LabyConnectSession.handleKick}. Unlike
 * {@link Disconnect} (ID 60, a graceful close), this signals an
 * administrative/forced kick.
 */
@Getter
public class Kick extends Packet {
    /**
     * Human-readable kick reason supplied by the server.
     */
    private String reason;

    public Kick() {}

    /**
     * Reads the single {@code writeString} reason (int32 length + UTF-8).
     */
    @Override
    public void read(PacketBuffer buf) {
        this.reason = buf.readString();
    }

    /**
     * No-op: this packet is only ever received, never sent.
     */
    @Override
    public void write(PacketBuffer buf) {}
}
