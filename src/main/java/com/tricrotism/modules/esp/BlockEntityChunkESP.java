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
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights every block entity (chests, spawners, shulkers, hoppers…) carried in
 * incoming chunk packets, at their exact positions — great for spotting stashes
 * as chunks stream in. Ported from the Meteor addon's block-entity-chunk-esp; the
 * block-type filter and logging options are dropped for a clean UI.
 */
public final class BlockEntityChunkESP extends Module implements Menu {

    public static final BlockEntityChunkESP instance = new BlockEntityChunkESP();

    private final Map<Long, List<BlockPos>> chunkEntities = new ConcurrentHashMap<>();
    private int chunkRadius;
    private int minBlockEntities;

    private BlockEntityChunkESP() {
        super("blockentitychunkesp", "Block Entity ESP", "Highlight block entities from incoming chunk packets.", "ESP");
        chunkRadius = Config.getInt(baseConfig + ".chunkRadius", 16);
        minBlockEntities = Config.getInt(baseConfig + ".minBlockEntities", 1);
        LevelRenderEvents.BEFORE_GIZMOS.register(this::render);
    }

    /**
     * Called from ConnectionMixin on the network thread as chunks arrive.
     */
    public void onChunk(ClientboundLevelChunkWithLightPacket packet) {
        if (!isActive()) return;
        int cx = packet.getX();
        int cz = packet.getZ();
        List<BlockPos> positions = new ArrayList<>();
        try {
            packet.getChunkData().getBlockEntitiesTagsConsumer(cx, cz)
                .accept((pos, type, nbt) -> positions.add(pos.immutable()));
        } catch (Exception ignored) {
        }
        if (positions.size() < minBlockEntities) return;
        chunkEntities.put(ChunkPos.pack(cx, cz), positions);
    }

    @Override
    public void onActivate() {
        chunkEntities.clear();
    }

    @Override
    public void onDeactivate() {
        chunkEntities.clear();
    }

    private void render(LevelRenderContext context) {
        if (!isActive() || chunkEntities.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChunkPos playerChunk = ChunkPos.containing(mc.player.blockPosition());
        chunkEntities.keySet().removeIf(k ->
            Math.abs(ChunkPos.getX(k) - playerChunk.x()) > chunkRadius
                || Math.abs(ChunkPos.getZ(k) - playerChunk.z()) > chunkRadius);
        if (chunkEntities.isEmpty()) return;

        var consumers = context.bufferSource();
        if (consumers == null) return;

        var matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = matrices.last().pose();
        var lines = consumers.getBuffer(RenderTypes.lines());

        for (List<BlockPos> positions : chunkEntities.values()) {
            for (BlockPos p : positions) {
                EspRenderUtils.drawBox(pose, lines,
                    p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                    1f, 0.78f, 0.2f, 0.7f, 2.0f);
            }
        }

        matrices.popPose();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##beChunkEspEnabled", isActive())) toggle();

        int[] cr = {chunkRadius};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Chunk Radius##beChunkRadius", cr, 2, 32)) {
            chunkRadius = cr[0];
            Config.setProperty(baseConfig + ".chunkRadius", String.valueOf(chunkRadius));
        }

        int[] mb = {minBlockEntities};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Min Block Entities##beMin", mb, 1, 20)) {
            minBlockEntities = mb[0];
            Config.setProperty(baseConfig + ".minBlockEntities", String.valueOf(minBlockEntities));
        }

        ImGui.text("Chunks: " + chunkEntities.size());
        ImGui.end();
    }
}
