package com.tricrotism.modules.misc;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;

/**
 * Allows typing/pasting any character — including control characters like NUL —
 * in chat, signs, anvils and other text fields, by bypassing the vanilla chat
 * character filter. The bypass itself lives in {@code ChatTextLimitsMixin}, which
 * checks {@link #isActive()}. Ported from the Meteor addon's allow-invalid-chars.
 */
public final class AllowInvalidChars extends Module implements Menu {

    public static final AllowInvalidChars instance = new AllowInvalidChars();

    private AllowInvalidChars() {
        super("allowinvalidchars", "Allow Invalid Chars",
            "Allow any character (incl. control chars) in text fields.", "Utility");
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##allowInvalidChars", isActive())) toggle();
        ImGui.end();
    }
}
