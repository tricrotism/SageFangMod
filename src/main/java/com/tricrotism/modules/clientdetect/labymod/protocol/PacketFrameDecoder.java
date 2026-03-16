package com.tricrotism.modules.clientdetect.labymod.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class PacketFrameDecoder extends ByteToMessageDecoder {
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
