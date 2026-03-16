package com.tricrotism.modules.clientdetect.labymod.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record PacketBuffer(ByteBuf buf) {
    private static final int MAX_STRING_LENGTH = 65535;
    private static final int MAX_BYTE_ARRAY_LENGTH = 65535;

    // --- VarInt ---

    public void writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

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

    // --- String (int32 BE length + UTF-8) ---

    public void writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public String readString() {
        int length = buf.readInt();
        if (length < 0 || length > MAX_STRING_LENGTH) {
            throw new RuntimeException("String length out of bounds: " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // --- ByteArray (int32 BE length + raw bytes) ---

    public void writeByteArray(byte[] value) {
        buf.writeInt(value.length);
        buf.writeBytes(value);
    }

    public byte[] readByteArray() {
        int length = buf.readInt();
        if (length < 0 || length > MAX_BYTE_ARRAY_LENGTH) {
            throw new RuntimeException("Byte array length out of bounds: " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }

    // --- UUID (string-serialized) ---

    public void writeUUID(UUID uuid) {
        writeString(uuid.toString());
    }

    public UUID readUUID() {
        return UUID.fromString(readString());
    }

    // --- Primitive delegates ---

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
}
