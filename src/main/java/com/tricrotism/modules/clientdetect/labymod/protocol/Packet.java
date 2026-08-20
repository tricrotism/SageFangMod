package com.tricrotism.modules.clientdetect.labymod.protocol;

/**
 * Base type for every LabyConnect protocol packet. A packet knows how to
 * (de)serialize its own body. The leading varint packet id is handled by the
 * {@link PacketRegistry} and the framing layer, not here.
 *
 * <p>Each concrete subclass is registered against a numeric id in
 * {@code LabyConnectClient.registerPackets()} and matches the wire layout of
 * the corresponding LabyMod {@code Packet*} class byte-for-byte.
 */
public abstract class Packet {
    /**
     * Reads this packet's body from {@code buf}, populating its fields. Called
     * on the Netty event-loop thread for inbound (client-bound) packets after
     * the id has already been consumed.
     */
    public abstract void read(PacketBuffer buf);

    /**
     * Writes this packet's body into {@code buf}. Called when sending; the
     * caller has already written the varint packet id before invoking this.
     */
    public abstract void write(PacketBuffer buf);
}
