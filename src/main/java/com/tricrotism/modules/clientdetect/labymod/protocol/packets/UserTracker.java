package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

import java.util.UUID;

public class UserTracker extends Packet {
    public static final int CHANNEL_ENTITIES = 0;
    public static final int CHANNEL_LIST = 1;
    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_CLEAR = 3;
    public static final int ACTION_SYNC = 4;

    private int channel;
    private int action;
    private UUID[] uuids;

    public UserTracker() {}

    public UserTracker(int channel, int action, UUID[] uuids) {
        this.channel = channel;
        this.action = action;
        this.uuids = uuids;
    }

    @Override
    public void read(PacketBuffer buf) {}

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
