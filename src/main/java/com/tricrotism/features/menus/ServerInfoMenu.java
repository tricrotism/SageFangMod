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
            if (Minecraft.getInstance().gui.screen() == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Server Info", flags);

            if (!mc.getCurrentServer().ip.contains("mchub")) {
                ImGui.textUnformatted("Not on MCHub");
                ImGui.end();
                return;
            }

            if (lastServerInfo == null) {
                ImGui.textUnformatted("No server info");
                ImGui.end();
                return;
            }

            ImGui.textUnformatted("Instance");
            ImGui.sameLine();
            ImGui.textUnformatted(lastServerInfo.instance());
            ImGui.textUnformatted("World");
            ImGui.sameLine();
            ImGui.textUnformatted(lastServerInfo.world());
            ImGui.textUnformatted("Uptime");
            ImGui.sameLine();
            ImGui.textUnformatted(TimeUtils.getTimeAmount(lastServerInfo.uptime(), true, false));
            ImGui.textUnformatted("Entities");
            ImGui.sameLine();
            ImGui.textUnformatted(String.valueOf(lastServerInfo.entityCount()));
            ImGui.textUnformatted("Chunks");
            ImGui.sameLine();
            ImGui.textUnformatted(String.valueOf(lastServerInfo.loadedChunks()));

            ImGui.textUnformatted("TPS");
            ImGui.sameLine();
            ImGuiUtil.textColored(MetricColors.tps(lastServerInfo.tps()), String.valueOf(Math.round(lastServerInfo.tps() * 100.0f) / 100.0f));

            ImGui.textUnformatted("MSPT");
            ImGui.sameLine();
            ImGuiUtil.textColored(MetricColors.mspt(lastServerInfo.mspt()), String.valueOf(Math.round(lastServerInfo.mspt() * 100.0f) / 100.0f));

            ImGui.textUnformatted("Memory");
            ImGui.sameLine();
            ImGui.textUnformatted(NumberUtils.formatMemorySize(lastServerInfo.memoryUsed()) + " / " + NumberUtils.formatMemorySize(lastServerInfo.memoryMax()));

            ImGui.textUnformatted("Java");
            ImGui.sameLine();
            ImGui.textUnformatted(lastServerInfo.javaVersion());
            ImGui.textUnformatted("Host");
            ImGui.sameLine();
            ImGui.textUnformatted(lastServerInfo.hostname());
            ImGui.textUnformatted("Players");
            ImGui.sameLine();
            ImGui.textUnformatted(NumberUtils.format(lastServerInfo.onlinePlayers()));
            ImGui.textUnformatted("Logins");
            ImGui.sameLine();
            ImGui.textUnformatted(NumberUtils.format(lastServerInfo.logins()));

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
