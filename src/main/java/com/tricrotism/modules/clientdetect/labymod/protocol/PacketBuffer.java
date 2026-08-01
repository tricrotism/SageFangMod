package com.tricrotism.modules.clientdetect.labymod.protocol;

import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.ServerInfo;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.UserStatus;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Thin reader/writer wrapper over a Netty {@link ByteBuf} that implements the
 * LabyConnect wire encoding. Used by every {@link Packet} for body
 * (de)serialization and by {@code LabyConnectClient} to read/write the leading
 * varint packet id.
 *
 * <p>Encoding notes (matched byte-for-byte against LabyMod's own
 * {@code PacketBuffer}):
 * <ul>
 *   <li><b>varint</b> &mdash; LEB128, little-endian groups, capped at 3 bytes
 *       (21 shift bits). Used only for packet ids / frame lengths.</li>
 *   <li><b>String</b> &mdash; a signed big-endian {@code int32} byte length
 *       followed by UTF-8 bytes (<i>not</i> a varint length, unlike vanilla MC).</li>
 *   <li><b>UUID</b> &mdash; written as its {@link UUID#toString()} String form,
 *       not as two longs.</li>
 *   <li>fixed-width numerics ({@code int}, {@code long}, {@code short},
 *       {@code double}, {@code byte}, {@code boolean}) are big-endian and
 *       delegate straight to {@link ByteBuf}.</li>
 * </ul>
 *
 * <p>Not thread-safe; a buffer is owned by the single Netty thread handling its
 * channel.
 */
public record PacketBuffer(ByteBuf buf) {
    private static final int MAX_STRING_LENGTH = 65535;
    private static final int MAX_BYTE_ARRAY_LENGTH = 65535;

    /**
     * Writes {@code value} as an LEB128 varint (1&ndash;3 bytes).
     */
    public void writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    /**
     * Reads an LEB128 varint.
     *
     * @throws RuntimeException if it exceeds 3 bytes (21 shift bits)
     */
    public int readVarInt() {
        int result = 0;
        int shift = 0;
        byte b;
        do {
            if (shift >= 21) {
                throw new RuntimeException("VarInt too long (max 3 bytes)");
            }
            b = buf.readByte();
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }

    /**
     * Writes a UTF-8 string as a 4-byte big-endian length prefix plus its bytes.
     */
    public void writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    /**
     * Reads a length-prefixed UTF-8 string.
     *
     * @throws RuntimeException if the decoded length is negative or exceeds
     *                          {@value #MAX_STRING_LENGTH}
     */
    public String readString() {
        int length = buf.readInt();
        if (length < 0 || length > MAX_STRING_LENGTH) {
            throw new RuntimeException("String length out of bounds: " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Writes a raw byte array as a 4-byte big-endian length prefix plus its bytes.
     */
    public void writeByteArray(byte[] value) {
        buf.writeInt(value.length);
        buf.writeBytes(value);
    }

    /**
     * Reads a length-prefixed byte array (used for crypto key/token blobs).
     *
     * @throws RuntimeException if the decoded length is negative or exceeds
     *                          {@value #MAX_BYTE_ARRAY_LENGTH}
     */
    public byte[] readByteArray() {
        int length = buf.readInt();
        if (length < 0 || length > MAX_BYTE_ARRAY_LENGTH) {
            throw new RuntimeException("Byte array length out of bounds: " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }

    /**
     * Writes a UUID as its canonical dashed {@link UUID#toString()} String.
     */
    public void writeUUID(UUID uuid) {
        writeString(uuid.toString());
    }

    /**
     * Reads a UUID encoded as its dashed String form.
     */
    public UUID readUUID() {
        return UUID.fromString(readString());
    }

    public void writeLong(long value) {
        buf.writeLong(value);
    }

    public long readLong() {
        return buf.readLong();
    }

    public void writeInt(int value) {
        buf.writeInt(value);
    }

    public int readInt() {
        return buf.readInt();
    }

    public void writeShort(int value) {
        buf.writeShort(value);
    }

    public short readShort() {
        return buf.readShort();
    }

    public void writeBoolean(boolean value) {
        buf.writeBoolean(value);
    }

    public boolean readBoolean() {
        return buf.readBoolean();
    }

    public void writeByte(int value) {
        buf.writeByte(value);
    }

    public byte readByte() {
        return buf.readByte();
    }

    public void writeDouble(double value) {
        buf.writeDouble(value);
    }

    public double readDouble() {
        return buf.readDouble();
    }

    /**
     * Writes a {@link UserStatus} as its single signed-byte wire id.
     */
    public void writeUserStatus(UserStatus status) {
        buf.writeByte(status.getId());
    }

    /**
     * Reads a {@link UserStatus} from its signed-byte id, defaulting to
     * {@link UserStatus#OFFLINE} for unknown values.
     */
    public UserStatus readUserStatus() {
        return UserStatus.fromId(buf.readByte());
    }

    /**
     * Writes a {@link ServerInfo}: address String, int port, then a boolean
     * "has game mode" flag followed by the game-mode String only when present.
     * A {@code null} argument is encoded as an empty server (empty address,
     * port 0, no game mode).
     */
    public void writeServerInfo(ServerInfo info) {
        if (info == null) info = new ServerInfo("", 0, null);
        writeString(info.getAddress() == null ? "" : info.getAddress());
        writeInt(info.getPort());
        if (info.getGameMode() != null) {
            writeBoolean(true);
            writeString(info.getGameMode());
        } else {
            writeBoolean(false);
        }
    }

    /**
     * Reads a {@link ServerInfo} written by {@link #writeServerInfo(ServerInfo)}.
     */
    public ServerInfo readServerInfo() {
        String address = readString();
        int port = readInt();
        String gameMode = readBoolean() ? readString() : null;
        return new ServerInfo(address, port, gameMode);
    }

    /**
     * Writes a {@link Friend} in the LabyConnect "chat user" layout, in field
     * order: name, uuid, status message, {@link UserStatus}, friend-request
     * flag, time zone, contact amount, last-online millis, first-joined millis,
     * and embedded {@link ServerInfo}. Null Strings are coerced to empty.
     * Inverse of {@link #readChatUser()}.
     */
    public void writeUser(Friend friend) {
        writeString(friend.getName());
        writeUUID(friend.getUuid());
        writeString(friend.getStatusMessage() == null ? "" : friend.getStatusMessage());
        writeUserStatus(friend.getStatus());
        writeBoolean(friend.isFriendRequest());
        writeString(friend.getTimeZone() == null ? "" : friend.getTimeZone());
        writeInt(friend.getContactAmount());
        writeLong(friend.getLastOnline());
        writeLong(friend.getFirstJoined());
        writeServerInfo(friend.getServer());
    }

    /**
     * Reads a {@link Friend} in the "chat user" layout written by
     * {@link #writeUser(Friend)}; the field order must stay in lockstep.
     */
    public Friend readChatUser() {
        Friend friend = new Friend();
        friend.setName(readString());
        friend.setUuid(readUUID());
        friend.setStatusMessage(readString());
        friend.setStatus(readUserStatus());
        friend.setFriendRequest(readBoolean());
        friend.setTimeZone(readString());
        friend.setContactAmount(readInt());
        friend.setLastOnline(readLong());
        friend.setFirstJoined(readLong());
        friend.setServer(readServerInfo());
        return friend;
    }
}
