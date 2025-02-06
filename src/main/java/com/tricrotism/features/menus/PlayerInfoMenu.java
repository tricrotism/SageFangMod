package com.tricrotism.features.menus;

import com.tricrotism.Main;
import com.tricrotism.Menu;
import com.tricrotism.utils.NumberUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;

@Slf4j
public class PlayerInfoMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        try {
            int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Player Info Menu", flags);

            if (Minecraft.getInstance().player != null) {
                ImGui.text("FPS: " + Minecraft.getInstance().getFps());
                String xyzFormat = String.format("%.2f, %.2f, %.2f",round( Minecraft.getInstance().player.getX()), round(Minecraft.getInstance().player.getY()), round(Minecraft.getInstance().player.getZ()));
                ImGui.text("Position:");
                ImGui.sameLine();
                ImGui.text(xyzFormat);
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Click to copy");
                }
                if (ImGui.isItemClicked()) {
                    ImGui.setClipboardText(xyzFormat);
                    log.info("Copied position to clipboard: {}", xyzFormat);
                }
                String worldName = Minecraft.getInstance().player.level().dimension().location().toString().split(":")[1];
                ImGui.text("World: " + worldName);
                long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long memoryMax = Runtime.getRuntime().maxMemory();
                ImGui.text("Memory: " + NumberUtils.formatMemorySize(memoryUsed) + "/" + NumberUtils.formatMemorySize(memoryMax));
                ImGui.text("Packets: " + Math.round(Minecraft.getInstance().player.connection.getConnection().getAverageReceivedPackets()));
                if (!Minecraft.getInstance().isSingleplayer()) {
                    var remoteAddress = Minecraft.getInstance().player.connection.getConnection().getRemoteAddress().toString();
                    var serverData = Minecraft.getInstance().player.connection.getServerData();
                    if (serverData != null) {
                        var protocol = serverData.protocol;
                        var brand = Minecraft.getInstance().player.connection.serverBrand();

                        ImGui.text("Server IP: " + remoteAddress.split("/")[0]);
                        ImGui.text("Server Address: " + remoteAddress.split("/")[1]);
                        ImGui.text("Server Protocol: " + protocol);
                        ImGui.text("Server Brand: " + brand);

                    }
                }

                ImGui.button("Copy full location");
                if (ImGui.isItemClicked()) {
                    String fullPos = String.format("Dimension: %s, Position: %s", worldName, xyzFormat);
                    ImGui.setClipboardText(fullPos);
                    log.info("Copied full location to clipboard: {}", fullPos);
                }
            } else {
                ImGui.text("No player info");
            }

            ImGui.end();
        } catch (Exception e) {
            Main.LOGGER.error("Error in PlayerInfoMenu", e);
        }
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

}