package com.tricrotism.modules.misc;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;

/**
 * Shows real account names above players' heads (and wherever a player's display
 * name is used) instead of nicked/team-decorated display names. The override
 * itself lives in {@code PlayerDisplayNameMixin}, which checks {@link #isActive()}.
 * Ported from the Meteor addon's real-player-names.
 */
public final class RealPlayerNames extends Module implements Menu {

    public static final RealPlayerNames instance = new RealPlayerNames();

    private RealPlayerNames() {
        super("realplayernames", "Real Player Names", "Show real account names instead of display names.", "Visual");
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##realPlayerNames", isActive())) toggle();
        ImGui.end();
    }
}
