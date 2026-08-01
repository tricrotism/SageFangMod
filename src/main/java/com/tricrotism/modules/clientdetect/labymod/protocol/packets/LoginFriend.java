package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Clientbound (ID 4): the full friend list sent right after login.
 *
 * <p>Consumed by {@code LabySocial.onFriendList}, which splits the entries into
 * accepted friends versus pending incoming requests (a {@link Friend} with its
 * {@code friendRequest} flag set).
 */
@Getter
public class LoginFriend extends Packet {
    /**
     * All friend entries, including pending requests, in server order.
     */
    private final List<Friend> friends = new ArrayList<>();

    /**
     * Reads an int32 count followed by that many {@code readChatUser} entries
     * (each a full {@link Friend} struct: name, UUID-as-string, status, etc.).
     */
    @Override
    public void read(PacketBuffer buf) {
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            friends.add(buf.readChatUser());
        }
    }

    /**
     * Writes an int32 count followed by each {@link Friend} via
     * {@code writeUser}. Provided for symmetry; SageFang only receives this.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeInt(friends.size());
        for (Friend f : friends) buf.writeUser(f);
    }
}
