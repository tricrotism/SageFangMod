package com.tricrotism.modules.logger;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameQuitEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects every plugin-message channel the server uses and <em>interprets</em> what it sends on
 * them: {@code minecraft:register}/{@code unregister} are decoded into the NUL-separated channel
 * lists they carry, {@code minecraft:brand} into the server brand string, and anything else is
 * shown as a printable-ASCII rendering plus a hex dump of the raw body.
 * <p>
 * Channel names come from inbound payload ids in {@code ConnectionMixin}; the bodies come from
 * {@code DiscardedPayloadMixin}, which grabs them before vanilla discards them.
 */
public final class ChannelLogger extends Module implements Menu {

    public static final ChannelLogger instance = new ChannelLogger();

    private static final int MAX_PREVIEW_BYTES = 256;

    /**
     * Per-channel state: how many payloads seen and the interpretation of the latest one.
     */
    public record ChannelInfo(AtomicInteger count, String lastSummary, String lastAscii, String lastHex,
                              int lastSize) {}

    private final Map<String, ChannelInfo> channels = new ConcurrentHashMap<>();
    private String selected;
    private boolean showHex;

    private ChannelLogger() {
        super("channellogger", "Channel Logger", "Log plugin-message channels and decode their contents.", "Logger");
        showHex = Config.getBool(baseConfig + ".showHex", true);
    }

    /**
     * Called from ConnectionMixin — records that a channel exists even if we never see its body.
     */
    public void onInbound(Packet<?> packet) {
        if (!isActive()) return;
        if (packet instanceof ClientboundCustomPayloadPacket p) {
            channels.computeIfAbsent(p.payload().type().id().toString(),
                k -> new ChannelInfo(new AtomicInteger(), "(no body captured)", "", "", 0));
        }
    }

    /**
     * Called from DiscardedPayloadMixin on the network thread with the raw payload body.
     */
    public void onRawPayload(String channel, byte[] body) {
        if (!isActive()) return;
        ChannelInfo existing = channels.get(channel);
        int count = existing != null ? existing.count().get() + 1 : 1;

        AtomicInteger counter = new AtomicInteger(count);
        channels.put(channel, new ChannelInfo(counter, interpret(channel, body), toAscii(body), toHex(body), body.length));
    }

    /**
     * Decodes the well-known vanilla channels; falls back to a size note.
     */
    private static String interpret(String channel, byte[] body) {
        String id = channel.toLowerCase();
        if (id.endsWith("register") || id.endsWith("unregister")) {
            List<String> listed = new ArrayList<>();
            for (String raw : new String(body, StandardCharsets.UTF_8).split("\0")) {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) listed.add(trimmed);
            }
            String verb = id.endsWith("unregister") ? "Unregisters" : "Registers";
            return listed.isEmpty()
                ? verb + " nothing"
                : verb + " " + listed.size() + ": " + String.join(", ", listed);
        }
        if (id.endsWith("brand")) {
            // Brand is a length-prefixed string; skip the VarInt length byte(s) for display.
            String s = new String(body, StandardCharsets.UTF_8);
            return "Server brand: " + stripLeadingControl(s);
        }
        return body.length + " bytes";
    }

    private static String stripLeadingControl(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) < 0x20) i++;
        return s.substring(i);
    }

    private static String toAscii(byte[] body) {
        int len = Math.min(body.length, MAX_PREVIEW_BYTES);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int b = body[i] & 0xFF;
            sb.append(b >= 0x20 && b < 0x7F ? (char) b : '.');
        }
        if (body.length > len) sb.append('…');
        return sb.toString();
    }

    private static String toHex(byte[] body) {
        int len = Math.min(body.length, MAX_PREVIEW_BYTES);
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x ", body[i]));
            if ((i + 1) % 16 == 0) sb.append('\n');
        }
        if (body.length > len) sb.append("…");
        return sb.toString();
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        channels.clear();
        selected = null;
    }

    @Override
    public void onDeactivate() {
        channels.clear();
        selected = null;
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.None);

        if (ImGui.checkbox("Enabled##channelLoggerEnabled", isActive())) toggle();
        ImGui.sameLine();
        if (ImGui.checkbox("Hex##clHex", showHex)) {
            showHex = !showHex;
            Config.setProperty(baseConfig + ".showHex", String.valueOf(showHex));
        }
        ImGui.sameLine();
        if (ImGui.button("Clear##clClear")) {
            channels.clear();
            selected = null;
        }
        ImGui.text("Channels: " + channels.size());
        ImGui.separator();

        List<String> sorted = channels.keySet().stream().sorted().toList();
        ImGui.beginChild("##clList", 300, 220, true);
        for (String channel : sorted) {
            ChannelInfo info = channels.get(channel);
            if (info == null) continue;
            String label = channel + "  (" + info.count().get() + ")";
            if (ImGui.selectable(label + "##ch_" + channel, channel.equals(selected))) {
                selected = channel;
            }
        }
        ImGui.endChild();

        ImGui.sameLine();
        ImGui.beginChild("##clDetail", 420, 220, true);
        ChannelInfo info = selected != null ? channels.get(selected) : null;
        if (info == null) {
            ImGui.textDisabled("Select a channel.");
        } else {
            ImGui.textWrapped(selected);
            ImGui.separator();
            ImGui.textWrapped(info.lastSummary());
            if (info.lastSize() > 0) {
                ImGui.separator();
                ImGui.textDisabled("ASCII (" + info.lastSize() + " bytes)");
                ImGui.textWrapped(info.lastAscii());
                if (showHex) {
                    ImGui.separator();
                    ImGui.textDisabled("Hex");
                    ImGui.textWrapped(info.lastHex());
                }
            }
        }
        ImGui.endChild();

        ImGui.end();
    }
}
