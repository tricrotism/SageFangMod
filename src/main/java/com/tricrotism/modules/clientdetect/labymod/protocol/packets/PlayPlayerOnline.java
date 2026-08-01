package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import lombok.Getter;

/**
 * Clientbound (ID 14): a friend has come online.
 *
 * <p>Consumed by {@code LabySocial.onPlayerOnline}, which refreshes the cached
 * friend and surfaces an "is now online" alert.
 */
@Getter
public class PlayPlayerOnline extends Packet {
    /**
     * The friend who just came online.
     */
    private Friend player;

    /**
     * Reads the single {@code readChatUser} ({@link Friend}) entry.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.player = buf.readChatUser();
    }

    /**
     * Writes the friend via {@code writeUser}.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeUser(player);
    }
}
