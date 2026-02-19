package com.tricrotism.modules.packets;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.KeybindUtil;
import com.tricrotism.utils.MessageUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import io.avaje.config.Config;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Intercepts, logs, delays, and allows manipulation of all network packets.
 * <p>
 * Outbound packets can be delayed (all, or only selected types) and flushed/dropped on demand.
 * Inbound packets are logged and inspected (not delayed — would break game state).
 * <p>
 * On server join, a watchdog checks whether expected protocol packets arrived
 * within a grace period and alerts the player if any are missing.
 */
public class PacketManager extends Module implements Menu {

    public static final PacketManager instance = new PacketManager();

    // ── Outbound queue ──────────────────────────────────────────────

    private final Deque<QueuedPacket> outboundQueue = new ConcurrentLinkedDeque<>();

    @Getter
    private volatile boolean flushingOutbound;

    private boolean delayOutbound;
    private boolean logPackets;

    // ── Selective delay filter ───────────────────────────────────────

    /** When non-empty, only packets whose short name is in this set get delayed. */
    private final Set<String> delayFilter = new LinkedHashSet<>();

    // ── Packet log (rolling, both directions) ───────────────────────

    private final Deque<PacketLogEntry> packetLog = new ArrayDeque<>();
    private static final int MAX_LOG = 200;

    /** Distinct packet type names we've seen (for the selector UI). */
    private final Set<String> seenOutboundTypes = new TreeSet<>();
    private final Set<String> seenInboundTypes = new TreeSet<>();

    // ── Missing packet detection ────────────────────────────────────

    private static final Set<Class<?>> EXPECTED_PACKETS = Set.of(
            ClientboundLoginPacket.class,
            ClientboundPlayerAbilitiesPacket.class,
            ClientboundSetHealthPacket.class,
            ClientboundSetExperiencePacket.class,
            ClientboundGameEventPacket.class,
            ClientboundChangeDifficultyPacket.class,
            ClientboundPlayerPositionPacket.class,
            ClientboundSetChunkCacheCenterPacket.class
    );

    private final Set<String> receivedPacketTypes = Collections.synchronizedSet(new HashSet<>());
    private int joinTickCounter = -1;
    private static final int GRACE_TICKS = 100;

    // ── Keybind ─────────────────────────────────────────────────────

    private boolean keyWasDown;
    private boolean awaitingKeybind;

    // ── ImGui state ─────────────────────────────────────────────────

    private final ImString filterText = new ImString(128);
    private final ImString delayFilterInput = new ImString(128);

    // ── Constructor ─────────────────────────────────────────────────

    public PacketManager() {
        super("packetmanager", "Packet Manager", "Intercept, delay, and inspect all packets.");
        // Restore persisted delay filter
        String saved = Config.get(baseConfig + ".delayFilter", "");
        if (!saved.isBlank()) {
            for (String s : saved.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) delayFilter.add(trimmed);
            }
        }
    }

    // ── Keybind config ──────────────────────────────────────────────

    private int getKeybind() {
        return Config.getInt(baseConfig + ".keybind", GLFW.GLFW_KEY_UNKNOWN);
    }

    private void setKeybind(int key) {
        Config.setProperty(baseConfig + ".keybind", String.valueOf(key));
    }

    // ── Outbound interception (called from ConnectionMixin) ─────────

    public boolean captureOutbound(Packet<?> packet) {
        if (!isActive()) return false;

        String name = packetName(packet);
        logPacket("OUT", name);
        seenOutboundTypes.add(name);

        if (delayOutbound) {
            // If filter is non-empty, only delay matching packets
            if (!delayFilter.isEmpty() && !delayFilter.contains(name)) {
                return false;
            }
            outboundQueue.addLast(new QueuedPacket(packet, System.currentTimeMillis()));
            return true;
        }
        return false;
    }

    public void observeInbound(Packet<?> packet) {
        String name = packetName(packet);
        receivedPacketTypes.add(name);

        if (isActive()) {
            logPacket("IN", name);
            seenInboundTypes.add(name);
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    @Override
    public void onActivate() {
        delayOutbound = Config.getBool(baseConfig + ".delayOutbound", false);
        logPackets = Config.getBool(baseConfig + ".logPackets", true);
        SageFang.LOGGER.info("[PacketManager] Activated");
    }

    @Override
    public void onDeactivate() {
        flushOutbound();
        SageFang.LOGGER.info("[PacketManager] Deactivated — flushed outbound queue");
    }

    private void flushOutbound() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            outboundQueue.clear();
            return;
        }
        flushingOutbound = true;
        try {
            QueuedPacket qp;
            while ((qp = outboundQueue.pollFirst()) != null) {
                conn.send(qp.packet());
            }
        } finally {
            flushingOutbound = false;
        }
    }

    private void persistDelayFilter() {
        Config.setProperty(baseConfig + ".delayFilter", String.join(",", delayFilter));
    }

    // ── Events ──────────────────────────────────────────────────────

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        receivedPacketTypes.clear();
        // Only run missing packet detection on multiplayer — integrated server
        // bypasses the Netty pipeline for many packets, causing false positives.
        var mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && !mc.isLocalServer()) {
            joinTickCounter = 0;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Missing packet watchdog
        if (joinTickCounter >= 0) {
            joinTickCounter++;
            if (joinTickCounter >= GRACE_TICKS) {
                checkMissingPackets();
                joinTickCounter = -1;
            }
        }

        // Keybind toggle
        int key = getKeybind();
        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            boolean down = KeybindUtil.isKeyDown(key);
            if (down && !keyWasDown) {
                toggle();
            }
            keyWasDown = down;
        }
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        joinTickCounter = -1;
        outboundQueue.clear();
    }

    // ── Missing packet check ────────────────────────────────────────

    private void checkMissingPackets() {
        List<String> missing = new ArrayList<>();
        for (Class<?> expected : EXPECTED_PACKETS) {
            String name = expected.getSimpleName();
            if (name.startsWith("Clientbound")) name = name.substring(11);
            if (!receivedPacketTypes.contains(name)) {
                missing.add(name);
            }
        }

        var mc = Minecraft.getInstance();
        if (!missing.isEmpty()) {
            MessageUtils.sendMessage(mc, Component.literal("Missing packets detected!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

            for (String pkt : missing) {
                MessageUtils.sendMessage(mc, Component.literal("  \u2717 " + pkt)
                        .withStyle(ChatFormatting.YELLOW));
            }

            MessageUtils.sendMessage(mc, Component.literal(
                    "Server may be running non-standard software or is misconfigured.")
                    .withStyle(ChatFormatting.GRAY));

            SageFang.LOGGER.warn("[PacketManager] Missing expected packets: {}", missing);
        } else {
            SageFang.LOGGER.info("[PacketManager] All expected packets received");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void logPacket(String direction, String name) {
        if (!logPackets) return;
        synchronized (packetLog) {
            packetLog.addLast(new PacketLogEntry(direction, name, System.currentTimeMillis()));
            while (packetLog.size() > MAX_LOG) packetLog.pollFirst();
        }
    }

    public static String packetName(Packet<?> packet) {
        String full = packet.getClass().getSimpleName();
        if (full.startsWith("Clientbound")) return full.substring(11);
        if (full.startsWith("Serverbound")) return full.substring(11);
        return full;
    }

    // ── ImGui ───────────────────────────────────────────────────────

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!isVisible()) return;

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            ImGui.setNextWindowBgAlpha(0.55f);
            ImGui.begin("Packet Manager", flags);

            if (ImGui.checkbox("Enabled##pmEnabled", isActive())) { toggle(); }
            ImGui.separator();

            // ── Keybind ──
            int result = KeybindUtil.renderKeybindButton("Toggle", "pmKeybind", getKeybind(), awaitingKeybind);
            if (result == KeybindUtil.START_LISTENING) {
                awaitingKeybind = true;
            } else if (result == KeybindUtil.CLEAR_BIND) {
                setKeybind(GLFW.GLFW_KEY_UNKNOWN);
                awaitingKeybind = false;
            } else if (result != KeybindUtil.NO_CHANGE) {
                setKeybind(result);
                awaitingKeybind = false;
            }

            // ── Controls ──
            ImGui.separatorText("Controls");

            if (ImGui.checkbox("Delay Outbound##pmDelayOut", delayOutbound)) {
                delayOutbound = !delayOutbound;
                Config.setProperty(baseConfig + ".delayOutbound", String.valueOf(delayOutbound));
            }
            ImGui.sameLine();
            ImGui.text("(" + outboundQueue.size() + ")");

            if (ImGui.checkbox("Log Packets##pmLog", logPackets)) {
                logPackets = !logPackets;
                Config.setProperty(baseConfig + ".logPackets", String.valueOf(logPackets));
            }

            // ── Delay Filter ──
            ImGui.separatorText("Delay Filter");
            if (delayFilter.isEmpty()) {
                ImGui.textDisabled("All outbound packets delayed (no filter)");
            } else {
                ImGui.text("Only delaying " + delayFilter.size() + " packet type(s):");
            }

            // Current filter entries with remove buttons
            String toRemove = null;
            for (String entry : delayFilter) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.75f, 0.55f, 1.0f);
                ImGui.text("  " + entry);
                ImGui.popStyleColor();
                ImGui.sameLine();
                if (ImGui.smallButton("X##rmFilter_" + entry)) {
                    toRemove = entry;
                }
            }
            if (toRemove != null) {
                delayFilter.remove(toRemove);
                persistDelayFilter();
            }

            // Add from seen types — collapsible tree node
            if (ImGui.treeNode("Add Packet Type##pmAddFilter")) {
                ImGui.inputText("Search##pmDelaySearch", delayFilterInput);
                String search = delayFilterInput.get().trim().toLowerCase();

                ImGui.beginChild("##pmFilterPicker", 380, 150, true, ImGuiWindowFlags.None);
                for (String name : seenOutboundTypes) {
                    if (delayFilter.contains(name)) continue;
                    if (!search.isEmpty() && !name.toLowerCase().contains(search)) continue;

                    if (ImGui.selectable(name + "##addFilter_" + name)) {
                        delayFilter.add(name);
                        persistDelayFilter();
                    }
                }
                if (seenOutboundTypes.isEmpty()) {
                    ImGui.textDisabled("No outbound packets seen yet");
                }
                ImGui.endChild();

                ImGui.treePop();
            }

            if (!delayFilter.isEmpty() && ImGui.button("Clear Filter##pmClearFilter")) {
                delayFilter.clear();
                persistDelayFilter();
            }

            // ── Queue Actions ──
            ImGui.separatorText("Outbound Queue");

            if (ImGui.button("Flush##pmFlush")) {
                flushOutbound();
            }
            ImGui.sameLine();
            if (ImGui.button("Drop All##pmDrop")) {
                int count = outboundQueue.size();
                outboundQueue.clear();
                SageFang.LOGGER.info("[PacketManager] Dropped {} outbound packets", count);
            }

            // Queue preview
            if (!outboundQueue.isEmpty()) {
                ImGui.beginChild("##pmOutQ", 400, 100, true, ImGuiWindowFlags.None);
                int i = 0;
                for (QueuedPacket qp : outboundQueue) {
                    long age = (System.currentTimeMillis() - qp.timestamp()) / 1000;
                    ImGui.text(i + ": " + packetName(qp.packet()) + " (" + age + "s)");
                    i++;
                    if (i > 50) {
                        ImGui.text("... +" + (outboundQueue.size() - 50) + " more");
                        break;
                    }
                }
                ImGui.endChild();
            }

            // ── Packet Log ──
            ImGui.separatorText("Packet Log");

            ImGui.inputText("Filter##pmFilter", filterText);

            ImGui.beginChild("##pmLogChild", 400, 200, true, ImGuiWindowFlags.None);
            String filter = filterText.get().trim();
            synchronized (packetLog) {
                var it = packetLog.descendingIterator();
                while (it.hasNext()) {
                    PacketLogEntry entry = it.next();
                    if (!filter.isEmpty() && !entry.name().toLowerCase().contains(filter.toLowerCase())) {
                        continue;
                    }

                    boolean isOut = "OUT".equals(entry.direction());
                    if (isOut) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.55f, 0.55f, 1.0f);
                    } else {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.75f, 0.75f, 0.90f, 1.0f);
                    }
                    ImGui.text(entry.direction() + " " + entry.name());
                    ImGui.popStyleColor();

                    // Right-click on outbound entries to add to delay filter
                    if (isOut && ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
                        if (!delayFilter.contains(entry.name())) {
                            delayFilter.add(entry.name());
                            persistDelayFilter();
                        }
                    }
                    if (isOut && ImGui.isItemHovered()) {
                        ImGui.setTooltip("Right-click to add to delay filter");
                    }
                }
            }
            ImGui.endChild();

            if (ImGui.button("Clear Log##pmClear")) {
                synchronized (packetLog) {
                    packetLog.clear();
                }
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in PacketManager menu", e);
        }
    }

    // ── Records ─────────────────────────────────────────────────────

    public record QueuedPacket(Packet<?> packet, long timestamp) {}
    record PacketLogEntry(String direction, String name, long timestamp) {}
}
