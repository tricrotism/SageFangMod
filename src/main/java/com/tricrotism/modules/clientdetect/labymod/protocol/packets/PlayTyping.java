package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import lombok.Getter;

/**
 * Typing indicator (ID 25) — clientbound when received, serverbound when sent.
 *
 * <p>Incoming indicators are handled by {@code LabySocial.onTyping} (which
 * tracks a per-friend typing timeout); outgoing ones are sent by
 * {@code LabySocial.sendTyping}.
 */
@Getter
public class PlayTyping extends Packet {
    /**
     * The user who is (or stopped) typing — the local player when sending.
     */
    private Friend player;
    /**
     * The conversation partner the typing is directed at.
     */
    private Friend inChatWith;
    /**
     * {@code true} while typing, {@code false} when typing stopped.
     */
    private boolean typing;

    public PlayTyping() {}

    public PlayTyping(Friend player, Friend inChatWith, boolean typing) {
        this.player = player;
        this.inChatWith = inChatWith;
        this.typing = typing;
    }

    /**
     * Reads two {@code readChatUser} entries (typist, partner) followed by the
     * boolean typing flag.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.player = buf.readChatUser();
        this.inChatWith = buf.readChatUser();
        this.typing = buf.readBoolean();
    }

    /**
     * Serializes the two friends and the typing flag in read order.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeUser(player);
        buf.writeUser(inChatWith);
        buf.writeBoolean(typing);
    }
}
