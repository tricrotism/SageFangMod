package com.tricrotism.modules.logger;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.MessageUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catches a server fingerprinting our installed mods, and records what it asked for.
 * <p>
 * The vector is MC-265322. A component may carry a translation key or a keybind name instead of
 * literal text, and the client resolves those against its own language table before displaying
 * them. Sign editing is the place where a resolved string travels back: the server writes a sign
 * whose lines are {@code translate} or {@code keybind} placeholders, opens the editor on it, and
 * reads whatever the client sends in the sign update. A vanilla client has no entry for
 * {@code sodium.option_impact.low} and echoes the key; a client that ships that mod's language file
 * answers with the mod's own wording. The difference names the mod. Anvil renames carry a resolved
 * string back the same way.
 * <p>
 * The useful half is not that we were probed but <em>what for</em>. The key list is the server's
 * blacklist, written down, so this keeps every distinct key seen for the session and groups them by
 * the namespace in front of the first dot, which is the mod id often enough to read at a glance.
 * <p>
 * This module reports and does not interfere. Answering the probe as a vanilla client would is a
 * stealth feature, and this client is instrumented rather than stealthy, so that belongs behind its
 * own module and its own decision.
 */
public final class ProbeDetect extends Module {

    public static final ProbeDetect instance = new ProbeDetect();

    private static final int MAX_HITS = 100;
    private static final long PLANT_TTL_MS = 60_000L;
    private static final long NOTIFY_COOLDOWN_MS = 2000L;

    private static final Pattern PLACEHOLDER =
        Pattern.compile("(translate|keybind)\\s*:\\s*[\"']([^\"']{1,128})[\"']");

    private final Settings.Bool notify =
        bool("Chat notify", "notify", "Announce probes in chat as well as recording them.", true);

    private final Queue<Report> reports = new ConcurrentLinkedQueue<>();
    private final Map<Long, Plant> planted = new ConcurrentHashMap<>();

    private final Deque<Hit> hits = new ArrayDeque<>();
    private final Set<String> keys = new LinkedHashSet<>();
    private long lastNotify;

    private ProbeDetect() {
        super("probedetect", "Probe Detect", "Catch a server fingerprinting our installed mods.", Category.LOGGING);
    }

    private record Plant(long at, List<String> keys) {}

    private record Report(long at, boolean answered, int x, int y, int z, List<String> keys) {}

    private record Hit(long at, String detail) {}

    /**
     * Called from ConnectionMixin on the network thread.
     */
    public void onInbound(Packet<?> packet) {
        if (!isActive()) return;

        switch (packet) {
            case ClientboundBlockEntityDataPacket data -> onBlockEntity(data);
            case ClientboundOpenSignEditorPacket editor -> onEditor(editor);
            default -> {
            }
        }
    }

    /**
     * The plant. Sign text carrying a placeholder is worth recording on its own, since a server with
     * an honest use for one on a sign is rare and a keybind on a sign has none at all.
     */
    private void onBlockEntity(ClientboundBlockEntityDataPacket packet) {
        if (packet.getType() != BlockEntityTypes.SIGN && packet.getType() != BlockEntityTypes.HANGING_SIGN) return;

        CompoundTag tag = packet.getTag();
        if (tag == null) return;

        List<String> found = placeholders(tag.toString());
        if (found.isEmpty()) return;

        BlockPos pos = packet.getPos();

        planted.put(pos.asLong(), new Plant(System.currentTimeMillis(), found));
        reports.add(new Report(System.currentTimeMillis(), false, pos.getX(), pos.getY(), pos.getZ(), found));
    }

    /**
     * The read. Opening the editor on a planted sign is what turns the placeholder into an answer,
     * so this is the escalation from "a sign we should not trust" to "we are being measured".
     */
    private void onEditor(ClientboundOpenSignEditorPacket packet) {
        BlockPos pos = packet.getPos();
        Plant plant = planted.get(pos.asLong());
        if (plant == null) return;

        reports.add(new Report(System.currentTimeMillis(), true, pos.getX(), pos.getY(), pos.getZ(), plant.keys()));
    }

    private static List<String> placeholders(String snbt) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(snbt);

        while (matcher.find()) {
            String key = matcher.group(2);
            if (!found.contains(key)) found.add(key);
        }

        return found;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) {
            reports.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Report report;

        while ((report = reports.poll()) != null) {
            keys.addAll(report.keys());
            record(report);
        }

        planted.values().removeIf(plant -> now - plant.at() > PLANT_TTL_MS);
    }

    private void record(Report report) {
        String detail = (report.answered() ? "editor opened on a planted sign at " : "placeholder sign at ")
            + report.x() + " " + report.y() + " " + report.z()
            + ", " + report.keys().size() + " key(s): " + String.join(", ", report.keys());

        if (hits.size() >= MAX_HITS) hits.removeFirst();
        hits.addLast(new Hit(report.at(), detail));

        if (!notify.get()) return;

        long now = System.currentTimeMillis();
        if (now - lastNotify < NOTIFY_COOLDOWN_MS) return;

        lastNotify = now;
        MessageUtils.sendMessage(mc, Component.empty().append(MinecraftClientAudiences.of().asNative(
            MiniMessage.format(
                "<warning>mod fingerprint probe<dark_gray>: <off_white><detail>",
                Placeholder.unparsed("detail", detail)
            )
        )));
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        reports.clear();
        planted.clear();
        hits.clear();
        keys.clear();
    }

    /**
     * Keys grouped by whatever sits in front of the first dot, which is the mod id for most of them.
     */
    private Map<String, List<String>> byNamespace() {
        Map<String, List<String>> grouped = new TreeMap<>();

        for (String key : keys) {
            int dot = key.indexOf('.');
            String namespace = dot > 0 ? key.substring(0, dot).toLowerCase(Locale.ROOT) : "(none)";

            grouped.computeIfAbsent(namespace, k -> new ArrayList<>()).add(key);
        }

        return grouped;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textUnformatted("Probes: " + hits.size());
        ImGui.textUnformatted("Distinct keys: " + keys.size());

        if (ImGui.button("Clear##probeDetectClear")) {
            hits.clear();
            keys.clear();
        }

        if (ImGui.treeNode("Asked about##probeDetectKeys")) {
            for (Map.Entry<String, List<String>> entry : byNamespace().entrySet()) {
                if (!ImGui.treeNode(entry.getKey() + " (" + entry.getValue().size() + ")##ns")) continue;

                for (String key : entry.getValue()) ImGui.textUnformatted(key);
                ImGui.treePop();
            }

            ImGui.treePop();
        }

        if (!ImGui.treeNode("Log##probeDetectLog")) return;

        for (Hit hit : hits.reversed()) ImGui.textUnformatted(hit.detail());

        ImGui.treePop();
    }
}
