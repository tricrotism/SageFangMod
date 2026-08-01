package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

/**
 * Clientbound (ID 17): result of an outgoing {@link PlayRequestAddFriend}
 * (ID 16).
 *
 * <p>Consumed by {@code LabySocial.onAddFriendResponse}, which reports success
 * or the failure {@link #reason} to the player.
 */
@Getter
public class PlayRequestAddFriendResponse extends Packet {
    /**
     * The username that was searched for.
     */
    private String searched;
    /**
     * Whether the request was successfully sent.
     */
    private boolean requestSent;
    /**
     * Failure reason; present (and read) only when {@link #requestSent} is false.
     */
    private String reason;

    /**
     * Reads the {@code writeString} searched name and the boolean success flag;
     * the trailing {@code writeString} reason is only present when the request
     * was not sent.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.searched = buf.readString();
        this.requestSent = buf.readBoolean();
        if (!requestSent) {
            this.reason = buf.readString();
        }
    }

    /**
     * Serializes the searched name and success flag, appending the reason
     * (empty when null) only on failure, mirroring {@link #read}.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(searched);
        buf.writeBoolean(requestSent);
        if (!requestSent) {
            buf.writeString(reason == null ? "" : reason);
        }
    }
}
