package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;

/**
 * Serverbound (ID 19): deny an incoming friend request.
 *
 * <p>Sent by {@code LabySocial.denyRequest}.
 */
public class PlayDenyFriendRequest extends Packet {
    /**
     * The requesting user whose pending request is being rejected.
     */
    private Friend denied;

    public PlayDenyFriendRequest() {}

    public PlayDenyFriendRequest(Friend denied) {
        this.denied = denied;
    }

    /**
     * Reads the single {@code readChatUser} ({@link Friend}) entry.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.denied = buf.readChatUser();
    }

    /**
     * Writes the denied {@link Friend} via {@code writeUser}.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeUser(denied);
    }
}
