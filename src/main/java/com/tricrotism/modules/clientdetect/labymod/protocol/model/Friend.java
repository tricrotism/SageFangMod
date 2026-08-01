package com.tricrotism.modules.clientdetect.labymod.protocol.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A LabyConnect friend (or pending friend request when {@link #friendRequest}
 * is true). Fields mirror the wire layout of {@code PacketBuffer.readChatUser}.
 */
@Getter
@Setter
public class Friend {
    private UUID uuid;
    private String name;
    private String statusMessage = "";
    private UserStatus status = UserStatus.OFFLINE;
    private boolean friendRequest;
    private String timeZone = "";
    private int contactAmount;
    private long lastOnline;
    private long firstJoined;
    private ServerInfo server = new ServerInfo("", 0, null);

    /**
     * Empty friend, populated field-by-field by {@code PacketBuffer.readChatUser}.
     */
    public Friend() {}

    /**
     * Minimal friend with just identity set — used to build the local-player
     * "self" friend and other senders/recipients where the full profile is
     * unknown.
     */
    public Friend(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}
