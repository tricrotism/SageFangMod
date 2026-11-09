package com.tricrotism.modules.logger;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.ImGuiUtil;
import com.tricrotism.utils.MessageUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Reads the server's real tick rate off the clock it broadcasts, without asking it anything.
 * <p>
 * A vanilla server sends the game time every twenty ticks. That value is a tick counter, so the game
 * time between two of those divided by the wall clock between them is the rate the server actually
 * ticked, whatever it reports elsewhere. Nothing is sent to obtain it and no plugin is involved, so
 * it cannot be answered with a number somebody chose.
 * <p>
 * Worth having next to the rest of this client because a result read during a tick dip is not a
 * result. A refusal, a setback or a timing window measured while the server was at twelve ticks a
 * second says nothing about the check that produced it.
 * <p>
 * Two things make it read empty rather than wrong. A server can stop sending time updates at all,
 * which shows as no samples rather than as zero. And a frozen tick loop stops the clock, so a freeze
 * is the absence of a packet rather than a low number, which is why it is timed separately.
 */
public final class ServerPulse extends Module {

    public static final ServerPulse instance = new ServerPulse();

    private static final int MAX_SAMPLES = 64;
    private static final double VANILLA_TPS = 20.0;

    private final Settings.Int window =
        integer("Window", "window", "How many time updates the average spans.", 10, 2, MAX_SAMPLES);
    private final Settings.Int freezeMillis =
        integer("Freeze after", "freezems", "Silence this long counts as a stalled tick loop, in ms.", 2500, 500, 30000);
    private final Settings.Bool notify =
        bool("Chat notify", "notify", "Announce freezes in chat.", true);

    private final Queue<Sample> incoming = new ConcurrentLinkedQueue<>();
    private final Deque<Sample> samples = new ArrayDeque<>();

    private double tps;
    private double worst = VANILLA_TPS;
    private int freezes;
    private long lastSampleAt;
    private boolean frozen;

    // the latency the server publishes for us, which is its own claim rather than a measurement
    private volatile UUID self;
    private volatile int reported = -1;

    private ServerPulse() {
        super("serverpulse", "Server Pulse", "Measure the server's real tick rate from its own clock.", Category.LOGGING);
    }

    private record Sample(long gameTime, long nanos) {}

    /**
     * Called from ConnectionMixin on the network thread.
     */
    public void onInbound(Packet<?> packet) {
        if (!isActive()) return;

        if (packet instanceof ClientboundSetTimePacket time) {
            incoming.add(new Sample(time.gameTime(), System.nanoTime()));
            return;
        }

        if (packet instanceof ClientboundPlayerInfoUpdatePacket info
            && info.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY)) {
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
                if (entry.profileId().equals(self)) reported = entry.latency();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) {
            incoming.clear();
            return;
        }

        if (mc.player != null) self = mc.player.getUUID();

        Sample sample;
        boolean arrived = false;

        while ((sample = incoming.poll()) != null) {
            while (samples.size() >= window.get()) samples.removeFirst();
            samples.addLast(sample);
            arrived = true;
        }

        if (arrived) {
            lastSampleAt = System.currentTimeMillis();
            frozen = false;
            recompute();
        }

        checkFreeze();
    }

    /**
     * Game time is a tick counter, so its delta over the wall-clock delta is the rate itself. Clamped
     * at the top because a server catching up after a stall runs the loop faster than real time for a
     * moment, and a reading above twenty says nothing useful.
     */
    private void recompute() {
        if (samples.size() < 2) return;

        Sample first = samples.peekFirst();
        Sample last = samples.peekLast();

        long ticks = last.gameTime() - first.gameTime();
        double seconds = (last.nanos() - first.nanos()) / 1_000_000_000.0;
        if (ticks <= 0 || seconds <= 0) return;

        tps = Math.min(ticks / seconds, VANILLA_TPS);
        if (tps < worst) worst = tps;
    }

    private void checkFreeze() {
        if (samples.isEmpty() || frozen) return;

        long silence = System.currentTimeMillis() - lastSampleAt;
        if (silence < freezeMillis.get()) return;

        frozen = true;
        freezes++;
        tps = 0;
        worst = 0;

        if (!notify.get()) return;

        MessageUtils.sendMessage(mc, Component.empty().append(MinecraftClientAudiences.of().asNative(
            MiniMessage.format(
                "<error>server pulse lost<dark_gray>: <off_white>no time update for <silence>ms",
                Placeholder.unparsed("silence", String.valueOf(silence))
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
        samples.clear();
        tps = 0;
        worst = VANILLA_TPS;
        freezes = 0;
        lastSampleAt = 0;
        frozen = false;
        reported = -1;
    }

    public double tps() {
        return tps;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        if (samples.isEmpty()) {
            ImGui.textUnformatted("No time updates from this server yet");
            return;
        }

        ImGui.textUnformatted(String.format("TPS: %.2f", tps));
        ImGui.textUnformatted(String.format("Worst: %.2f", worst));
        ImGui.textUnformatted("Freezes: " + freezes);
        ImGui.textUnformatted("Samples: " + samples.size() + "/" + window.get());
        ImGui.textUnformatted("Latency the server reports for us: " + (reported < 0 ? "not sent yet" : reported + "ms"));
        if (ImGui.isItemHovered()) {
            ImGuiUtil.setTooltip("The server's own claim, not a measurement. Watch it while Ping Spoof "
                + "is on: if it does not move, the server is not timing us off the packets Ping Spoof delays.");
        }

        if (frozen) ImGui.textUnformatted("Tick loop is stalled right now");

        if (ImGui.button("Reset##serverPulseReset")) {
            worst = VANILLA_TPS;
            freezes = 0;
        }
    }
}
