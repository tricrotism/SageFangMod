package com.tricrotism.features.menus;

import com.tricrotism.Main;
import com.tricrotism.Menu;
import com.tricrotism.config.Config;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

public class MiscMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!Main.getConfig().miscMenu) {
                return;
            }

            int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Misc Menu", flags);

            ImGui.text("Bypass Server Resource Pack");
            ImGui.sameLine();
            if (ImGui.checkbox("##bypassResourcePack", Main.getConfig().bypassResourcePack)) {
                Main.getConfig().bypassResourcePack = !Main.getConfig().bypassResourcePack;
                Config.write();
            }

            ImGui.text("Force Deny Resource Pack");
            ImGui.sameLine();
            if (ImGui.checkbox("##resourcePackForceDeny", Main.getConfig().resourcePackForceDeny)) {
                Main.getConfig().resourcePackForceDeny = !Main.getConfig().resourcePackForceDeny;
                Config.write();
            }

            ImGui.text("Delay UI Packets");
            ImGui.sameLine();
            if (ImGui.checkbox("##delayUIPackets", Main.getConfig().delayUIPackets)) {
                Main.getConfig().delayUIPackets = !Main.getConfig().delayUIPackets;
                Config.write();
            }

            ImGui.text("Send UI Packets");
            ImGui.sameLine();
            if (ImGui.checkbox("##sendUIPackets", Main.getConfig().sendUIPackets)) {
                Main.getConfig().sendUIPackets = !Main.getConfig().sendUIPackets;
                Config.write();
            }

            ImGui.text("Edit Sign");
            ImGui.sameLine();
            if (ImGui.checkbox("##shouldEditSign", Main.getConfig().shouldEditSign)) {
                Main.getConfig().shouldEditSign = !Main.getConfig().shouldEditSign;
                Config.write();
            }

            ImGui.text("Force Wake Up");
            ImGui.sameLine();
            if (ImGui.checkbox("##shouldForceWakeUp", Main.getConfig().shouldForceWakeUp)) {
                Main.getConfig().shouldForceWakeUp = !Main.getConfig().shouldForceWakeUp;
                Config.write();
            }

            ImGui.end();
        } catch (Exception e) {
            Main.LOGGER.error("Error in MiscMenu", e);
        }
    }
}