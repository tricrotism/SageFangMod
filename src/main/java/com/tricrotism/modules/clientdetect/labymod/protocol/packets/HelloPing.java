package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

/**
 * Serverbound (ID 0): the very first packet of the LabyConnect handshake,
 * sent immediately after the channel opens in {@code LabyConnectClient}.
 *
 * <p>Carries the client's current timestamp; the server echoes it back in
 * {@link HelloPong} (ID 1) for latency measurement.
 */
public class HelloPing extends Packet {
    /**
     * Client epoch-millis timestamp, echoed back by the server.
     */
    private long timestamp;

    public HelloPing() {}

    public HelloPing(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * No-op: this packet is only ever sent, never received.
     */
    @Override
    public void read(PacketBuffer buf) {}

    /**
     * Writes the 8-byte {@link #timestamp} followed by an int32 {@code 29}
     * (the LabyConnect protocol/version constant).
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeLong(timestamp);
        buf.writeInt(29);
    }
}
