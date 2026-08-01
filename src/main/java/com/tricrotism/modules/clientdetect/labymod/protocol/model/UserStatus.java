package com.tricrotism.modules.clientdetect.labymod.protocol.model;

import lombok.Getter;

/**
 * Online presence shown to LabyConnect friends. Wire id is a single signed
 * byte (note OFFLINE is -1).
 */
public enum UserStatus {
    ONLINE((byte) 0, "<emerald>"),
    AWAY((byte) 1, "<gold>"),
    BUSY((byte) 2, "<scarlet>"),
    OFFLINE((byte) -1, "<slate_gray>");

    /**
     * The signed-byte wire id sent in LabyConnect status fields.
     */
    @Getter private final byte id;
    /**
     * MiniMessage palette tag used to colour this status in chat/UI.
     */
    @Getter private final String colorTag;

    UserStatus(byte id, String colorTag) {
        this.id = id;
        this.colorTag = colorTag;
    }

    /**
     * Maps a wire id back to a status, defaulting to {@link #OFFLINE} when unknown.
     */
    public static UserStatus fromId(byte id) {
        for (UserStatus s : values()) {
            if (s.id == id) return s;
        }
        return OFFLINE;
    }
}
