package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;

/**
 * Serverbound (ID 20): remove an existing friend.
 *
 * <p>Sent by {@code LabySocial.removeFriend}.
 */
public class PlayFriendRemove extends Packet {
    /**
     * The friend to unfriend.
     */
    private Friend toRemove;

    public PlayFriendRemove() {}

    public PlayFriendRemove(Friend toRemove) {
        this.toRemove = toRemove;
    }

    /**
     * Reads the single {@code readChatUser} ({@link Friend}) entry.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.toRemove = buf.readChatUser();
    }

    /**
     * Writes the friend to remove via {@code writeUser}.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeUser(toRemove);
    }
}
