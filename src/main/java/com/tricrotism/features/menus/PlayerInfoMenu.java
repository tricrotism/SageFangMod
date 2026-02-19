package com.tricrotism.features.menus;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.config.SageFangConfig;
import com.tricrotism.utils.GraphHistory;
import com.tricrotism.utils.NumberUtils;
import com.tricrotism.utils.GraphRenderer;
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
            if (!SageFangConfig.isPlayerInfoMenuEnabled() || SageFangConfig.isMergedInfoMenu()) {
                return;
            }

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Player Info", flags);

            if (Minecraft.getInstance().player != null) {
                ImGui.text("FPS");
                ImGui.sameLine();
                ImGui.text(String.valueOf(Minecraft.getInstance().getFps()));

                String xyzFormat = String.format("%.2f, %.2f, %.2f", Math.round(Minecraft.getInstance().player.getX() * 100D) / 100D, Math.round(Minecraft.getInstance().player.getY() * 100D) / 100D, Math.round(Minecraft.getInstance().player.getZ() * 100D) / 100D);
                ImGui.text("Position");
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
                ImGui.text("World");
                ImGui.sameLine();
                ImGui.text(worldName);

                long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long memoryMax = Runtime.getRuntime().maxMemory();
                ImGui.text("Memory");
                ImGui.sameLine();
                ImGui.text(NumberUtils.formatMemorySize(memoryUsed) + "/" + NumberUtils.formatMemorySize(memoryMax));

                ImGui.text("Packets/s");
                ImGui.sameLine();
                ImGui.text(String.valueOf(Math.round(Minecraft.getInstance().player.connection.getConnection().getAverageReceivedPackets())));

                if (!Minecraft.getInstance().isSingleplayer()) {
                    var remoteAddress = Minecraft.getInstance().player.connection.getConnection().getRemoteAddress().toString();
                    var serverData = Minecraft.getInstance().player.connection.getServerData();
                    if (serverData != null) {
                        var protocol = serverData.protocol;
                        var brand = Minecraft.getInstance().player.connection.serverBrand();

                        ImGui.text("IP");
                        ImGui.sameLine();
                        ImGui.text(remoteAddress.split("/")[0]);

                        ImGui.text("Address");
                        ImGui.sameLine();
                        ImGui.text(remoteAddress.split("/")[1]);

                        ImGui.text("Protocol");
                        ImGui.sameLine();
                        ImGui.text(String.valueOf(protocol));

                        ImGui.text("Brand");
                        ImGui.sameLine();
                        ImGui.text(String.valueOf(brand));
                    }
                }

                if (ImGui.button("Copy Location")) {
                    ImGui.openPopup("##copyLocPopup");
                }
                if (ImGui.beginPopup("##copyLocPopup")) {
                    var mc = Minecraft.getInstance();
                    double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
                    if (ImGui.selectable("X Y Z")) {
                        ImGui.setClipboardText(String.format("%.2f %.2f %.2f", x, y, z));
                    }
                    if (ImGui.selectable("X, Y, Z")) {
                        ImGui.setClipboardText(xyzFormat);
                    }
                    if (ImGui.selectable("X Y Z (Integers)")) {
                        ImGui.setClipboardText(String.format("%d %d %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
                    }
                    if (ImGui.selectable("/tp X Y Z")) {
                        ImGui.setClipboardText(String.format("/tp %.2f %.2f %.2f", x, y, z));
                    }
                    if (ImGui.selectable("Dimension: X, Y, Z")) {
                        ImGui.setClipboardText(String.format("Dimension: %s, Position: %s", worldName, xyzFormat));
                    }
                    if (ImGui.selectable("[Dimension] X Y Z")) {
                        ImGui.setClipboardText(String.format("[%s] %.2f %.2f %.2f", worldName, x, y, z));
                    }
                    if (ImGui.selectable("Bukkit Location")) {
                        ImGui.setClipboardText(String.format(
                                "new Location(Bukkit.getWorld(\"%s\"), %s, %s, %s, %sf, %sf)",
                                worldName, x, y, z, mc.player.getYRot(), mc.player.getXRot()));
                    }
                    ImGui.endPopup();
                }

                // Push client metrics for graphs
                GraphHistory.INSTANCE.ensureCapacity();
                GraphHistory.INSTANCE.pushClientMetrics(
                        Minecraft.getInstance().getFps(),
                        memoryUsed / (1024f * 1024f)
                );

                // Graphs section
                if (SageFangConfig.isShowGraphs() && GraphHistory.INSTANCE.fps.size() > 1) {
                    ImGui.separator();
                    if (ImGui.collapsingHeader("Graphs##clientGraphs")) {
                       if (SageFangConfig.isGraphFps()) GraphRenderer.plotLines("FPS", GraphHistory.INSTANCE.fps, 0f, Float.NaN);
                       if (SageFangConfig.isGraphClientMemory()) GraphRenderer.plotLines("Client Memory (MB)", GraphHistory.INSTANCE.clientMemory, 0f, Float.NaN);
                    }
                }
            } else {
                ImGui.text("Not in world");
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in PlayerInfoMenu", e);
        }
    }

}
