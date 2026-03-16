package com.tricrotism.features.menus;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.config.SageFangConfig;
import com.tricrotism.utils.*;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

import static com.tricrotism.SageFang.lastServerInfo;

public class ServerInfoMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!SageFangConfig.isServerInfoMenuEnabled() || SageFangConfig.isMergedInfoMenu()) {
                return;
            }

            var mc = Minecraft.getInstance();
            if (mc.isLocalServer() || mc.getCurrentServer() == null) {
                return;
            }

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Server Info", flags);

            if (!mc.getCurrentServer().ip.contains("mchub")) {
                ImGui.text("Not on MCHub");
                ImGui.end();
                return;
            }

            if (lastServerInfo == null) {
                ImGui.text("No server info");
                ImGui.end();
                return;
            }

            ImGui.text("Instance");
            ImGui.sameLine();
            ImGui.text(lastServerInfo.instance());
            ImGui.text("World");
            ImGui.sameLine();
            ImGui.text(lastServerInfo.world());
            ImGui.text("Uptime");
            ImGui.sameLine();
            ImGui.text(TimeUtils.getTimeAmount(lastServerInfo.uptime(), true, false));
            ImGui.text("Entities");
            ImGui.sameLine();
            ImGui.text(String.valueOf(lastServerInfo.entityCount()));
            ImGui.text("Chunks");
            ImGui.sameLine();
            ImGui.text(String.valueOf(lastServerInfo.loadedChunks()));

            ImGui.text("TPS");
            ImGui.sameLine();
            ImGui.textColored(MetricColors.tps(lastServerInfo.tps()), String.valueOf(Math.round(lastServerInfo.tps() * 100.0f) / 100.0f));

            ImGui.text("MSPT");
            ImGui.sameLine();
            ImGui.textColored(MetricColors.mspt(lastServerInfo.mspt()), String.valueOf(Math.round(lastServerInfo.mspt() * 100.0f) / 100.0f));

            ImGui.text("Memory");
            ImGui.sameLine();
            ImGui.text(NumberUtils.formatMemorySize(lastServerInfo.memoryUsed()) + " / " + NumberUtils.formatMemorySize(lastServerInfo.memoryMax()));

            ImGui.text("Java");
            ImGui.sameLine();
            ImGui.text(lastServerInfo.javaVersion());
            ImGui.text("Host");
            ImGui.sameLine();
            ImGui.text(lastServerInfo.hostname());
            ImGui.text("Players");
            ImGui.sameLine();
            ImGui.text(NumberUtils.format(lastServerInfo.onlinePlayers()));
            ImGui.text("Logins");
            ImGui.sameLine();
            ImGui.text(NumberUtils.format(lastServerInfo.logins()));

            GraphHistory.INSTANCE.ensureCapacity();
            GraphHistory.INSTANCE.pushServerMetrics(
                lastServerInfo.tps(),
                lastServerInfo.mspt(),
                lastServerInfo.memoryUsed() / (1024f * 1024f)
            );

            if (SageFangConfig.isShowGraphs() && GraphHistory.INSTANCE.tps.size() > 1) {
                ImGui.separator();
                if (ImGui.collapsingHeader("Graphs##serverGraphs")) {
                    if (SageFangConfig.isGraphTps()) GraphRenderer.plotLines("TPS", GraphHistory.INSTANCE.tps, 0f, 22f);
                    if (SageFangConfig.isGraphMspt())
                        GraphRenderer.plotLines("MSPT", GraphHistory.INSTANCE.mspt, 0f, 100f);
                    if (SageFangConfig.isGraphServerMemory())
                        GraphRenderer.plotLines("Server Memory (MB)", GraphHistory.INSTANCE.serverMemory, 0f, Float.NaN);
                }
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in ServerInfoMenu", e);
        }
    }
}
