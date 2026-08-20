package com.tricrotism.modules.esp;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
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
    }

    @Override
    public void onDeactivate() {
        updates.clear();
    }

    private void render(LevelRenderContext context) {
        if (!isActive() || updates.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        long maxAgeMs = maxAgeSeconds.get() * 1000L;
        updates.values().removeIf(t -> now - t > maxAgeMs);
        if (updates.isEmpty()) return;

        ChunkPos playerChunk = mc.player.chunkPosition();
        double y0 = mc.level.getMinY();
        double y1 = mc.level.getMinY() + mc.level.getHeight();

        List<double[]> boxes = new ArrayList<>(updates.size());
        for (Map.Entry<Long, Long> e : updates.entrySet()) {
            int cx = ChunkPos.getX(e.getKey());
            int cz = ChunkPos.getZ(e.getKey());
            if (Math.abs(cx - playerChunk.x()) <= exclusionChunks.get() && Math.abs(cz - playerChunk.z()) <= exclusionChunks.get()) {
                continue;
            }
            boxes.add(new double[]{cx << 4, cz << 4, Math.min(1f, (now - e.getValue()) / (float) maxAgeMs)});
        }
        if (boxes.isEmpty()) return;

        EspRenderUtils.submitBoxes(context, sink -> {
            for (double[] box : boxes) {
                float age = (float) box[2];
                sink.box(box[0], y0, box[1], box[0] + 16, y1, box[1] + 16, age, 1f - age, 0.15f, 0.6f, 2.0f);
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Chunks: " + updates.size());
    }
}
