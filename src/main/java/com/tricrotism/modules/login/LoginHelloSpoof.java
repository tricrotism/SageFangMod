package com.tricrotism.modules.login;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import imgui.ImGui;
import imgui.ImGuiIO;

import java.util.UUID;

/**
 * Modifies the {@code ServerboundHelloPacket} (login start): the name and/or
 * profile UUID sent when joining a server. The rewrite happens in
 * {@code LoginSpoofMixin}; this module only holds the values. Leave a field
 * blank to keep the original. Ported from the Meteor addon's login-hello-spoof.
 */
public final class LoginHelloSpoof extends Module {

    public static final LoginHelloSpoof instance = new LoginHelloSpoof();

    private final Settings.Text name = text("Name", "name", "Spoof name; blank keeps the original", "", 64);
    private final Settings.Text profileId = text("Profile UUID", "profileId", "Spoof UUID; blank keeps the original", "", 48);

    private LoginHelloSpoof() {
        super("loginhellospoof", "Login Hello Spoof", "Modify the name/UUID sent when joining.", Category.NETWORK);
    }

    /**
     * The spoof name, or null to keep the original.
     */
    public String getSpoofName() {
        String n = name.get().trim();
        return n.isEmpty() ? null : n;
    }

    /**
     * The spoof UUID, or null to keep the original (also null if unparseable).
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
    protected void renderExtra(ImGuiIO io) {
        if (!profileId.get().trim().isEmpty() && getSpoofProfileId() == null) {
            ImGui.textColored(0.9f, 0.3f, 0.3f, 1.0f, "Invalid UUID, original will be used.");
        }
    }
}
