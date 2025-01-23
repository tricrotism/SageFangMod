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

public class PlayerInfoMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
        if (Minecraft.getInstance().screen == null) {
            flags |= ImGuiWindowFlags.NoInputs;
        }

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin("Player Info Menu", flags);
        ImGui.text("Player Info");
        ImGui.beginTabBar("Player Info");

        if (Minecraft.getInstance().player != null) {
            ImGui.text("FPS: " + Minecraft.getInstance().getFps());
            ImGui.text("X: " + round(Minecraft.getInstance().player.getX()));
            ImGui.text("Y: " + round(Minecraft.getInstance().player.getY()));
            ImGui.text("Z: " + round(Minecraft.getInstance().player.getZ()));
            ImGui.text("World: " + Minecraft.getInstance().player.level().dimension().location());
            long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryMax = Runtime.getRuntime().maxMemory();
            ImGui.text("Memory: " + NumberUtils.formatMemorySize(memoryUsed) + "/" + NumberUtils.formatMemorySize(memoryMax));
            ImGui.text("Packets: " + Math.round(Minecraft.getInstance().player.connection.getConnection().getAverageReceivedPackets()));
            ImGui.text("Server: " + (Minecraft.getInstance().isSingleplayer() ? "Singleplayer" : Minecraft.getInstance().player.connection.getConnection().getRemoteAddress()));
        } else {
            ImGui.text("No player info");
        }

        ImGui.endTabBar();
        ImGui.end();
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }
}