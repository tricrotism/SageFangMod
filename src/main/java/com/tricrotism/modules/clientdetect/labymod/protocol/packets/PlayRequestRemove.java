package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;

/**
 * Serverbound (ID 18): cancel an outgoing friend request by name — the inverse
 * of {@link PlayRequestAddFriend} (ID 16).
 */
public class PlayRequestRemove extends Packet {
    /**
     * Username whose pending outgoing request should be cancelled.
     */
    private String playerName;

    public PlayRequestRemove() {}

    public PlayRequestRemove(String playerName) {
        this.playerName = playerName;
    }

    /**
     * Reads the single {@code writeString} username.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.playerName = buf.readString();
    }

    /**
     * Writes the username via {@code writeString}.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(playerName);
    }
}
