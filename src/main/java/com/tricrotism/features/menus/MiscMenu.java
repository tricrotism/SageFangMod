package com.tricrotism.features.menus;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.config.SageFangConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

public class MiscMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!SageFangConfig.isMiscMenuEnabled()) {
                return;
            }

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Misc", flags);

            ImGui.separatorText("Resource Packs");

            if (ImGui.checkbox("Bypass Server Pack##bypassResourcePack", SageFangConfig.isBypassResourcePack())) {
                SageFangConfig.setBypassResourcePack(!SageFangConfig.isBypassResourcePack());
            }

            if (ImGui.checkbox("Force Deny Pack##resourcePackForceDeny", SageFangConfig.isResourcePackForceDeny())) {
                SageFangConfig.setResourcePackForceDeny(!SageFangConfig.isResourcePackForceDeny());
            }

            if (ImGui.checkbox("Disable Pack Unobf##disablePackUnobf", SageFangConfig.isDisablePackUnobf())) {
                SageFangConfig.setDisablePackUnobf(!SageFangConfig.isDisablePackUnobf());
            }

            ImGui.separatorText("Packets");

            if (ImGui.checkbox("Delay UI Packets##delayUIPackets", SageFangConfig.isDelayUIPackets())) {
                SageFangConfig.setDelayUIPackets(!SageFangConfig.isDelayUIPackets());
            }

            if (ImGui.checkbox("Send UI Packets##sendUIPackets", SageFangConfig.isSendUIPackets())) {
                SageFangConfig.setSendUIPackets(!SageFangConfig.isSendUIPackets());
            }

            ImGui.separatorText("Interactions");

            if (ImGui.checkbox("Edit Signs##shouldEditSign", SageFangConfig.isShouldEditSign())) {
                SageFangConfig.setShouldEditSign(!SageFangConfig.isShouldEditSign());
            }

            if (ImGui.checkbox("Force Wake Up##shouldForceWakeUp", SageFangConfig.isShouldForceWakeUp())) {
                SageFangConfig.setShouldForceWakeUp(!SageFangConfig.isShouldForceWakeUp());
            }

            ImGui.separatorText("Debug");

            if (ImGui.checkbox("Log Sounds##shouldLogSounds", SageFangConfig.isShouldLogSounds())) {
                SageFangConfig.setShouldLogSounds(!SageFangConfig.isShouldLogSounds());
            }

            if (ImGui.checkbox("Show Slot Numbers##shouldShowSlotNumbers", SageFangConfig.isShouldShowSlotNumbers())) {
                SageFangConfig.setShouldShowSlotNumbers(!SageFangConfig.isShouldShowSlotNumbers());
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in MiscMenu", e);
        }
    }
}
