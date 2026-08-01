package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import lombok.Getter;

/**
 * Clientbound (ID 24): a friend joined a server (with its gamemode name).
 *
 * <p>Consumed by {@code LabySocial.onPlayingOn}, which updates the cached
 * friend and surfaces a "now playing on …" alert.
 */
@Getter
public class PlayFriendPlayingOn extends Packet {
    /**
     * The friend whose location changed.
     */
    private Friend player;
    /**
     * The server's advertised gamemode name (may be empty).
     */
    private String gameModeName;

    /**
     * Reads a {@code readChatUser} entry followed by the {@code writeString} gamemode name.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.player = buf.readChatUser();
        this.gameModeName = buf.readString();
    }

    /**
     * Serializes the friend and gamemode name in read order.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeUser(player);
        buf.writeString(gameModeName);
    }
}
