package com.tricrotism;

import com.tricrotism.data.ServerInfo;
import com.tricrotism.data.ServerInfoCustomPayload;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;

public class InfoOverlay implements ModInitializer {
    private static ServerInfo lastServerInfo = null;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(ServerInfoCustomPayload.ID, ServerInfoCustomPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(
            ServerInfoCustomPayload.ID,
            (payload, ctx) -> lastServerInfo = payload.getServerInfo()
        );
    }

    public static void renderImGui() {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
        if (MinecraftClient.getInstance().currentScreen == null) {
            flags |= ImGuiWindowFlags.NoInputs;
        }

        ImGui.setNextWindowBgAlpha(0.45f);
        if (!ImGui.begin("Info Overlay", flags)) {
            ImGui.end();
            return;
        }


        if (lastServerInfo == null) {
            ImGui.text("No server info");
            ImGui.end();
            return;
        }

        ImGui.text("Instance: " + lastServerInfo.getInstance());
        ImGui.text("World: " + lastServerInfo.getWorld());
        ImGui.text("Uptime: " + lastServerInfo.getUptime());
        ImGui.text("Entity count: " + lastServerInfo.getEntityCount());
        ImGui.text("Loaded chunks: " + lastServerInfo.getLoadedChunks());
        ImGui.text("TPS: " + lastServerInfo.getTps());
        ImGui.text("MSPT: " + lastServerInfo.getMspt());
        ImGui.text("Memory free: " + lastServerInfo.getMemoryFree());
        ImGui.text("Memory max: " + lastServerInfo.getMemoryMax());
        ImGui.end();

        // ImGui.textColored(ImGuiHelper.getImGuiColor(Formatting.GREEN), "text");
        // public static int getImGuiColor(@NotNull Formatting formatting) {
        //     @Nullable Integer colorValue = formatting.getColorValue();
        //     if (colorValue == null) return 0;
        //     Color color = new Color(colorValue);
        //     return ImColor.rgb(color);
        // }
    }
}
