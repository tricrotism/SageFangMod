package com.tricrotism.modules.logger;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Logs custom plugin-message payloads in both directions, showing the channel id
 * and payload contents — useful for reverse-engineering server plugins. Captured
 * in {@code ConnectionMixin}; entries go to a bounded in-window log rather than
 * the console. Ported from the Meteor addon's payload-logger.
 */
public final class PayloadLogger extends Module implements Menu {

    public static final PayloadLogger instance = new PayloadLogger();

    private static final int MAX_ENTRIES = 200;

    private final Deque<String> entries = new ArrayDeque<>();
    private boolean logC2S;
    private boolean logS2C;

    private PayloadLogger() {
        super("payloadlogger", "Payload Logger", "Log custom plugin-message payloads both ways.", "Logger");
        logC2S = Config.getBool(baseConfig + ".c2s", true);
        logS2C = Config.getBool(baseConfig + ".s2c", true);
    }

    /**
     * Called from ConnectionMixin for outbound packets.
     */
    public void onOutbound(Packet<?> packet) {
        if (!isActive() || !logC2S) return;
        if (packet instanceof ServerboundCustomPayloadPacket p) add("C2S", p.payload());
    }

    /**
     * Called from ConnectionMixin for inbound packets.
     */
    public void onInbound(Packet<?> packet) {
        if (!isActive() || !logS2C) return;
        if (packet instanceof ClientboundCustomPayloadPacket p) add("S2C", p.payload());
    }

    private void add(String direction, CustomPacketPayload payload) {
        String content;
        try {
            content = String.valueOf(payload);
        } catch (Exception e) {
            content = "<unreadable: " + e.getMessage() + ">";
        }
        String line = direction + " | " + payload.type().id() + " | " + content;
        synchronized (entries) {
            entries.addLast(line);
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        }
    }

    @Override
    public void onDeactivate() {
        synchronized (entries) {
            entries.clear();
        }
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.None);
        if (ImGui.checkbox("Enabled##payloadLoggerEnabled", isActive())) toggle();
        ImGui.sameLine();
        if (ImGui.checkbox("C2S##plC2S", logC2S)) {
            logC2S = !logC2S;
            Config.setProperty(baseConfig + ".c2s", String.valueOf(logC2S));
        }
        ImGui.sameLine();
        if (ImGui.checkbox("S2C##plS2C", logS2C)) {
            logS2C = !logS2C;
            Config.setProperty(baseConfig + ".s2c", String.valueOf(logS2C));
        }
        ImGui.sameLine();
        if (ImGui.button("Clear##plClear")) {
            synchronized (entries) {
                entries.clear();
            }
        }

        ImGui.separator();
        ImGui.beginChild("##payloadLoggerLog", 560, 260, true);
        synchronized (entries) {
            for (String line : entries) ImGui.textWrapped(line);
        }
        if (ImGui.getScrollY() >= ImGui.getScrollMaxY() - 2f) ImGui.setScrollHereY(1f);
        ImGui.endChild();

        ImGui.end();
    }
}
