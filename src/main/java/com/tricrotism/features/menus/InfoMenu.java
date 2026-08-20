package com.tricrotism.features.menus;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.config.SageFangConfig;
import com.tricrotism.utils.*;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;

import static com.tricrotism.SageFang.lastServerInfo;

@Slf4j
public class InfoMenu implements Menu {

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!SageFangConfig.isMergedInfoMenu()) {
                return;
            }

            if (!SageFangConfig.isPlayerInfoMenuEnabled() && !SageFangConfig.isServerInfoMenuEnabled()) {
                return;
            }

            var mc = Minecraft.getInstance();

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            if (mc.gui.screen() == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Info", flags);
            GraphHistory.INSTANCE.ensureCapacity();

            boolean playerShown = false;
            boolean serverShown = false;

            // Player section
            if (SageFangConfig.isPlayerInfoMenuEnabled() && mc.player != null) {
                playerShown = true;
                ImGui.separatorText("Player");

                ImGui.text("FPS: " + mc.getFps());

                String xyzFormat = String.format("%.2f, %.2f, %.2f",
                    Math.round(mc.player.getX() * 100D) / 100D,
                    Math.round(mc.player.getY() * 100D) / 100D,
                    Math.round(mc.player.getZ() * 100D) / 100D);
                ImGui.text("Position: ");
                ImGui.sameLine();
                ImGui.text(xyzFormat);
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Click to copy");
                }
                if (ImGui.isItemClicked()) {
                    ImGui.setClipboardText(xyzFormat);
                    log.info("Copied position to clipboard: {}", xyzFormat);
                }

                String worldName = mc.player.level().dimension().identifier().toString().split(":")[1];
                ImGui.text("World: " + worldName);

                long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long memoryMax = Runtime.getRuntime().maxMemory();
                ImGui.text("Memory: " + NumberUtils.formatMemorySize(memoryUsed) + "/" + NumberUtils.formatMemorySize(memoryMax));

                ImGui.text("Packets/s: " + Math.round(mc.player.connection.getConnection().getAverageReceivedPackets()));

                if (!mc.hasSingleplayerServer()) {
                    var remoteAddress = mc.player.connection.getConnection().getRemoteAddress().toString();
                    var serverData = mc.player.connection.getServerData();
                    if (serverData != null) {
                        ImGui.text("IP: " + remoteAddress.split("/")[0]);
                        ImGui.text("Address: " + remoteAddress.split("/")[1]);
                        ImGui.text("Protocol: " + serverData.protocol);
                        ImGui.text("Brand: " + mc.player.connection.serverBrand());
                    }
                }

                if (ImGui.button("Copy Location")) {
                    ImGui.openPopup("##copyLocPopup");
                }
                if (ImGui.beginPopup("##copyLocPopup")) {
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

                // Push client metrics
                float memMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024f * 1024f);
                GraphHistory.INSTANCE.pushClientMetrics(mc.getFps(), memMb);
            }

            // Server section
            if (SageFangConfig.isServerInfoMenuEnabled() && !mc.hasSingleplayerServer() && mc.getCurrentServer() != null) {
                serverShown = true;
                ImGui.separatorText("Server");

                if (!mc.getCurrentServer().ip.contains("mchub")) {
                    ImGui.text("Not on MCHub");
                } else if (lastServerInfo == null) {
                    ImGui.text("No server info");
                } else {
                    ImGui.text("Instance: " + lastServerInfo.instance());
                    ImGui.text("World: " + lastServerInfo.world());
                    ImGui.text("Uptime: " + TimeUtils.getTimeAmount(lastServerInfo.uptime(), true, false));
                    ImGui.text("Entities: " + lastServerInfo.entityCount());
                    ImGui.text("Chunks: " + lastServerInfo.loadedChunks());
                    ImGui.textColored(MetricColors.tps(lastServerInfo.tps()),
                        "TPS: " + Math.round(lastServerInfo.tps() * 100.0f) / 100.0f);
                    ImGui.textColored(MetricColors.mspt(lastServerInfo.mspt()),
                        "MSPT: " + Math.round(lastServerInfo.mspt() * 100.0f) / 100.0f);
                    ImGui.text("Memory: " + NumberUtils.formatMemorySize(lastServerInfo.memoryUsed())
                        + "/" + NumberUtils.formatMemorySize(lastServerInfo.memoryMax()));
                    ImGui.text("Java: " + lastServerInfo.javaVersion());
                    ImGui.text("Host: " + lastServerInfo.hostname());
                    ImGui.text("Players: " + NumberUtils.format(lastServerInfo.onlinePlayers()));
                    ImGui.text("Logins: " + NumberUtils.format(lastServerInfo.logins()));

                    // Push server metrics
                    float serverMemMb = lastServerInfo.memoryUsed() / (1024f * 1024f);
                    GraphHistory.INSTANCE.pushServerMetrics(
                        lastServerInfo.tps(), lastServerInfo.mspt(), serverMemMb);
                }
            }

            // Graphs section
            if (SageFangConfig.isShowGraphs() && (playerShown || serverShown)) {
                var gh = GraphHistory.INSTANCE;
                boolean hasData = gh.fps.size() > 0 || gh.tps.size() > 0;

                if (hasData) {
                    ImGui.separator();
                    if (ImGui.collapsingHeader("Graphs")) {
                        if (playerShown) {
                            if (SageFangConfig.isGraphFps()) GraphRenderer.plotImPlot("FPS", gh.fps, 0, 300);
                            if (SageFangConfig.isGraphClientMemory())
                                GraphRenderer.plotImPlot("Client Memory (MB)", gh.clientMemory, 0, Runtime.getRuntime().maxMemory() / (1024f * 1024f));
                        }
                        if (serverShown && lastServerInfo != null) {
                            if (SageFangConfig.isGraphTps()) GraphRenderer.plotImPlot("TPS", gh.tps, 0, 20);
                            if (SageFangConfig.isGraphMspt()) GraphRenderer.plotImPlot("MSPT", gh.mspt, 0, 100);
                            if (SageFangConfig.isGraphServerMemory())
                                GraphRenderer.plotImPlot("Server Memory (MB)", gh.serverMemory, 0,
                                    lastServerInfo.memoryMax() / (1024f * 1024f));
                        }
                    }
                }
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in InfoMenu", e);
        }
    }
}
