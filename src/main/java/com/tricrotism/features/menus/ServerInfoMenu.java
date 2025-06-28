package com.tricrotism.features.menus;

import com.tricrotism.Main;
import com.tricrotism.Menu;
import com.tricrotism.utils.NumberUtils;
import com.tricrotism.utils.TimeUtils;
import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;

import static com.tricrotism.Main.lastServerInfo;

public class ServerInfoMenu implements Menu {
    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!Main.getConfig().serverInfoMenu) {
                return;
            }

            var mc = Minecraft.getInstance();
            if (mc.isLocalServer() || mc.getCurrentServer() == null) {
                return;
            }

            int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize;
            if (Minecraft.getInstance().screen == null) {
                flags |= ImGuiWindowFlags.NoInputs;
            }

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin("Server Info Menu", flags);

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

            ImGui.text("Instance: " + lastServerInfo.getInstance());
            ImGui.text("World: " + lastServerInfo.getWorld());
            ImGui.text("Uptime: " + TimeUtils.getTimeAmount(lastServerInfo.getUptime(), true, false));
            ImGui.text("Entity count: " + lastServerInfo.getEntityCount());
            ImGui.text("Loaded chunks: " + lastServerInfo.getLoadedChunks());
            ImGui.textColored(formatTPSColors(lastServerInfo.getTps()), "TPS: " + round(lastServerInfo.getTps()));
            ImGui.textColored(formatMSTPColors(lastServerInfo.getMspt()), "MSPT: " + round(lastServerInfo.getMspt()));
            ImGui.text("Memory: " + NumberUtils.formatMemorySize(lastServerInfo.getMemoryUsed()) + "/" + NumberUtils.formatMemorySize(lastServerInfo.getMemoryMax()));
            ImGui.text("Java Version: " + lastServerInfo.getJavaVersion());
            ImGui.text("Hostname: " + lastServerInfo.getHostname());
            ImGui.text("Online Players: " + NumberUtils.format(lastServerInfo.getOnlinePlayers()));
            ImGui.text("Logins: " + NumberUtils.format(lastServerInfo.getLogins()));
            ImGui.end();
        } catch (Exception e) {
            Main.LOGGER.error("Error in ServerInfoMenu", e);
        }
    }

    public static int formatTPSColors(float tps) {
        if (tps >= 18.0) {
            return ImColor.rgb("#00FF00");
        } else if (tps >= 15.0) {
            return ImColor.rgb("#FFFF00");
        } else {
            return ImColor.rgb("#FF0000");
        }
    }

    public static int formatMSTPColors(float mstp) {
        if (mstp <= 35.0) {
            return ImColor.rgb("#00FF00");
        } else if (mstp <= 65.0) {
            return ImColor.rgb("#FFFF00");
        } else {
            return ImColor.rgb("#FF0000");
        }
    }

    private float round(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }
}