package com.tricrotism.menus;

import com.tricrotism.Menu;
import com.tricrotism.utils.SharedVariables;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

public class UiUtilsMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
        if (Minecraft.getInstance().screen == null) {
            flags |= ImGuiWindowFlags.NoInputs;
        }

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin("Ui Utils Menu", flags);

        ImGui.text("Bypass Server Resource Pack");
        ImGui.sameLine();
        if (ImGui.checkbox("##bypassResourcePack", SharedVariables.bypassResourcePack)) {
            SharedVariables.bypassResourcePack = !SharedVariables.bypassResourcePack;
        }

        ImGui.text("Force Deny Resource Pack");
        ImGui.sameLine();
        if (ImGui.checkbox("##resourcePackForceDeny", SharedVariables.resourcePackForceDeny)) {
            SharedVariables.resourcePackForceDeny = !SharedVariables.resourcePackForceDeny;
        }

        ImGui.text("Delay UI Packets");
        ImGui.sameLine();
        if (ImGui.checkbox("##delayUIPackets", SharedVariables.delayUIPackets)) {
            SharedVariables.delayUIPackets = !SharedVariables.delayUIPackets;
        }

        ImGui.text("Send UI Packets");
        ImGui.sameLine();
        if (ImGui.checkbox("##sendUIPackets", SharedVariables.sendUIPackets)) {
            SharedVariables.sendUIPackets = !SharedVariables.sendUIPackets;
        }

        ImGui.text("Edit Sign");
        ImGui.sameLine();
        if (ImGui.checkbox("##shouldEditSign", SharedVariables.shouldEditSign)) {
            SharedVariables.shouldEditSign = !SharedVariables.shouldEditSign;
        }

        ImGui.text("Force Wake Up");
        ImGui.sameLine();
        if (ImGui.checkbox("##shouldForceWakeUp", SharedVariables.shouldForceWakeUp)) {
            SharedVariables.shouldForceWakeUp = !SharedVariables.shouldForceWakeUp;
        }

        ImGui.end();
    }
}