package com.tricrotism.data;

import com.google.gson.Gson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record ServerInfoCustomPayload(ServerInfo serverInfo) implements CustomPacketPayload {
    public static final Type<ServerInfoCustomPayload> ID = CustomPacketPayload.createType("custom/server_info");
    public static final StreamCodec<FriendlyByteBuf, ServerInfoCustomPayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, ServerInfoCustomPayload payload) {
            payload.write(buf);
        }

        @Override
        public @NotNull ServerInfoCustomPayload decode(FriendlyByteBuf buf) {
            String json = new String(buf.readByteArray());
            ServerInfo serverInfo = GSON.fromJson(json, ServerInfo.class);
            return new ServerInfoCustomPayload(serverInfo);
        }
    };

    private static final Gson GSON = new Gson();

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(GSON.toJson(serverInfo).getBytes());
    }

}