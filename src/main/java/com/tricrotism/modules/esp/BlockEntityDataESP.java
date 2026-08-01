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
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flags chunks and block positions that receive {@code ClientboundBlockEntityDataPacket}s —
 * a way to spot block entities (and thus bases) the server updates outside your
 * normal view. Flagged chunks get a surface marker, flagged blocks a box. Ported
 * from the Meteor addon's chunk-debug (the full grey chunk grid is dropped; only
 * flagged chunks/blocks are drawn).
 */
public final class BlockEntityDataESP extends Module implements Menu {

    public static final BlockEntityDataESP instance = new BlockEntityDataESP();

    private final Set<Long> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> flaggedBlocks = ConcurrentHashMap.newKeySet();
    private int radius;
    private boolean showBlocks;

    private BlockEntityDataESP() {
        super("chunkdebug", "Chunk Debug", "Flag chunks/blocks that get block-entity-data packets.", "ESP");
        radius = Config.getInt(baseConfig + ".radius", 16);
        showBlocks = Config.getBool(baseConfig + ".showBlocks", true);
        LevelRenderEvents.BEFORE_GIZMOS.register(this::render);
    }

    /**
     * Called from ConnectionMixin on the network thread.
     */
    public void onBlockEntityData(ClientboundBlockEntityDataPacket packet) {
        if (!isActive()) return;
        BlockPos pos = packet.getPos();
        flaggedChunks.add(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        flaggedBlocks.add(pos.immutable());
    }

    @Override
    public void onDeactivate() {
        flaggedChunks.clear();
        flaggedBlocks.clear();
    }

    private void render(LevelRenderContext context) {
        if (!isActive() || (flaggedChunks.isEmpty() && flaggedBlocks.isEmpty())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ChunkPos pc = mc.player.chunkPosition();
        flaggedChunks.removeIf(k -> Math.abs(ChunkPos.getX(k) - pc.x()) > radius || Math.abs(ChunkPos.getZ(k) - pc.z()) > radius);
        flaggedBlocks.removeIf(p -> Math.abs((p.getX() >> 4) - pc.x()) > radius || Math.abs((p.getZ() >> 4) - pc.z()) > radius);

        var consumers = context.bufferSource();
        if (consumers == null) return;

        var matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = matrices.last().pose();
        var lines = consumers.getBuffer(RenderTypes.lines());

        for (long key : flaggedChunks) {
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            int surfaceY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, cx * 16 + 8, cz * 16 + 8);
            EspRenderUtils.drawBox(pose, lines,
                cx * 16, surfaceY, cz * 16, cx * 16 + 16, surfaceY + 0.2, cz * 16 + 16,
                0f, 0.47f, 1f, 0.8f, 2.0f);
        }

        if (showBlocks) {
            for (BlockPos p : flaggedBlocks) {
                EspRenderUtils.drawBox(pose, lines,
                    p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                    0.2f, 0.7f, 1f, 0.9f, 2.0f);
            }
        }

        matrices.popPose();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##chunkDebugEnabled", isActive())) toggle();
        if (ImGui.checkbox("Show Blocks##chunkDebugBlocks", showBlocks)) {
            showBlocks = !showBlocks;
            Config.setProperty(baseConfig + ".showBlocks", String.valueOf(showBlocks));
        }
        int[] r = {radius};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Radius##chunkDebugRadius", r, 2, 32)) {
            radius = r[0];
            Config.setProperty(baseConfig + ".radius", String.valueOf(radius));
        }
        ImGui.text("Chunks: " + flaggedChunks.size() + "  Blocks: " + flaggedBlocks.size());
        ImGui.end();
    }
}
