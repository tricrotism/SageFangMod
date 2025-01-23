package com.tricrotism.menus;

import com.tricrotism.Menu;
import com.tricrotism.utils.NumberUtils;
import com.tricrotism.utils.TimeUtils;
import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

import static com.tricrotism.Main.lastServerInfo;

public class ServerInfoMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
        if (Minecraft.getInstance().screen == null) {
            flags |= ImGuiWindowFlags.NoInputs;
        }

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin("Server Info Menu", flags);
        ImGui.text("Server Info");
        ImGui.beginTabBar("Server Info");

        if (lastServerInfo == null) {
            ImGui.text("No server info");
            ImGui.endTabBar();
            ImGui.end();
            return;
        }

        ImGui.text("Instance: " + lastServerInfo.getInstance());
        ImGui.text("World: " + lastServerInfo.getWorld());
        ImGui.text("Uptime: " + TimeUtils.getTimeAmount(lastServerInfo.getUptime(), true, false));
        ImGui.text("Entity count: " + lastServerInfo.getEntityCount());
        ImGui.text("Loaded chunks: " + lastServerInfo.getLoadedChunks());
        ImGui.textColored(formatTPSColors(lastServerInfo.getTps()), "TPS: " + round(lastServerInfo.getTps()));
        ImGui.text("MSPT: " + round(lastServerInfo.getMspt()));
        ImGui.text("Memory free: " + NumberUtils.formatMemorySize(lastServerInfo.getMemoryMax() - lastServerInfo.getMemoryFree()));
        ImGui.text("Current Memory: " + NumberUtils.formatMemorySize(lastServerInfo.getMemoryMax() - lastServerInfo.getMemoryFree()));
        ImGui.text("Memory max: " + NumberUtils.formatMemorySize(lastServerInfo.getMemoryMax()));
        ImGui.endTabBar();
        ImGui.end();
    }

    public static int formatTPSColors(float tps) {
        if (tps >= 19.5) {
            return ImColor.rgb("#00FF00");
        } else if (tps >= 18.5) {
            return ImColor.rgb("#FFFF00");
        } else {
            return ImColor.rgb("#FF0000");
        }
    }

    private float round(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }
}