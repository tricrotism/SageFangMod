package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

/**
 * Clientbound (ID 60): the server is closing the connection gracefully.
 *
 * <p>Dispatched to {@code LabyConnectSession.handleDisconnect}. Distinct from
 * {@link Kick} (ID 61), which signals a forced/administrative disconnect.
 */
@Getter
public class Disconnect extends Packet {
    /**
     * Human-readable reason for the disconnect, as sent by LabyConnect.
     */
    private String reason;

    public Disconnect() {}

    /**
     * Reads the single {@code writeString} reason (int32 length + UTF-8).
     */
    @Override
    public void read(PacketBuffer buf) {
        this.reason = buf.readString();
    }

    /**
     * No-op: this packet is only ever received, never sent by SageFang.
     */
    @Override
    public void write(PacketBuffer buf) {}
}
