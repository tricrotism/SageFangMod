package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

import java.util.UUID;

/**
 * Clientbound (ID 33): badge/rank assignments for a batch of users.
 *
 * <p>Dispatched to {@code LabyConnectSession.handleUserBadge}. Note the wire
 * format here encodes UUIDs as two raw 8-byte longs (msb/lsb), <em>not</em> via
 * {@code PacketBuffer.writeUUID} (which uses the UUID string form).
 */
@Getter
public class UserBadge extends Packet {
    /**
     * The users this batch describes, parallel to {@link #ranks}.
     */
    private UUID[] uuids;
    /**
     * One rank byte per UUID, in the same order as {@link #uuids}.
     */
    private byte[] ranks;

    public UserBadge() {}

    /**
     * Reads a VarInt count, then that many UUIDs as raw long pairs
     * (most- then least-significant bits), followed by the same number of rank
     * bytes.
     */
    @Override
    public void read(PacketBuffer buf) {
        int count = buf.readVarInt();
        this.uuids = new UUID[count];
        for (int i = 0; i < count; i++) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            this.uuids[i] = new UUID(msb, lsb);
        }
        this.ranks = new byte[count];
        for (int i = 0; i < count; i++) {
            this.ranks[i] = buf.readByte();
        }
    }

    /**
     * No-op: this packet is only ever received, never sent.
     */
    @Override
    public void write(PacketBuffer buf) {}
}
