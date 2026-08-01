package com.tricrotism.modules.login;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import io.avaje.config.Config;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.handshake.ClientIntent;

/**
 * Modifies the {@code ClientIntentionPacket} (handshake) sent when connecting to
 * a server — protocol version, address, port and intended next state. The rewrite
 * happens in {@code LoginSpoofMixin} via {@code @ModifyVariable} on
 * {@code Connection.send}; this module only holds the toggles/values.
 * Enable it before connecting. Ported from the Meteor addon's Handshake Spoof.
 */
public final class HandshakeSpoofer extends Module implements Menu {

    public static final HandshakeSpoofer instance = new HandshakeSpoofer();

    private static final String[] INTENT_LABELS = {"Status", "Login", "Transfer"};
    private static final ClientIntent[] INTENTS = {ClientIntent.STATUS, ClientIntent.LOGIN, ClientIntent.TRANSFER};

    private boolean modifyProtocol;
    private int protocolVersion;
    private boolean modifyAddress;
    private final ImString address = new ImString(128);
    private boolean modifyPort;
    private int port;
    private boolean modifyIntendedState;
    private final ImInt intentIndex = new ImInt(1);

    private HandshakeSpoofer() {
        super("handshakespoof", "Handshake Spoof", "Modify the handshake packet sent on connect.", "Network");
        modifyProtocol = Config.getBool(baseConfig + ".modifyProtocol", false);
        protocolVersion = Config.getInt(baseConfig + ".protocolVersion", clientProtocol());
        modifyAddress = Config.getBool(baseConfig + ".modifyAddress", false);
        address.set(Config.get(baseConfig + ".address", "localhost"));
        modifyPort = Config.getBool(baseConfig + ".modifyPort", false);
        port = Config.getInt(baseConfig + ".port", 25565);
        modifyIntendedState = Config.getBool(baseConfig + ".modifyIntent", false);
        intentIndex.set(Config.getInt(baseConfig + ".intent", 1));
    }

    public boolean isModifyProtocol() {
        return modifyProtocol;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public boolean isModifyAddress() {
        return modifyAddress;
    }

    public String getAddress() {
        return address.get();
    }

    public boolean isModifyPort() {
        return modifyPort;
    }

    public int getPort() {
        return port;
    }

    public boolean isModifyIntendedState() {
        return modifyIntendedState;
    }

    public ClientIntent getIntendedState() {
        return INTENTS[intentIndex.get()];
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##handshakeEnabled", isActive())) toggle();
        ImGui.separator();

        if (ImGui.checkbox("Protocol Version##hsModProto", modifyProtocol)) {
            modifyProtocol = !modifyProtocol;
            Config.setProperty(baseConfig + ".modifyProtocol", String.valueOf(modifyProtocol));
        }
        if (modifyProtocol) {
            int[] v = {protocolVersion};
            ImGui.setNextItemWidth(200);
            if (ImGui.sliderInt("Protocol##hsProtoSlider", v, -1, 1024)) {
                protocolVersion = v[0];
                Config.setProperty(baseConfig + ".protocolVersion", String.valueOf(protocolVersion));
            }
        }

        if (ImGui.checkbox("Address##hsModAddr", modifyAddress)) {
            modifyAddress = !modifyAddress;
            Config.setProperty(baseConfig + ".modifyAddress", String.valueOf(modifyAddress));
        }
        if (modifyAddress) {
            ImGui.setNextItemWidth(200);
            if (ImGui.inputText("##hsAddr", address, ImGuiInputTextFlags.None)) {
                Config.setProperty(baseConfig + ".address", address.get());
            }
        }

        if (ImGui.checkbox("Port##hsModPort", modifyPort)) {
            modifyPort = !modifyPort;
            Config.setProperty(baseConfig + ".modifyPort", String.valueOf(modifyPort));
        }
        if (modifyPort) {
            int[] v = {port};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderInt("Port##hsPort", v, 0, 65535)) {
                port = v[0];
                Config.setProperty(baseConfig + ".port", String.valueOf(port));
            }
        }

        if (ImGui.checkbox("Intended State##hsModIntent", modifyIntendedState)) {
            modifyIntendedState = !modifyIntendedState;
            Config.setProperty(baseConfig + ".modifyIntent", String.valueOf(modifyIntendedState));
        }
        if (modifyIntendedState) {
            ImGui.setNextItemWidth(160);
            if (ImGui.combo("##hsIntent", intentIndex, INTENT_LABELS)) {
                Config.setProperty(baseConfig + ".intent", String.valueOf(intentIndex.get()));
            }
        }

        ImGui.end();
    }

    private static int clientProtocol() {
        try {
            return SharedConstants.getProtocolVersion();
        } catch (Throwable t) {
            return 0;
        }
    }
}
