package com.tricrotism.features.menus;

import com.tricrotism.Main;
import com.tricrotism.Menu;
import com.tricrotism.config.Config;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

public class SkillCrashMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!Main.getConfig().skillsCrashMenu) {
                return;
            }

            int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Skills Crash Menu", flags);

            ImGui.text("Enabled");
            ImGui.sameLine();
            if (ImGui.checkbox("##skillCrash", Main.getConfig().skillCrash)) {
                Main.getConfig().skillCrash = !Main.getConfig().skillCrash;
                Config.write();
            }

            ImGui.text("Amount");
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.pushTextWrapPos(ImGui.getFontSize() * 35f);
                ImGui.textUnformatted("Amount of packets per tick.");
                ImGui.popTextWrapPos();
                ImGui.endTooltip();
            }
            ImGui.sameLine();
            int[] amountArray = {Main.getConfig().amountOfPacketsPerTick};
            if (ImGui.sliderInt("##amountPerTick", amountArray, Main.getConfig().amountOfPacketsPerTickMin, Main.getConfig().amountOfPacketsPerTickMax)) {
                Main.getConfig().amountOfPacketsPerTick = amountArray[0];
                Config.write();
            }

            ImGui.text("Buffer");
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.pushTextWrapPos(ImGui.getFontSize() * 35f);
                ImGui.textUnformatted("Spam buffer size.");
                ImGui.popTextWrapPos();
                ImGui.endTooltip();
            }
            ImGui.sameLine();
            int[] bufferArray = {Main.getConfig().skillCrashBuffer};
            if (ImGui.sliderInt("##spamBufferSize", bufferArray, Main.getConfig().skillCrashBufferMin, Main.getConfig().skillCrashBufferMax)) {
                Main.getConfig().skillCrashBuffer = bufferArray[0];
                Config.write();
            }

            ImGui.end();
        } catch (Exception e) {
            Main.LOGGER.error("Error in SkillCrashMenu", e);
        }
    }
}