package com.tricrotism.modules.esp;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.utils.EspRenderUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flags chunks where the server sent sky light for suspiciously deep sections —
 * sky light shouldn't reach far underground in natural terrain, so it usually
 * means a dug-out base/tunnel. Each suspicious section is boxed. Ported and
 * streamlined from the Meteor addon's LightMapESP: the actual per-block light-map
 * render, nibble decoding and light-threshold filter are dropped; detection is on
 * the sky-light section mask alone.
 */
public final class SkyLightESP extends Module implements Menu {

    public static final SkyLightESP instance = new SkyLightESP();

    private final Map<Long, int[]> susSections = new ConcurrentHashMap<>();
    private int range;
    private int sectionThreshold;

    private SkyLightESP() {
        super("suschunks", "Sus Chunks", "Flag chunks where sky light reaches deep (dug-out bases).", "ESP");
        range = Config.getInt(baseConfig + ".range", 12);
        sectionThreshold = Config.getInt(baseConfig + ".sectionThreshold", 5);
        LevelRenderEvents.BEFORE_GIZMOS.register(this::render);
    }

    /**
     * Called from ConnectionMixin on the network thread with a chunk's light data.
     */
    public void onChunkLight(int cx, int cz, ClientboundLightUpdatePacketData lightData) {
        if (!isActive() || lightData == null) return;
        BitSet sky = lightData.getSkyYMask();
        if (sky == null) return;

        int[] tmp = new int[sectionThreshold];
        int n = 0;
        for (int idx = sky.nextSetBit(0); idx != -1 && idx < sectionThreshold; idx = sky.nextSetBit(idx + 1)) {
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
            Math.max(Math.abs(ChunkPos.getX(k) - pc.x()), Math.abs(ChunkPos.getZ(k) - pc.z())) > range);
        if (susSections.isEmpty()) return;

        int levelBottom = mc.level.getMinY() - 16; // light section index 0 sits one section below the world

        var consumers = context.bufferSource();
        if (consumers == null) return;

        var matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = matrices.last().pose();
        var lines = consumers.getBuffer(RenderTypes.lines());

        for (Map.Entry<Long, int[]> e : susSections.entrySet()) {
            double x0 = ChunkPos.getX(e.getKey()) << 4;
            double z0 = ChunkPos.getZ(e.getKey()) << 4;
            for (int idx : e.getValue()) {
                double y0 = levelBottom + idx * 16;
                EspRenderUtils.drawBox(pose, lines, x0, y0, z0, x0 + 16, y0 + 16, z0 + 16,
                    1f, 0.85f, 0.1f, 0.7f, 2.0f);
            }
        }

        matrices.popPose();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##susChunksEnabled", isActive())) toggle();

        int[] r = {range};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Range (chunks)##scRange", r, 2, 24)) {
            range = r[0];
            Config.setProperty(baseConfig + ".range", String.valueOf(range));
        }
        int[] st = {sectionThreshold};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Deep Sections##scThreshold", st, 1, 12)) {
            sectionThreshold = st[0];
            Config.setProperty(baseConfig + ".sectionThreshold", String.valueOf(sectionThreshold));
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Flag sky light in the lowest N light sections.");

        ImGui.text("Sus chunks: " + susSections.size());
        ImGui.end();
    }
}
