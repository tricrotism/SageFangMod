package com.tricrotism.modules.login;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.handshake.ClientIntent;

/**
 * Modifies the {@code ClientIntentionPacket} (handshake) sent when connecting to
 * a server: protocol version, address, port.get() and intended next state. The rewrite
 * happens in {@code LoginSpoofMixin} via {@code @ModifyVariable} on
 * {@code Connection.send}; this module only holds the toggles/values.
 * Enable it before connecting. Ported from the Meteor addon's Handshake Spoof.
 */
public final class HandshakeSpoofer extends Module {

    public static final HandshakeSpoofer instance = new HandshakeSpoofer();

    private final Settings.Bool modifyProtocol =
        bool("Protocol Version", "modifyProtocol", "Rewrite the handshake protocol number", false);
    private final Settings.Int protocolVersion =
        integer("Protocol", "protocolVersion", "Protocol number to send", 0, -1, 1024);
    private final Settings.Bool modifyAddress =
        bool("Address", "modifyAddress", "Rewrite the handshake server address", false);
    private final Settings.Bool modifyPort =
        bool("Port", "modifyPort", "Rewrite the handshake port", false);
    private final Settings.Int port =
        integer("Port", "port", "Port to send", 25565, 0, 65535);
    private final Settings.Bool modifyIntendedState =
        bool("Intended State", "modifyIntent", "Rewrite the handshake next-state", false);

    private static final ClientIntent[] INTENTS = {ClientIntent.STATUS, ClientIntent.LOGIN, ClientIntent.TRANSFER};

    private final Settings.Text address = text("Address", "address", "Handshake server address to send", "localhost", 128);
    private final Settings.Mode intent = mode("Intended State", "intent", "Handshake next-state", 1, "Status", "Login", "Transfer");

    private HandshakeSpoofer() {
        super("handshakespoof", "Handshake Spoof", "Modify the handshake packet sent on connect.", Category.NETWORK);
    }

    public boolean isModifyProtocol() {
        return modifyProtocol.get();
    }

    public int getProtocolVersion() {
        return protocolVersion.get();
    }

    public boolean isModifyAddress() {
        return modifyAddress.get();
    }

    public String getAddress() {
        return address.get();
    }

    public boolean isModifyPort() {
        return modifyPort.get();
    }

    public int getPort() {
        return port.get();
    }

    public boolean isModifyIntendedState() {
        return modifyIntendedState.get();
    }

    public ClientIntent getIntendedState() {
        return INTENTS[intent.get()];
    }

    private static int clientProtocol() {
        try {
            return SharedConstants.getProtocolVersion();
        } catch (Throwable t) {
            return 0;
        }
    }
}
