package com.tricrotism.modules.blink;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import org.lwjgl.glfw.GLFW;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Blink — queues all outbound packets while active. On deactivate,
 * flushes them to the server in order so movement + interactions replay.
 */
public class Blink extends Module implements Menu {

    public static final Blink instance = new Blink();

    private final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();

    @Getter
    private volatile boolean flushing;

    private boolean awaitingKeybind;
    private boolean keyWasDown;

    public Blink() {
        super("blink", "Blink", "Queue outbound packets, replay on disable.");
    }

    // ── Packet interception (called from ConnectionMixin) ───────────

    public boolean capturePacket(Packet<?> packet) {
        if (!isActive() || flushing) return false;
        packetQueue.add(packet);
        return true;
    }

    public int queueSize() {
        return packetQueue.size();
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    @Override
    public void onActivate() {
        packetQueue.clear();
        SageFang.LOGGER.info("[Blink] Activated — capturing packets");
    }

    @Override
    public void onDeactivate() {
        flush();
    }

    private void flush() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            packetQueue.clear();
            return;
        }

        int count = packetQueue.size();
        SageFang.LOGGER.info("[Blink] Flushing {} queued packets", count);

        flushing = true;
        try {
            Packet<?> pkt;
            while ((pkt = packetQueue.poll()) != null) {
                conn.send(pkt);
            }
        } finally {
            flushing = false;
        }

        SageFang.LOGGER.info("[Blink] Flush complete");
    }

    // ── Events ──────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Post event) {
        int key = getKeybind();
        if (key == GLFW.GLFW_KEY_UNKNOWN) return;

        boolean down = KeybindUtil.isKeyDown(key);
        if (down && !keyWasDown) {
            toggle();
        }
        keyWasDown = down;
    }

    @EventHandler
    private void onGameLeft(GameQuitEvent event) {
        if (isActive()) {
            toggle();
        }
    }

    // ── Keybind config ──────────────────────────────────────────────

    private int getKeybind() {
        return Config.getInt(baseConfig + ".keybind", GLFW.GLFW_KEY_B);
    }

    private void setKeybind(int key) {
        Config.setProperty(baseConfig + ".keybind", String.valueOf(key));
    }

    // ── ImGui menu ──────────────────────────────────────────────────

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!isVisible()) {
                return;
            }

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin(title, flags);

            if (ImGui.checkbox("Enabled##blinkEnabled", isActive())) {
                toggle();
            }

            ImGui.text("Queued: " + packetQueue.size());

            ImGui.separator();

            // Keybind
            int result = KeybindUtil.renderKeybindButton("Toggle", "blinkKeybind", getKeybind(), awaitingKeybind);
            if (result == KeybindUtil.START_LISTENING) {
                awaitingKeybind = true;
            } else if (result == KeybindUtil.CLEAR_BIND) {
                setKeybind(GLFW.GLFW_KEY_UNKNOWN);
                awaitingKeybind = false;
            } else if (result != KeybindUtil.NO_CHANGE) {
                setKeybind(result);
                awaitingKeybind = false;
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in Blink menu", e);
        }
    }
}
