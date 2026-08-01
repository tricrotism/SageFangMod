package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

/**
 * Serverbound (ID 63): the keep-alive reply to {@link Ping} (ID 62), carrying
 * no payload.
 *
 * <p>Sent by {@code LabyConnectSession.handlePing} in response to each server
 * heartbeat.
 */
public class Pong extends Packet {

    public Pong() {}

    /**
     * No-op: this packet is only ever sent, never received.
     */
    @Override
    public void read(PacketBuffer buf) {}

    /**
     * No-op: the pong carries no fields.
     */
    @Override
    public void write(PacketBuffer buf) {}
}
