package com.tricrotism.modules.logger;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Logs custom plugin-message payloads in both directions, showing the channel id
 * and payload contents, which is what you need to reverse-engineer server plugins. Captured
 * in {@code ConnectionMixin}; entries go to a bounded in-window log rather than
 * the console. Ported from the Meteor addon's payload-logger.
 */
public final class PayloadLogger extends Module {

    public static final PayloadLogger instance = new PayloadLogger();

    private final Settings.Bool logC2S =
        bool("C2S##plC2S", "c2s", "Log client-to-server payloads", true);
    private final Settings.Bool logS2C =
        bool("S2C##plS2C", "s2c", "Log server-to-client payloads", true);

    private static final int MAX_ENTRIES = 200;

    private final Deque<String> entries = new ArrayDeque<>();

    private PayloadLogger() {
        super("payloadlogger", "Payload Logger", "Log custom plugin-message payloads both ways.", Category.LOGGING);
    }

    /**
     * Called from ConnectionMixin for outbound packets.
     */
    public void onOutbound(Packet<?> packet) {
        if (!isActive() || !logC2S.get()) return;
        if (packet instanceof ServerboundCustomPayloadPacket(CustomPacketPayload payload)) add("C2S", payload);
    }

    /**
     * Called from ConnectionMixin for inbound packets.
     */
    public void onInbound(Packet<?> packet) {
        if (!isActive() || !logS2C.get()) return;
        if (packet instanceof ClientboundCustomPayloadPacket(CustomPacketPayload payload)) add("S2C", payload);
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
        if (ImGui.checkbox("C2S##plC2S", logC2S.get())) logC2S.set(!logC2S.get());
        ImGui.sameLine();
        if (ImGui.checkbox("S2C##plS2C", logS2C.get())) logS2C.set(!logS2C.get());
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
