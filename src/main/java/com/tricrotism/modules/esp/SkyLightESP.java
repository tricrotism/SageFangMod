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
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flags chunks where the server sent sky light for suspiciously deep sections.
 * Sky light shouldn't reach far underground in natural terrain, so it usually
 * means a dug-out base/tunnel. Each suspicious section is boxed. Ported and
 * streamlined from the Meteor addon's LightMapESP: the actual per-block light-map
 * render, nibble decoding and light-threshold filter are dropped; detection is on
 * the sky-light section mask alone.
 */
public final class SkyLightESP extends Module {

    public static final SkyLightESP instance = new SkyLightESP();

    private final Settings.Int range =
        integer("Range (chunks)", "range", "Chunk radius to scan", 12, 2, 24);
    private final Settings.Int sectionThreshold =
        integer("Deep Sections", "sectionThreshold", "Lit sections below surface before flagging", 5, 1, 12);

    private final Map<Long, int[]> susSections = new ConcurrentHashMap<>();

    private SkyLightESP() {
        super("suschunks", "Sus Chunks", "Flag chunks where sky light reaches deep (dug-out bases).", Category.RENDER);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
    }

    /**
     * Called from ConnectionMixin on the network thread with a chunk's light data.
     */
    public void onChunkLight(int cx, int cz, ClientboundLightUpdatePacketData lightData) {
        if (!isActive() || lightData == null) return;
        BitSet sky = lightData.getSkyYMask();
        if (sky == null) return;

        int[] tmp = new int[sectionThreshold.get()];
        int n = 0;
        for (int idx = sky.nextSetBit(0); idx != -1 && idx < sectionThreshold.get(); idx = sky.nextSetBit(idx + 1)) {
            tmp[n++] = idx;
        }
        long key = ChunkPos.pack(cx, cz);
        if (n == 0) {
            susSections.remove(key);
            return;
        }
        int[] sus = new int[n];
        System.arraycopy(tmp, 0, sus, 0, n);
        susSections.put(key, sus);
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        susSections.clear();
    }

    @Override
    public void onDeactivate() {
        susSections.clear();
    }

    private void render(LevelRenderContext context) {
        if (!isActive() || susSections.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ChunkPos pc = mc.player.chunkPosition();
        susSections.keySet().removeIf(k ->
            Math.max(Math.abs(ChunkPos.getX(k) - pc.x()), Math.abs(ChunkPos.getZ(k) - pc.z())) > range.get());
        if (susSections.isEmpty()) return;

        int levelBottom = mc.level.getMinY() - 16; // light section index 0 sits one section below the world

        List<double[]> boxes = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : susSections.entrySet()) {
            double x0 = ChunkPos.getX(e.getKey()) << 4;
            double z0 = ChunkPos.getZ(e.getKey()) << 4;
            for (int idx : e.getValue()) boxes.add(new double[]{x0, levelBottom + idx * 16, z0});
        }
        if (boxes.isEmpty()) return;

        EspRenderUtils.submitBoxes(context, sink -> {
            for (double[] box : boxes) {
                sink.box(box[0], box[1], box[2], box[0] + 16, box[1] + 16, box[2] + 16,
                    1f, 0.85f, 0.1f, 0.7f, 2.0f);
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Sus chunks: " + susSections.size());
    }
}
