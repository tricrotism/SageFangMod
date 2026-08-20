package com.tricrotism.features.menus;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.utils.SFLog;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.minecraft.client.Minecraft;

/**
 * The SageFang log window: every module message routed through {@link SFLog} is
 * shown here, with a toggle controlling whether those messages are also echoed
 * into Minecraft chat. Visibility persists under {@code menu.log.visible}.
 */
public class LogMenu implements Menu {

    private static final String VISIBLE_KEY = "menu.log.visible";

    @Override
    public void frame(ImGuiIO io) {
        if (!Config.getBool(VISIBLE_KEY, false)) return;

        int flags = ImGuiWindowFlags.None;
        if (Minecraft.getInstance().gui.screen() == null) flags |= ImGuiWindowFlags.NoInputs;

        ImGui.setNextWindowBgAlpha(0.55f);
        ImGui.begin("SageFang Log", flags);

        boolean chat = SFLog.isChatOutput();
        if (ImGui.checkbox("Also print to chat##sfLogChat", chat)) {
            SFLog.setChatOutput(!chat);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Off: module messages appear only in this window.");
        }
        ImGui.sameLine();
        if (ImGui.button("Clear##sfLogClear")) SFLog.clear();

        ImGui.separator();
        ImGui.beginChild("##sfLogEntries", 560, 260, true);
        for (String line : SFLog.entries()) ImGui.textWrapped(line);
        if (ImGui.getScrollY() >= ImGui.getScrollMaxY() - 2f) ImGui.setScrollHereY(1f);
        ImGui.endChild();

        ImGui.end();
    }
}
