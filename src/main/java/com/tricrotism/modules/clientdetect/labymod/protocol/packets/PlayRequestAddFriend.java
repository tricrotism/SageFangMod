package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

/**
 * Serverbound (ID 16): request to add a friend by name. The wire form is an
 * int32 length + raw UTF-8 bytes, identical to {@code writeString}.
 *
 * <p>Sent by {@code LabySocial.addFriend} and also by
 * {@code LabySocial.acceptRequest} (accepting an incoming request is modelled
 * as sending the reciprocal add). The server replies with
 * {@link PlayRequestAddFriendResponse} (ID 17).
 */
public class PlayRequestAddFriend extends Packet {
    /**
     * The username of the player to add as a friend.
     */
    private String name;

    public PlayRequestAddFriend() {}

    public PlayRequestAddFriend(String name) {
        this.name = name;
    }

    /**
     * Reads the single {@code writeString} username.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.name = buf.readString();
    }

    /**
     * Writes the username via {@code writeString}.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(name);
    }
}
