package com.tricrotism.modules.login;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;

import java.util.Base64;

/**
 * Overwrites the encrypted secret key and/or encrypted challenge (nonce) in the
 * {@code ServerboundKeyPacket} sent during login encryption. Applied by
 * {@code LoginSpoofMixin} via {@code ServerboundKeyPacketAccessor}. Values are
 * parsed as hex (default) or base64. Note: replacing these bytes will normally
 * break the login handshake. This is a research/debug tool. Ported from the
 * Meteor addon's login-key-spoof (fixed for 26.1 field names).
 */
public final class LoginKeySpoof extends Module {

    public static final LoginKeySpoof instance = new LoginKeySpoof();

    private final Settings.Text keyBytes = text("Secret Key", "key", "Encrypted key bytes (hex or base64)", "", 512);
    private final Settings.Text nonceBytes = text("Nonce", "nonce", "Encrypted challenge bytes (hex or base64)", "", 512);

    private final Settings.Bool useBase64 =
        bool("Base64 (else hex)", "base64", "Parse the byte fields as base64 instead of hex", false);
    private final Settings.Bool modifyKey =
        bool("Modify Secret Key", "modifyKey", "Overwrite the encrypted secret key", false);
    private final Settings.Bool modifyNonce =
        bool("Modify Nonce", "modifyNonce", "Overwrite the encrypted challenge", false);


    private LoginKeySpoof() {
        super("loginkeyspoof", "Login Key Spoof", "Overwrite the login encryption key/nonce bytes.", Category.NETWORK);
    }

    public boolean shouldModifyKey() {
        return modifyKey.get() && getKeyBytes() != null;
    }

    public byte[] getKeyBytes() {
        return parse(keyBytes.get());
    }

    public boolean shouldModifyNonce() {
        return modifyNonce.get() && getNonceBytes() != null;
    }

    public byte[] getNonceBytes() {
        return parse(nonceBytes.get());
    }

    private byte[] parse(String s) {
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            if (useBase64.get()) return Base64.getDecoder().decode(s);
            String hex = s.replaceAll("\\s+", "");
            if (hex.length() % 2 != 0) return null;
            byte[] out = new byte[hex.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##loginKeyEnabled", isActive())) toggle();
        useBase64.render();
        ImGui.separator();

        modifyKey.render();
        if (modifyKey.get()) {
            ImGui.setNextItemWidth(260);
            keyBytes.render();
            if (!keyBytes.get().trim().isEmpty() && getKeyBytes() == null) {
                ImGui.textColored(0.9f, 0.3f, 0.3f, 1.0f, "Unparseable bytes.");
            }
        }

        modifyNonce.render();
        if (modifyNonce.get()) {
            ImGui.setNextItemWidth(260);
            nonceBytes.render();
            if (!nonceBytes.get().trim().isEmpty() && getNonceBytes() == null) {
                ImGui.textColored(0.9f, 0.3f, 0.3f, 1.0f, "Unparseable bytes.");
            }
        }

        ImGui.end();
    }
}
