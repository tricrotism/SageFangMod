package com.tricrotism.modules.clientdetect.labymod.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Inbound length-prefix splitter for the LabyConnect stream. Each frame on the
 * wire is a varint byte-length followed by that many bytes of packet payload;
 * this handler reassembles a complete payload slice before passing it on to the
 * packet handler. Sits in the Netty pipeline as {@code "splitter"} (after the
 * decryption stage once encryption is enabled).
 *
 * <p>Mirrors LabyMod's frame format: the length is a 1&ndash;3 byte varint
 * (capped at 21 shift bits). The matching outbound side is
 * {@link PacketFrameEncoder}.
 */
public class PacketFrameDecoder extends ByteToMessageDecoder {
    /**
     * Attempts to extract one complete frame. If the length varint or the full
     * payload has not arrived yet, the reader index is reset and the call
     * returns without emitting, so Netty retries on the next read. Empty frames
     * (length &le; 0) are silently dropped as keepalive/padding. A successful
     * frame is emitted as a retained slice (callers must release it).
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();

        int length = readVarInt(in);
        if (length == -1) {
            in.resetReaderIndex();
            return;
        }
        if (length <= 0) {
            return; // skip empty frames (keepalive/padding)
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        out.add(in.readRetainedSlice(length));
    }

    /**
     * Reads a varint from {@code buf} without committing if it is incomplete.
     *
     * @return the decoded value, or {@code -1} if the buffer ran out of bytes
     * mid-varint (signalling "need more data")
     * @throws RuntimeException if the varint exceeds 3 bytes (21 shift bits)
     */
    private static int readVarInt(ByteBuf buf) {
        int result = 0;
        int shift = 0;
        while (shift < 21) {
            if (!buf.isReadable()) {
                return -1;
            }
            byte b = buf.readByte();
            result |= (b & 0x7F) << shift;
            shift += 7;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new RuntimeException("VarInt too long (max 3 bytes)");
    }
}
