package com.tricrotism.modules.esp;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.EspRenderUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights chunks that receive server light updates as full-height columns,
 * fading fresh (green) to old (red) with age. A base-finding heuristic (activity
 * in far chunks lights up). Ported (streamlined) from the Meteor addon's
 * LightUpdateESP: the per-section light-mask decoding and the low-light block-scan
 * mode are dropped in favour of a whole-column highlight.
 */
public final class LightUpdateESP extends Module {

    public static final LightUpdateESP instance = new LightUpdateESP();

    private final Settings.Int maxAgeSeconds =
        integer("Max Age (s)", "maxAge", "How long a chunk stays highlighted", 30, 1, 120);
    private final Settings.Int exclusionChunks =
        integer("Exclusion (chunks)", "exclusion", "Ignore chunks this close to you", 4, 0, 16);

    private final Map<Long, Long> updates = new ConcurrentHashMap<>();

    // built on the tick, read by the draw call, swapped whole rather than mutated
    private volatile List<double[]> boxes = List.of();

    private LightUpdateESP() {
        super("lightupdateesp", "Light Update ESP", "Highlight chunks that receive server light updates.", Category.RENDER);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
    }

    /**
     * Called from ConnectionMixin on the network thread.
     */
    public void onLightUpdate(ClientboundLightUpdatePacket packet) {
        if (!isActive()) return;
        updates.put(ChunkPos.pack(packet.getX(), packet.getZ()), System.currentTimeMillis());
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        updates.clear();
        boxes = List.of();
    }

    @Override
    public void onDeactivate() {
        updates.clear();
        boxes = List.of();
    }

    /**
     * Expiry and the exclusion filter run here, once a tick, and publish the list the draw call
     * reads. Doing it in {@code render} meant sweeping the map, allocating a list and testing every
     * chunk against the player at frame rate, for a set that only changes when a light packet
     * arrives. The fade is over seconds, so a tick of granularity is invisible.
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            boxes = List.of();
            return;
        }

        long now = System.currentTimeMillis();
        long maxAgeMs = maxAgeSeconds.get() * 1000L;
        updates.values().removeIf(t -> now - t > maxAgeMs);

        if (updates.isEmpty()) {
            boxes = List.of();
            return;
        }

        int exclusion = exclusionChunks.get();
        ChunkPos playerChunk = mc.player.chunkPosition();
        List<double[]> built = new ArrayList<>(updates.size());

        for (Map.Entry<Long, Long> e : updates.entrySet()) {
            int cx = ChunkPos.getX(e.getKey());
            int cz = ChunkPos.getZ(e.getKey());
            if (Math.abs(cx - playerChunk.x()) <= exclusion && Math.abs(cz - playerChunk.z()) <= exclusion) {
                continue;
            }
            built.add(new double[]{cx << 4, cz << 4, Math.min(1f, (now - e.getValue()) / (float) maxAgeMs)});
        }

        boxes = List.copyOf(built);
    }

    private void render(LevelRenderContext context) {
        List<double[]> snapshot = boxes;
        if (!isActive() || snapshot.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        double y0 = mc.level.getMinY();
        double y1 = mc.level.getMinY() + mc.level.getHeight();

        EspRenderUtils.submitBoxes(context, sink -> {
            for (double[] box : snapshot) {
                float age = (float) box[2];
                sink.box(box[0], y0, box[1], box[0] + 16, y1, box[1] + 16, age, 1f - age, 0.15f, 0.6f, 2.0f);
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textUnformatted("Chunks: " + updates.size());
    }
}
