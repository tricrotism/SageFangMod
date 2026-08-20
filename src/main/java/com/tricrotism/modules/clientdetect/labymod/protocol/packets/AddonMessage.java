package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

/**
 * Server-bound or client-bound addon message (packet 32).
 * Carries a string key and a raw data payload (often GZIP-compressed JSON).
 * We only need to read it; no response required.
 *
 * <p>Registered clientbound under ID {@code 32} in
 * {@code LabyConnectClient.registerPackets()}. It is not dispatched by
 * {@code LabyConnectSession} and falls through to the "unhandled packet"
 * debug log. SageFang ignores addon traffic entirely.
 */
@Getter
public class AddonMessage extends Packet {
    private String key;
    private byte[] data;

    public AddonMessage() {}

    /**
     * Decodes the wire form: a {@code writeString} key (int32 length + UTF-8)
     * followed by a {@code writeByteArray} payload (int32 length + raw bytes).
     */
    @Override
    public void read(PacketBuffer buf) {
        this.key = buf.readString();
        this.data = buf.readByteArray();
    }

    /**
     * No-op: SageFang never sends addon messages, so nothing is serialized.
     */
    @Override
    public void write(PacketBuffer buf) {}
}
