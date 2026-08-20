package com.tricrotism.modules.blink;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import org.lwjgl.glfw.GLFW;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Blink queues all outbound packets while active. On deactivate it
 * flushes them to the server in order so movement and interactions replay.
 */
public class Blink extends Module {

    public static final Blink instance = new Blink();

    private final Settings.Key keybind = key("Toggle", "keybind", "Activation key", GLFW.GLFW_KEY_O);

    private final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private final BlinkRenderer renderer = new BlinkRenderer();

    @Getter
    private volatile boolean flushing;

    private boolean keyWasDown;

    public Blink() {
        super("blink", "Blink", "Queue outbound packets, replay on disable.", Category.NETWORK);
        renderer.register();
    }

    public boolean capturePacket(Packet<?> packet) {
        if (!isActive() || flushing) return false;
        packetQueue.add(packet);
        return true;
    }

    private long heldSinceMs;

    public int queueSize() {
        return packetQueue.size();
    }

    @Override
    public void onActivate() {
        packetQueue.clear();
        renderer.onActivate();
        heldSinceMs = System.currentTimeMillis();
        SageFang.LOGGER.info("[Blink] Activated, capturing packets");
        TestLog.event("blink_hold_start");
    }

    @Override
    public void onDeactivate() {
        renderer.onDeactivate();
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
        TestLog.event("blink_flush",
            "packets", count,
            "heldMs", heldSinceMs == 0L ? 0L : System.currentTimeMillis() - heldSinceMs);

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

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (isActive()) {
            renderer.onTick();
        }

        int key = keybind.get();
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

            keybind.render();

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in Blink menu", e);
        }
    }
}
