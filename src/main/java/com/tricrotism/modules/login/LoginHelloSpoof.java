package com.tricrotism.modules.login;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import io.avaje.config.Config;

import java.util.UUID;

/**
 * Modifies the {@code ServerboundHelloPacket} (login start) — the name and/or
 * profile UUID sent when joining a server. The rewrite happens in
 * {@code LoginSpoofMixin}; this module only holds the values. Leave a field
 * blank to keep the original. Ported from the Meteor addon's login-hello-spoof.
 */
public final class LoginHelloSpoof extends Module implements Menu {

    public static final LoginHelloSpoof instance = new LoginHelloSpoof();

    private final ImString name = new ImString(64);
    private final ImString profileId = new ImString(48);

    private LoginHelloSpoof() {
        super("loginhellospoof", "Login Hello Spoof", "Modify the name/UUID sent when joining.", "Network");
        name.set(Config.get(baseConfig + ".name", ""));
        profileId.set(Config.get(baseConfig + ".profileId", ""));
    }

    /**
     * @return the spoof name, or null to keep the original.
     */
    public String getSpoofName() {
        String n = name.get().trim();
        return n.isEmpty() ? null : n;
    }

    /**
     * @return the spoof UUID, or null to keep the original (also null if unparseable).
     */
    public UUID getSpoofProfileId() {
        String id = profileId.get().trim();
        if (id.isEmpty()) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##loginHelloEnabled", isActive())) toggle();
        ImGui.separator();

        ImGui.text("Name (blank = original)");
        ImGui.setNextItemWidth(220);
        if (ImGui.inputText("##loginHelloName", name)) {
            Config.setProperty(baseConfig + ".name", name.get());
        }

        ImGui.text("Profile UUID (blank = original)");
        ImGui.setNextItemWidth(220);
        if (ImGui.inputText("##loginHelloUuid", profileId)) {
            Config.setProperty(baseConfig + ".profileId", profileId.get());
        }
        if (!profileId.get().trim().isEmpty() && getSpoofProfileId() == null) {
            ImGui.textColored(0.9f, 0.3f, 0.3f, 1.0f, "Invalid UUID — original will be used.");
        }

        ImGui.end();
    }
}
