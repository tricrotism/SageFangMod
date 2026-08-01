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
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights chunks that receive server light updates as full-height columns,
 * fading fresh (green) to old (red) with age — a base-finding heuristic (activity
 * in far chunks lights up). Ported (streamlined) from the Meteor addon's
 * LightUpdateESP: the per-section light-mask decoding and the low-light block-scan
 * mode are dropped in favour of a whole-column highlight.
 */
public final class LightUpdateESP extends Module implements Menu {

    public static final LightUpdateESP instance = new LightUpdateESP();

    private final Map<Long, Long> updates = new ConcurrentHashMap<>();
    private int maxAgeSeconds;
    private int exclusionChunks;

    private LightUpdateESP() {
        super("lightupdateesp", "Light Update ESP", "Highlight chunks that receive server light updates.", "ESP");
        maxAgeSeconds = Config.getInt(baseConfig + ".maxAge", 30);
        exclusionChunks = Config.getInt(baseConfig + ".exclusion", 4);
        LevelRenderEvents.BEFORE_GIZMOS.register(this::render);
    }

    /**
     * Called from ConnectionMixin on the network thread.
     */
    public void onLightUpdate(ClientboundLightUpdatePacket packet) {
        if (!isActive()) return;
        updates.put(ChunkPos.pack(packet.getX(), packet.getZ()), System.currentTimeMillis());
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
        long maxAgeMs = maxAgeSeconds * 1000L;
        updates.values().removeIf(t -> now - t > maxAgeMs);
        if (updates.isEmpty()) return;

        ChunkPos playerChunk = mc.player.chunkPosition();
        double y0 = mc.level.getMinY();
        double y1 = mc.level.getMinY() + mc.level.getHeight();

        var consumers = context.bufferSource();
        if (consumers == null) return;

        var matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = matrices.last().pose();
        var lines = consumers.getBuffer(RenderTypes.lines());

        for (Map.Entry<Long, Long> e : updates.entrySet()) {
            int cx = ChunkPos.getX(e.getKey());
            int cz = ChunkPos.getZ(e.getKey());
            if (Math.abs(cx - playerChunk.x()) <= exclusionChunks && Math.abs(cz - playerChunk.z()) <= exclusionChunks) {
                continue;
            }
            float age = Math.min(1f, (now - e.getValue()) / (float) maxAgeMs);
            double x0 = cx << 4;
            double z0 = cz << 4;
            EspRenderUtils.drawBox(pose, lines, x0, y0, z0, x0 + 16, y1, z0 + 16, age, 1f - age, 0.15f, 0.6f, 2.0f);
        }

        matrices.popPose();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##lightUpdateEnabled", isActive())) toggle();

        int[] age = {maxAgeSeconds};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Max Age (s)##luMaxAge", age, 1, 120)) {
            maxAgeSeconds = age[0];
            Config.setProperty(baseConfig + ".maxAge", String.valueOf(maxAgeSeconds));
        }

        int[] ex = {exclusionChunks};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Exclusion (chunks)##luExclusion", ex, 0, 16)) {
            exclusionChunks = ex[0];
            Config.setProperty(baseConfig + ".exclusion", String.valueOf(exclusionChunks));
        }

        ImGui.text("Chunks: " + updates.size());
        ImGui.end();
    }
}
