package com.tricrotism.modules.clientdetect.labymod.protocol.packets;

import com.tricrotism.modules.clientdetect.labymod.protocol.Packet;
import com.tricrotism.modules.clientdetect.labymod.protocol.PacketBuffer;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import lombok.Getter;

/**
 * Direct message (ID 65) — clientbound when received, serverbound when sent.
 *
 * <p>Incoming messages are handled by {@code LabySocial.onMessage} (appended to
 * chat history and surfaced via an alert); outgoing ones are built by
 * {@code LabySocial.sendMessage} with the four-arg constructor.
 */
@Getter
public class Message extends Packet {
    /**
     * Author of the message (the local player when sending).
     */
    private Friend sender;
    /**
     * Recipient of the message.
     */
    private Friend to;
    /**
     * The text body.
     */
    private String message;
    /**
     * Attachment size in bytes for file messages; {@code 0} for plain text.
     */
    private long fileSize;
    /**
     * Duration in seconds for voice messages; {@code 0} for plain text.
     */
    private double audioTime;
    /**
     * Epoch-millis the message was sent.
     */
    private long sentTime;

    public Message() {}

    /**
     * Builds an outgoing plain-text message (file size and audio time left zero).
     */
    public Message(Friend sender, Friend to, String message, long sentTime) {
        this.sender = sender;
        this.to = to;
        this.message = message;
        this.sentTime = sentTime;
    }

    /**
     * Reads two {@code readChatUser} entries (sender, recipient), the
     * {@code writeString} body, an 8-byte file size, an 8-byte double audio
     * time, and the 8-byte sent timestamp.
     */
    @Override
    public void read(PacketBuffer buf) {
        this.sender = buf.readChatUser();
        this.to = buf.readChatUser();
        this.message = buf.readString();
        this.fileSize = buf.readLong();
        this.audioTime = buf.readDouble();
        this.sentTime = buf.readLong();
    }

    /**
     * Serializes the fields in the same order {@link #read} consumes them.
     */
    @Override
    public void write(PacketBuffer buf) {
        buf.writeUser(sender);
        buf.writeUser(to);
        buf.writeString(message);
        buf.writeLong(fileSize);
        buf.writeDouble(audioTime);
        buf.writeLong(sentTime);
    }
}
