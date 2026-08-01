package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

import java.util.UUID;

/**
 * Serverbound (ID 69): subscribe to or unsubscribe from status updates for a
 * set of users, scoped to a channel (entities the player can see, or an
 * explicit friend list).
 *
 * <p>Sent by {@code LabyConnectSession} to register the UUIDs SageFang wants
 * presence updates for. Like {@link UserBadge}, UUIDs are encoded as raw
 * msb/lsb long pairs rather than the string form.
 */
public class UserTracker extends Packet {
    /**
     * {@link #channel} value: track entities currently visible to the player.
     */
    public static final int CHANNEL_ENTITIES = 0;
    /**
     * {@link #channel} value: track an explicit user/friend list.
     */
    public static final int CHANNEL_LIST = 1;
    /**
     * {@link #action} value: add the given UUIDs to the channel.
     */
    public static final int ACTION_ADD = 0;
    /**
     * {@link #action} value: remove the given UUIDs from the channel.
     */
    public static final int ACTION_REMOVE = 1;
    /**
     * {@link #action} value: clear the whole channel (no UUID payload follows).
     */
    public static final int ACTION_CLEAR = 3;
    /**
     * {@link #action} value: replace the channel with the given UUIDs.
     */
    public static final int ACTION_SYNC = 4;

    /**
     * Which tracking channel this operation applies to (see {@code CHANNEL_*}).
     */
    private int channel;
    /**
     * What to do with {@link #uuids} on that channel (see {@code ACTION_*}).
     */
    private int action;
    /**
     * The users affected; ignored/omitted for {@link #ACTION_CLEAR}.
     */
    private UUID[] uuids;

    public UserTracker() {}

    public UserTracker(int channel, int action, UUID[] uuids) {
        this.channel = channel;
        this.action = action;
        this.uuids = uuids;
    }

    /**
     * No-op: this packet is only ever sent, never received.
     */
    @Override
    public void read(PacketBuffer buf) {}

    /**
     * Writes a leading sub-type byte ({@code 6}), then the {@link #channel} and
     * {@link #action} bytes. Unless the action is {@link #ACTION_CLEAR}, an
     * int32 count and each UUID as two raw longs (msb, lsb) follow; when adding
     * to {@link #CHANNEL_LIST} a {@code false} "hasCape" boolean is appended per
     * entry.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeByte(6);
        buf.writeByte(channel);
        buf.writeByte(action);
        if (action != ACTION_CLEAR) {
            buf.writeInt(uuids.length);
            for (UUID uuid : uuids) {
                buf.writeLong(uuid.getMostSignificantBits());
                buf.writeLong(uuid.getLeastSignificantBits());
                if (channel == CHANNEL_LIST && action == ACTION_ADD) {
                    buf.writeBoolean(false); // hasCape
                }
            }
        }
    }
}
