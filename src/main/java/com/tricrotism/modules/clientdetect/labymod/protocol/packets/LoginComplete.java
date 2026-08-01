package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

/**
 * Clientbound (ID 7): login succeeded; the session is now authenticated.
 *
 * <p>Dispatched to {@code LabyConnectSession.handleLoginComplete}, which marks
 * the session ready and publishes the current server via
 * {@link ServerStatusUpdate}.
 */
@Getter
public class LoginComplete extends Packet {
    /**
     * Dashboard PIN issued for this session (used by LabyMod's web dashboard).
     */
    private String dashboardPin;

    public LoginComplete() {}

    /**
     * Reads the single {@code writeString} dashboard PIN.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.dashboardPin = buf.readString();
    }

    /**
     * No-op: this packet is only ever received, never sent.
     */
    @Override
    public void write(PacketBuffer buf) {}
}
