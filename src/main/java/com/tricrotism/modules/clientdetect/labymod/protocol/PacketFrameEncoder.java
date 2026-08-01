package com.tricrotism.modules.clientdetect.labymod.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Outbound length-prefix prepender for the LabyConnect stream. Takes a
 * fully-serialized packet body (id varint + payload, produced by
 * {@code LabyConnectClient.sendPacket}) and prefixes it with a varint of its
 * byte length, producing the wire frame that {@link PacketFrameDecoder} reads.
 * Sits in the Netty pipeline as {@code "prepender"} (before the encryption
 * stage once encryption is enabled).
 */
public class PacketFrameEncoder extends MessageToByteEncoder<ByteBuf> {
    /**
     * Writes {@code msg.readableBytes()} as a varint, then copies the body.
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int length = msg.readableBytes();
        writeVarInt(out, length);
        out.writeBytes(msg);
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }
}
