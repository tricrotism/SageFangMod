package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

/**
 * Clientbound (ID 62): a keep-alive heartbeat from the server, carrying no
 * payload.
 *
 * <p>Dispatched to {@code LabyConnectSession.handlePing}, which immediately
 * replies with {@link Pong} (ID 63) to keep the connection from timing out.
 */
public class Ping extends Packet {

    public Ping() {}

    /**
     * No-op: the ping carries no fields.
     */
    @Override
    public void read(PacketBuffer buf) {}

    /**
     * No-op: SageFang never sends this; the client answers with {@link Pong}.
     */
    @Override
    public void write(PacketBuffer buf) {}
}
