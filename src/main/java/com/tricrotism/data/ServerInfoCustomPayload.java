package com.tricrotism.data;

import com.google.gson.Gson;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public class ServerInfoCustomPayload implements CustomPayload {
    public static final CustomPayload.Id<ServerInfoCustomPayload> ID = CustomPayload.id("custom/server_info");
    public static final PacketCodec<PacketByteBuf, ServerInfoCustomPayload> CODEC = CustomPayload.codecOf(
        ServerInfoCustomPayload::write, ServerInfoCustomPayload::new
    );

    private static final Gson GSON = new Gson();

    private final ServerInfo serverInfo;

    public ServerInfoCustomPayload(PacketByteBuf buf) {
        this.serverInfo = GSON.fromJson(new String(buf.readByteArray()), ServerInfo.class);
    }

    public ServerInfoCustomPayload(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBytes(GSON.toJson(serverInfo).getBytes());
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }
}
