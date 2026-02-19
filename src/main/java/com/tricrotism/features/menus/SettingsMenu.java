package com.tricrotism.features.menus;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.config.SageFangConfig;
import com.tricrotism.modules.crash.SkillCrash;
import com.tricrotism.modules.crash.OffhandCrash;
import com.tricrotism.modules.blink.Blink;
import com.tricrotism.modules.packets.PacketManager;
import com.tricrotism.modules.items.ItemViewer;
import com.tricrotism.modules.macros.ChatMacros;
import com.tricrotism.modules.ghost.GhostBlock;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

public class SettingsMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        try {
            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Settings", flags);

            ImGui.beginChild("##settingsContent", 280, 300, false, ImGuiWindowFlags.None);

            // --- Menus ---
            ImGui.separatorText("Menus");

            if (ImGui.checkbox("Server Info##serverInfo", SageFangConfig.isServerInfoMenuEnabled())) {
                SageFangConfig.setServerInfoMenuEnabled(!SageFangConfig.isServerInfoMenuEnabled());
            }

            if (ImGui.checkbox("Player Info##playerInfo", SageFangConfig.isPlayerInfoMenuEnabled())) {
                SageFangConfig.setPlayerInfoMenuEnabled(!SageFangConfig.isPlayerInfoMenuEnabled());
            }

            if (ImGui.checkbox("Misc##misc", SageFangConfig.isMiscMenuEnabled())) {
                SageFangConfig.setMiscMenuEnabled(!SageFangConfig.isMiscMenuEnabled());
            }

            // --- Display ---
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

                int[] widthArr = { SageFangConfig.getGraphWidth() };
                if (ImGui.sliderInt("Width##graphWidth", widthArr, 100, 500)) {
                    SageFangConfig.setGraphWidth(widthArr[0]);
                }

                int[] heightArr = { SageFangConfig.getGraphHeight() };
                if (ImGui.sliderInt("Height##graphHeight", heightArr, 40, 200)) {
                    SageFangConfig.setGraphHeight(heightArr[0]);
                }

                int[] historyArr = { SageFangConfig.getGraphHistory() };
                if (ImGui.sliderInt("History##graphHistory", historyArr, 10, 3600)) {
                    SageFangConfig.setGraphHistory(historyArr[0]);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Number of data points to keep");
                }

                ImGui.treePop();
            }

            // --- Modules ---
            ImGui.separatorText("Modules");

            if (ImGui.checkbox("Skill Crash##skillCrash", SkillCrash.instance.isVisible())) {
                SkillCrash.instance.toggleVisible();
            }

            if (ImGui.checkbox("Offhand Crash##offhandCrash", OffhandCrash.instance.isVisible())) {
                OffhandCrash.instance.toggleVisible();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Flood offhand swap packets; also has anti-crash mode");
            }

            if (ImGui.checkbox("Blink##blink", Blink.instance.isVisible())) {
                Blink.instance.toggleVisible();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Queue packets while active, replay on disable");
            }

            if (ImGui.checkbox("Packet Manager##packetMgr", PacketManager.instance.isVisible())) {
                PacketManager.instance.toggleVisible();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Intercept, log, and delay packets; detect missing packets on join");
            }

            if (ImGui.checkbox("Item Viewer##itemViewer", ItemViewer.instance.isVisible())) {
                ItemViewer.instance.toggleVisible();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("View and edit item data components on held item");
            }

            if (ImGui.checkbox("Chat Macros##chatMacros", ChatMacros.instance.isVisible())) {
                ChatMacros.instance.toggleVisible();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Save and run chat macros");
            }

            if (ImGui.checkbox("Ghost Blocks##ghostBlock", GhostBlock.instance.isVisible())) {
                GhostBlock.instance.toggleVisible();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Place and break blocks client-side only");
            }

            ImGui.endChild();

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in SettingsMenu", e);
        }
    }
}
