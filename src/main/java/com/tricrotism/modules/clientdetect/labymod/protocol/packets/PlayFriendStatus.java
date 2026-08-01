package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.ServerInfo;
import lombok.Getter;

/**
 * Clientbound (ID 23): a friend's status and/or server changed.
 *
 * <p>Consumed by {@code LabySocial.onFriendStatus}, which attaches the new
 * {@link ServerInfo} to the cached friend.
 */
@Getter
public class PlayFriendStatus extends Packet {
    /**
     * The friend whose status changed.
     */
    private Friend player;
    /**
     * The friend's current server location (address/port/gamemode).
     */
    private ServerInfo playerInfo;

    /**
     * Reads a {@code readChatUser} entry followed by a {@code readServerInfo} block.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.player = buf.readChatUser();
        this.playerInfo = buf.readServerInfo();
    }

    /**
     * Serializes the friend and server info in read order.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeUser(player);
        buf.writeServerInfo(playerInfo);
    }
}
