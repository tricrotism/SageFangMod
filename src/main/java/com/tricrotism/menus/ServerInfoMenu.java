package com.tricrotism.menus;

import com.tricrotism.Menu;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.MinecraftClient;

import static com.tricrotism.Main.lastServerInfo;

public class ServerInfoMenu implements Menu {
    @Override
    public void frame() {
        ImGuiIO io = ImGui.getIO();
        MinecraftClient client = MinecraftClient.getInstance();
        io.setDisplaySize(client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight());

        ImGui.newFrame();
        ImGui.setNextWindowBgAlpha(0.45f);
        if (ImGui.begin("Info Overlay", ImGuiWindowFlags.AlwaysAutoResize)) {
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
            return;
        }

        ImGui.end();
        ImGui.endFrame();
        ImGui.render();
    }
}
