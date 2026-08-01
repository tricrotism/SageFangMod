package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.UserStatus;

import java.util.TimeZone;

/**
 * Serverbound (ID 21): change your visibility options and online status at
 * runtime — the post-login counterpart to {@link LoginOptions} (ID 6).
 *
 * <p>Sent by {@code LabySocial.setStatus}.
 */
public class PlayChangeOptions extends Packet {
    /**
     * Whether friends may see the server the player is currently on.
     */
    private boolean showServer;
    /**
     * The desired online {@link UserStatus} (online/away/busy/…).
     */
    private UserStatus status;
    /**
     * The player's IANA time-zone id.
     */
    private String timeZoneId;

    public PlayChangeOptions() {}

    /**
     * Builds the change request, capturing the local default time zone.
     */
    public PlayChangeOptions(boolean showServer, UserStatus status) {
        this.showServer = showServer;
        this.status = status;
        this.timeZoneId = TimeZone.getDefault().getID();
    }

    /**
     * Reads a boolean {@code showServer}, a {@code readUserStatus} byte, and the
     * {@code writeString} time-zone id.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.showServer = buf.readBoolean();
        this.status = buf.readUserStatus();
        this.timeZoneId = buf.readString();
    }

    /**
     * Serializes the fields in the same order {@link #read} consumes them.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeBoolean(showServer);
        buf.writeUserStatus(status);
        buf.writeString(timeZoneId);
    }
}
