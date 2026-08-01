package com.tricrotism.modules.login;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import io.avaje.config.Config;

import java.util.Base64;

/**
 * Overwrites the encrypted secret key and/or encrypted challenge (nonce) in the
 * {@code ServerboundKeyPacket} sent during login encryption. Applied by
 * {@code LoginSpoofMixin} via {@code ServerboundKeyPacketAccessor}. Values are
 * parsed as hex (default) or base64. Note: replacing these bytes will normally
 * break the login handshake — this is a research/debug tool. Ported from the
 * Meteor addon's login-key-spoof (fixed for 26.1 field names).
 */
public final class LoginKeySpoof extends Module implements Menu {

    public static final LoginKeySpoof instance = new LoginKeySpoof();

    private boolean modifyKey;
    private final ImString keyBytes = new ImString(512);
    private boolean modifyNonce;
    private final ImString nonceBytes = new ImString(512);
    private boolean useBase64;

    private LoginKeySpoof() {
        super("loginkeyspoof", "Login Key Spoof", "Overwrite the login encryption key/nonce bytes.", "Network");
        modifyKey = Config.getBool(baseConfig + ".modifyKey", false);
        keyBytes.set(Config.get(baseConfig + ".key", ""));
        modifyNonce = Config.getBool(baseConfig + ".modifyNonce", false);
        nonceBytes.set(Config.get(baseConfig + ".nonce", ""));
        useBase64 = Config.getBool(baseConfig + ".base64", false);
    }

    public boolean shouldModifyKey() {
        return modifyKey && getKeyBytes() != null;
    }

    public byte[] getKeyBytes() {
        return parse(keyBytes.get());
    }

    public boolean shouldModifyNonce() {
        return modifyNonce && getNonceBytes() != null;
    }

    public byte[] getNonceBytes() {
        return parse(nonceBytes.get());
    }

    private byte[] parse(String s) {
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            if (useBase64) return Base64.getDecoder().decode(s);
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
        if (ImGui.checkbox("Base64 (else hex)##loginKeyB64", useBase64)) {
            useBase64 = !useBase64;
            Config.setProperty(baseConfig + ".base64", String.valueOf(useBase64));
        }
        ImGui.separator();

        if (ImGui.checkbox("Modify Secret Key##loginKeyModKey", modifyKey)) {
            modifyKey = !modifyKey;
            Config.setProperty(baseConfig + ".modifyKey", String.valueOf(modifyKey));
        }
        if (modifyKey) {
            ImGui.setNextItemWidth(260);
            if (ImGui.inputText("##loginKeyKey", keyBytes)) Config.setProperty(baseConfig + ".key", keyBytes.get());
            if (!keyBytes.get().trim().isEmpty() && getKeyBytes() == null) {
                ImGui.textColored(0.9f, 0.3f, 0.3f, 1.0f, "Unparseable bytes.");
            }
        }

        if (ImGui.checkbox("Modify Nonce/Challenge##loginKeyModNonce", modifyNonce)) {
            modifyNonce = !modifyNonce;
            Config.setProperty(baseConfig + ".modifyNonce", String.valueOf(modifyNonce));
        }
        if (modifyNonce) {
            ImGui.setNextItemWidth(260);
            if (ImGui.inputText("##loginKeyNonce", nonceBytes))
                Config.setProperty(baseConfig + ".nonce", nonceBytes.get());
            if (!nonceBytes.get().trim().isEmpty() && getNonceBytes() == null) {
                ImGui.textColored(0.9f, 0.3f, 0.3f, 1.0f, "Unparseable bytes.");
            }
        }

        ImGui.end();
    }
}
