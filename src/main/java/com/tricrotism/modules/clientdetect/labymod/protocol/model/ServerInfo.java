package com.tricrotism.modules.clientdetect.labymod.protocol.model;

import lombok.Getter;
import lombok.Setter;

/**
 * The server a friend is currently on, as carried by LabyConnect friend
 * packets. {@code gameMode} is optional (nullable).
 */
@Getter
@Setter
public class ServerInfo {
    private String address;
    private int port;
    private String gameMode;

    public ServerInfo() {}

    public ServerInfo(String address, int port, String gameMode) {
        this.address = address;
        this.port = port;
        this.gameMode = gameMode;
    }

    /**
     * True when the friend is actually on a server (non-empty address).
     */
    public boolean isPresent() {
        return address != null && !address.isEmpty();
    }
}
