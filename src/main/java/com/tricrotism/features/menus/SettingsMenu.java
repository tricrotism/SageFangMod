package com.tricrotism.features.menus;

import com.tricrotism.Main;
import com.tricrotism.Menu;
import com.tricrotism.config.Config;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

public class SettingsMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        try {
            int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Settings Menu", flags);

            ImGui.text("Server Info Menu Enabled");
            ImGui.sameLine();
            if (ImGui.checkbox("##serverInfoMenuEnabled", Main.getConfig().serverInfoMenu)) {
                Main.getConfig().serverInfoMenu = !Main.getConfig().serverInfoMenu;
                Config.write();
            }

            ImGui.text("Player Info Menu Enabled");
            ImGui.sameLine();
            if (ImGui.checkbox("##playerInfoMenuEnabled", Main.getConfig().playerInfoMenu)) {
                Main.getConfig().playerInfoMenu = !Main.getConfig().playerInfoMenu;
                Config.write();
            }

            ImGui.text("Misc Menu Enabled");
            ImGui.sameLine();
            if (ImGui.checkbox("##miscMenuEnabled", Main.getConfig().miscMenu)) {
                Main.getConfig().miscMenu = !Main.getConfig().miscMenu;
                Config.write();
            }

            ImGui.text("Skill Crash Menu Enabled");
            ImGui.sameLine();
            if (ImGui.checkbox("##skillsCrashMenuEnabled", Main.getConfig().skillsCrashMenu)) {
                Main.getConfig().skillsCrashMenu = !Main.getConfig().skillsCrashMenu;
                Config.write();
            }

            ImGui.end();
        } catch (Exception e) {
            Main.LOGGER.error("Error in SettingsMenu", e);
        }
    }
}