package com.tricrotism.modules.testing;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.MessageUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Records what the server does back to us, and how fast.
 * <p>
 * The rest of this client works the other way round. {@code BadPackets}, {@code ForgedAck} and
 * {@code Timer} send something and wait to see whether anybody objects, and {@code RefusalMonitor}
 * works out when a check has declined to look at all. None of them writes down the reply. A run that
 * produced eleven setbacks at a median of sixty milliseconds after a movement packet, all of them
 * absolute and under a tenth of a block, is a different result from one that produced two at four
 * hundred milliseconds, and without the reply side both look the same from here: something happened.
 * <p>
 * Four replies are worth separating, because they come from different places in a server:
 * <ul>
 *   <li><b>setback</b>, a position packet moving us off where we last said we were. The delay since
 *       that claim is the useful number, since it says whether the decision was made inside the
 *       tick that received the movement or a few ticks later by something batching.</li>
 *   <li><b>velocity</b>, a motion packet for our own entity with no damage in front of it. With
 *       damage, it is knockback and vanilla; without, something chose to move us.</li>
 *   <li><b>transaction</b>, the ping and pong pair a server uses to pin an answer to a moment. Only
 *       the cadence and the id range are kept, never one line each, because an anticheat that uses
 *       these sends one per tick and a log of that is not a log.</li>
 *   <li><b>ability</b>, flight taken away after being granted, which is the one correction that
 *       arrives as a permission rather than as a position.</li>
 * </ul>
 * <p>
 * <b>Observables, not verdicts.</b> This reports delays, distances, flags and cadences. It does not
 * name an anticheat, because the mapping from a signature to a product is not something this can
 * verify from the client and a confident wrong label is worse than a number. Read the numbers and
 * decide.
 */
public final class CorrectionLog extends Module {

    // declared before instance, which reads it in its field initializers
    private static final Claim NONE = new Claim(0, 0, 0, 0);

    public static final CorrectionLog instance = new CorrectionLog();

    private static final int MAX_EVENTS = 200;
    private static final long NOTIFY_COOLDOWN_MS = 1000L;

    // a motion packet this soon after damage is knockback rather than a correction
    private static final long KNOCKBACK_GRACE_MS = 250L;

    private final Settings.Bool setbacks =
        bool("Setbacks", "setbacks", "Position packets that move us off our last claim.", true);
    private final Settings.Bool velocity =
        bool("Velocity", "velocity", "Motion packets for us with no damage behind them.", true);
    private final Settings.Bool transactions =
        bool("Transactions", "transactions", "Cadence and id range of server ping and pong pairs.", true);
    private final Settings.Bool abilities =
        bool("Abilities", "abilities", "Flight revoked after being granted.", true);
    private final Settings.Decimal minDistance =
        decimal("Min setback", "mindistance", "Ignore position packets under this, in blocks.", 0.01, 0.0, 16.0);
    private final Settings.Bool notify =
        bool("Chat notify", "notify", "Announce corrections in chat.", true);

    private final Queue<Event> incoming = new ConcurrentLinkedQueue<>();

    /**
     * One reference rather than four fields. The main thread writes our last claimed position and the
     * Netty thread reads it as a group, so four independent volatiles let a setback distance be
     * measured from a new X against an old Z, timed by a third packet's clock. For a module whose
     * entire output is those numbers, that is the bug that matters.
     */
    private record Claim(double x, double y, double z, long at) {}

    private volatile Claim claim = NONE;
    private volatile long lastHurtAt;
    private volatile long lastPingAt;
    private volatile int selfId = -1;
    private volatile boolean couldFly;

    private final Deque<Event> events = new ArrayDeque<>();
    private final Map<Kind, Stat> stats = new EnumMap<>(Kind.class);
    private long lastNotify;

    private CorrectionLog() {
        super("correctionlog", "Correction Log",
            "Record what the server does back to us, and how fast.", Category.LOGGING);
    }

    private enum Kind {SETBACK, VELOCITY, TRANSACTION, ABILITY}

    private record Event(long at, Kind kind, long delayMs, double magnitude, String detail) {}

    private static final class Stat {
        int count;
        long minDelay = Long.MAX_VALUE;
        long maxDelay;
        long sumDelay;
        double sumMagnitude;
        int minId = Integer.MAX_VALUE;
        int maxId = Integer.MIN_VALUE;

        void add(long delay, double magnitude) {
            count++;
            sumDelay += delay;
            sumMagnitude += magnitude;
            if (delay < minDelay) minDelay = delay;
            if (delay > maxDelay) maxDelay = delay;
        }

        long meanDelay() {
            return count == 0 ? 0 : sumDelay / count;
        }

        double meanMagnitude() {
            return count == 0 ? 0 : sumMagnitude / count;
        }
    }

    /**
     * Called from ConnectionMixin's send hook. {@code Connection.send} runs on the caller's thread,
     * which is usually the main one but is Netty when a packet handler re-sends and the sampler
     * thread when a scheduler does, so this writes the claim as a single reference and reads no
     * world state. The position a setback is measured against is the one we last claimed rather than
     * the one the client has already been moved to by the time a tick runs.
     */
    public void onOutbound(Packet<?> packet) {
        if (!isActive() || !(packet instanceof ServerboundMovePlayerPacket move) || !move.hasPosition()) return;

        Claim previous = claim;

        claim = new Claim(
            move.getX(previous.x()),
            move.getY(previous.y()),
            move.getZ(previous.z()),
            System.currentTimeMillis());
    }

    /**
     * Called from ConnectionMixin on the network thread.
     */
    public void onInbound(Packet<?> packet) {
        if (!isActive()) return;

        switch (packet) {
            case ClientboundPlayerPositionPacket position -> onPosition(position);
            case ClientboundSetEntityMotionPacket motion -> onMotion(motion);
            case ClientboundPingPacket ping -> onPing(ping);
            case ClientboundPlayerAbilitiesPacket ability -> onAbilities(ability);
            case ClientboundHurtAnimationPacket hurt -> {
                if (hurt.id() == selfId) lastHurtAt = System.currentTimeMillis();
            }
            default -> {
            }
        }
    }

    private void onPosition(ClientboundPlayerPositionPacket packet) {
        Claim from = claim;
        if (!setbacks.get() || from.at() == 0) return;

        Vec3 to = packet.change().position();
        Set<Relative> relatives = packet.relatives();

        // a relative packet nudges rather than places, so our last claim is not the right baseline
        double distance = relatives.contains(Relative.X) || relatives.contains(Relative.Z)
            ? -1
            : Math.sqrt(square(to.x - from.x()) + square(to.y - from.y()) + square(to.z - from.z()));

        if (distance >= 0 && distance < minDistance.get()) return;

        long delay = System.currentTimeMillis() - from.at();
        String detail = (distance < 0 ? "relative" : String.format(Locale.ROOT, "%.3f blocks", distance))
            + ", teleport id " + packet.id()
            + (relatives.isEmpty() ? ", absolute" : ", relative " + relatives);

        incoming.add(new Event(System.currentTimeMillis(), Kind.SETBACK, delay, Math.max(distance, 0), detail));
    }

    private void onMotion(ClientboundSetEntityMotionPacket packet) {
        if (!velocity.get() || packet.id() != selfId) return;

        long now = System.currentTimeMillis();
        if (now - lastHurtAt < KNOCKBACK_GRACE_MS) return;
        // No move claimed yet, so there is no delay to measure from.
        if (claim.at() == 0) return;

        Vec3 push = packet.movement();
        double magnitude = push.length();
        if (magnitude <= 0) return;

        incoming.add(new Event(now, Kind.VELOCITY, now - claim.at(), magnitude,
            String.format(Locale.ROOT, "%.3f blocks per tick, no damage behind it", magnitude)));
    }

    /**
     * Cadence only. An anticheat leaning on transactions sends one per tick, so a line each would
     * bury everything else in the log.
     */
    private void onPing(ClientboundPingPacket packet) {
        if (!transactions.get()) return;

        long now = System.currentTimeMillis();
        long gap = lastPingAt == 0 ? 0 : now - lastPingAt;
        lastPingAt = now;

        incoming.add(new Event(now, Kind.TRANSACTION, gap, packet.getId(), null));
    }

    private void onAbilities(ClientboundPlayerAbilitiesPacket packet) {
        boolean now = packet.canFly();
        boolean before = couldFly;
        couldFly = now;

        if (!abilities.get() || !before || now || claim.at() == 0) return;

        long at = System.currentTimeMillis();
        incoming.add(new Event(at, Kind.ABILITY, at - claim.at(), 0, "flight revoked"));
    }

    private static double square(double value) {
        return value * value;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) {
            incoming.clear();
            return;
        }

        selfId = mc.player == null ? -1 : mc.player.getId();

        Event correction;

        while ((correction = incoming.poll()) != null) {
            Stat stat = stats.computeIfAbsent(correction.kind(), k -> new Stat());
            stat.add(correction.delayMs(), correction.magnitude());

            if (correction.kind() == Kind.TRANSACTION) {
                int id = (int) correction.magnitude();
                if (id < stat.minId) stat.minId = id;
                if (id > stat.maxId) stat.maxId = id;
                continue;
            }

            if (events.size() >= MAX_EVENTS) events.removeFirst();
            events.addLast(correction);
            announce(correction);
        }
    }

    private void announce(Event correction) {
        if (!notify.get()) return;

        long now = System.currentTimeMillis();
        if (now - lastNotify < NOTIFY_COOLDOWN_MS) return;

        lastNotify = now;
        MessageUtils.sendMessage(mc, Component.empty().append(MinecraftClientAudiences.of().asNative(
            MiniMessage.format(
                "<warning><kind><dark_gray>: <off_white><detail> <dark_gray>(<delay>ms after our move)",
                Placeholder.unparsed("kind", correction.kind().name().toLowerCase(Locale.ROOT)),
                Placeholder.unparsed("detail", correction.detail()),
                Placeholder.unparsed("delay", String.valueOf(correction.delayMs()))
            )
        )));
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        reset();
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
        incoming.clear();
        events.clear();
        stats.clear();
        claim = NONE;
        lastHurtAt = 0;
        lastPingAt = 0;
        couldFly = false;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        for (Kind kind : Kind.values()) {
            Stat stat = stats.get(kind);
            if (stat == null) continue;

            String label = kind.name().toLowerCase(Locale.ROOT);

            if (kind == Kind.TRANSACTION) {
                ImGui.textUnformatted(String.format(Locale.ROOT, "%s: %d, every %dms, ids %d to %d",
                    label, stat.count, stat.meanDelay(), stat.minId, stat.maxId));
                continue;
            }

            ImGui.textUnformatted(String.format(Locale.ROOT, "%s: %d, delay %d/%d/%dms, mean %.3f",
                label, stat.count, stat.minDelay, stat.meanDelay(), stat.maxDelay, stat.meanMagnitude()));
        }

        if (stats.isEmpty()) ImGui.textUnformatted("Nothing yet");

        if (ImGui.button("Clear##correctionLogClear")) {
            events.clear();
            stats.clear();
        }

        if (!ImGui.treeNode("Log##correctionLogList")) return;

        for (Event correction : events.reversed()) {
            ImGui.textUnformatted(correction.kind().name().toLowerCase(Locale.ROOT)
                + " +" + correction.delayMs() + "ms: " + correction.detail());
        }

        ImGui.treePop();
    }
}
