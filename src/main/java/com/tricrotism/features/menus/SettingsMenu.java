package com.tricrotism.features.menus;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.config.SageFangConfig;
import com.tricrotism.modules.packets.UIPacketDelay;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central settings panel. Modules are discovered from {@link Module#getRegistry()}
 * and grouped by {@link Module#category} automatically — no hardcoded references.
 * <p>
 * Toggle visibility with Right-Control.
 */
public class SettingsMenu implements Menu {

    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_RIGHT_CONTROL;
    private static final List<String> CATEGORY_ORDER = List.of(
        "Visual", "ESP", "Chat", "Combat", "Network", "World", "Utility", "Exploit", "Logger"
    );

    private boolean visible = true;
    private boolean keyWasDown;

    private Map<String, List<Module>> categoryCache;
    private int cachedModuleCount = -1;

    @Override
    public void frame(ImGuiIO io) {
        long window = Minecraft.getInstance().getWindow().handle();
        boolean down = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
        if (down && !keyWasDown) visible = !visible;
        keyWasDown = down;

        if (!visible) return;

        try {
            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Settings", flags);

            ImGui.beginChild("##settingsContent", 280, 500, false, ImGuiWindowFlags.None);

            renderMenusSection();
            renderDisplaySection();
            renderModulesSection();
            renderMiscSection();
            ImGui.endChild();

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in SettingsMenu", e);
        }
    }

    private void renderMenusSection() {
        ImGui.separatorText("Menus");

        if (ImGui.checkbox("Server Info##serverInfo", SageFangConfig.isServerInfoMenuEnabled())) {
            SageFangConfig.setServerInfoMenuEnabled(!SageFangConfig.isServerInfoMenuEnabled());
        }

        if (ImGui.checkbox("Player Info##playerInfo", SageFangConfig.isPlayerInfoMenuEnabled())) {
            SageFangConfig.setPlayerInfoMenuEnabled(!SageFangConfig.isPlayerInfoMenuEnabled());
        }

        boolean logVisible = io.avaje.config.Config.getBool("menu.log.visible", false);
        if (ImGui.checkbox("Log##sfLogMenu", logVisible)) {
            io.avaje.config.Config.setProperty("menu.log.visible", String.valueOf(!logVisible));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Module messages window (with a toggle for chat output)");
        }
    }

    private void renderDisplaySection() {
        ImGui.separatorText("Display");

        if (ImGui.checkbox("Merged Info##mergedInfo", SageFangConfig.isMergedInfoMenu())) {
            SageFangConfig.setMergedInfoMenu(!SageFangConfig.isMergedInfoMenu());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Combine server and player info into one window");
        }

        if (ImGui.checkbox("Show Graphs##showGraphs", SageFangConfig.isShowGraphs())) {
            SageFangConfig.setShowGraphs(!SageFangConfig.isShowGraphs());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Show rolling graphs for TPS, MSPT, memory, FPS");
        }

        if (SageFangConfig.isShowGraphs() && ImGui.treeNode("Graph Settings##graphSettings")) {
            if (ImGui.checkbox("FPS##graphFps", SageFangConfig.isGraphFps())) {
                SageFangConfig.setGraphFps(!SageFangConfig.isGraphFps());
            }
            if (ImGui.checkbox("Client Memory##graphClientMem", SageFangConfig.isGraphClientMemory())) {
                SageFangConfig.setGraphClientMemory(!SageFangConfig.isGraphClientMemory());
            }
            if (ImGui.checkbox("TPS##graphTps", SageFangConfig.isGraphTps())) {
                SageFangConfig.setGraphTps(!SageFangConfig.isGraphTps());
            }
            if (ImGui.checkbox("MSPT##graphMspt", SageFangConfig.isGraphMspt())) {
                SageFangConfig.setGraphMspt(!SageFangConfig.isGraphMspt());
            }
            if (ImGui.checkbox("Server Memory##graphServerMem", SageFangConfig.isGraphServerMemory())) {
                SageFangConfig.setGraphServerMemory(!SageFangConfig.isGraphServerMemory());
            }

            ImGui.spacing();

            int[] widthArr = {SageFangConfig.getGraphWidth()};
            if (ImGui.sliderInt("Width##graphWidth", widthArr, 100, 500)) {
                SageFangConfig.setGraphWidth(widthArr[0]);
            }

            int[] heightArr = {SageFangConfig.getGraphHeight()};
            if (ImGui.sliderInt("Height##graphHeight", heightArr, 40, 200)) {
                SageFangConfig.setGraphHeight(heightArr[0]);
            }

            int[] historyArr = {SageFangConfig.getGraphHistory()};
            if (ImGui.sliderInt("History##graphHistory", historyArr, 10, 3600)) {
                SageFangConfig.setGraphHistory(historyArr[0]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Number of data points to keep");
            }

            ImGui.treePop();
        }
    }

    private void renderMiscSection() {
        ImGui.separatorText("Misc");

        if (ImGui.checkbox("Bypass Server Pack##bypassResourcePack", SageFangConfig.isBypassResourcePack())) {
            SageFangConfig.setBypassResourcePack(!SageFangConfig.isBypassResourcePack());
        }

        if (ImGui.checkbox("Force Deny Pack##resourcePackForceDeny", SageFangConfig.isResourcePackForceDeny())) {
            SageFangConfig.setResourcePackForceDeny(!SageFangConfig.isResourcePackForceDeny());
        }

        if (ImGui.checkbox("Disable Pack Unobf##disablePackUnobf", SageFangConfig.isDisablePackUnobf())) {
            SageFangConfig.setDisablePackUnobf(!SageFangConfig.isDisablePackUnobf());
        }

        if (ImGui.checkbox("Delay UI Packets##delayUIPackets", SageFangConfig.isDelayUIPackets())) {
            SageFangConfig.setDelayUIPackets(!SageFangConfig.isDelayUIPackets());
        }

        int queued = UIPacketDelay.size();
        if (queued > 0) {
            if (ImGui.button("Send##delayUISend")) UIPacketDelay.release();
            ImGui.sameLine();
            if (ImGui.button("Clear##delayUIClear")) UIPacketDelay.clear();
            ImGui.sameLine();
            ImGui.text("Queued: " + queued);
        }

        if (ImGui.checkbox("Send UI Packets##sendUIPackets", SageFangConfig.isSendUIPackets())) {
            SageFangConfig.setSendUIPackets(!SageFangConfig.isSendUIPackets());
        }

        if (ImGui.checkbox("Edit Signs##shouldEditSign", SageFangConfig.isShouldEditSign())) {
            SageFangConfig.setShouldEditSign(!SageFangConfig.isShouldEditSign());
        }

        if (ImGui.checkbox("Force Wake Up##shouldForceWakeUp", SageFangConfig.isShouldForceWakeUp())) {
            SageFangConfig.setShouldForceWakeUp(!SageFangConfig.isShouldForceWakeUp());
        }

        if (ImGui.checkbox("Log Sounds##shouldLogSounds", SageFangConfig.isShouldLogSounds())) {
            SageFangConfig.setShouldLogSounds(!SageFangConfig.isShouldLogSounds());
        }

        if (ImGui.checkbox("Show Slot Numbers##shouldShowSlotNumbers", SageFangConfig.isShouldShowSlotNumbers())) {
            SageFangConfig.setShouldShowSlotNumbers(!SageFangConfig.isShouldShowSlotNumbers());
        }
    }

    private void renderModulesSection() {
        List<Module> registry = Module.getRegistry();
        if (categoryCache == null || registry.size() != cachedModuleCount) {
            Map<String, List<Module>> byCategory = new LinkedHashMap<>();
            for (String cat : CATEGORY_ORDER) byCategory.put(cat, new ArrayList<>());
            for (Module m : registry) {
                byCategory.computeIfAbsent(m.category, k -> new ArrayList<>()).add(m);
            }
            categoryCache = byCategory;
            cachedModuleCount = registry.size();
        }

        for (var entry : categoryCache.entrySet()) {
            List<Module> modules = entry.getValue();
            if (modules.isEmpty()) continue;

            ImGui.separatorText(entry.getKey());
            for (Module m : modules) {
                if (ImGui.checkbox(m.title + "##" + m.id, m.isVisible())) {
                    m.toggleVisible();
                }
                if (ImGui.isItemHovered() && !m.description.isEmpty()) {
                    ImGui.setTooltip(m.description);
                }
            }
        }
    }
}
