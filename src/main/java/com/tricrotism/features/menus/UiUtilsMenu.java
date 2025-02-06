package com.tricrotism.features.menus;

import com.tricrotism.Main;
import com.tricrotism.Menu;
import com.tricrotism.utils.UIUtilVariables;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

public class UiUtilsMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        try {
            int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Ui Utils Menu", flags);

            ImGui.text("Bypass Server Resource Pack");
            ImGui.sameLine();
            if (ImGui.checkbox("##bypassResourcePack", UIUtilVariables.bypassResourcePack)) {
                UIUtilVariables.bypassResourcePack = !UIUtilVariables.bypassResourcePack;
            }

            ImGui.text("Force Deny Resource Pack");
            ImGui.sameLine();
            if (ImGui.checkbox("##resourcePackForceDeny", UIUtilVariables.resourcePackForceDeny)) {
                UIUtilVariables.resourcePackForceDeny = !UIUtilVariables.resourcePackForceDeny;
            }

            ImGui.text("Delay UI Packets");
            ImGui.sameLine();
            if (ImGui.checkbox("##delayUIPackets", UIUtilVariables.delayUIPackets)) {
                UIUtilVariables.delayUIPackets = !UIUtilVariables.delayUIPackets;
            }

            ImGui.text("Send UI Packets");
            ImGui.sameLine();
            if (ImGui.checkbox("##sendUIPackets", UIUtilVariables.sendUIPackets)) {
                UIUtilVariables.sendUIPackets = !UIUtilVariables.sendUIPackets;
            }

            ImGui.text("Edit Sign");
            ImGui.sameLine();
            if (ImGui.checkbox("##shouldEditSign", UIUtilVariables.shouldEditSign)) {
                UIUtilVariables.shouldEditSign = !UIUtilVariables.shouldEditSign;
            }

            ImGui.text("Force Wake Up");
            ImGui.sameLine();
            if (ImGui.checkbox("##shouldForceWakeUp", UIUtilVariables.shouldForceWakeUp)) {
                UIUtilVariables.shouldForceWakeUp = !UIUtilVariables.shouldForceWakeUp;
            }

            ImGui.end();
        } catch (Exception e) {
            Main.LOGGER.error("Error in UiUtilsMenu", e);
        }
    }
}