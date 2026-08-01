package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import lombok.Getter;

/**
 * Clientbound (ID 64): a system/broadcast message from LabyConnect.
 *
 * <p>Consumed by {@code LabySocial.onServerMessage}, which surfaces it as an
 * info alert. Distinct from {@link Message} (ID 65), a person-to-person DM.
 */
@Getter
public class ServerMessage extends Packet {
    /**
     * The broadcast/system text.
     */
    private String message;

    /**
     * Reads the single {@code writeString} message body.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.message = buf.readString();
    }

    /**
     * Writes the message via {@code writeString}.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(message);
    }
}
