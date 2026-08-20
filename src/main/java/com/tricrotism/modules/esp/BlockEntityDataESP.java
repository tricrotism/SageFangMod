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
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flags chunks and block positions that receive {@code ClientboundBlockEntityDataPacket}s.
 * That is how you spot block entities (and thus bases) the server updates outside your
 * normal view. Flagged chunks get a surface marker, flagged blocks a box. Ported
 * from the Meteor addon's chunk-debug (the full grey chunk grid is dropped; only
 * flagged chunks/blocks are drawn).
 */
public final class BlockEntityDataESP extends Module {

    public static final BlockEntityDataESP instance = new BlockEntityDataESP();

    private final Settings.Int radius =
        integer("Radius", "radius", "Chunk radius to keep flags for", 16, 2, 32);
    private final Settings.Bool showBlocks =
        bool("Show Blocks", "showBlocks", "Also box the individual block entities", true);

    private final Set<Long> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> flaggedBlocks = ConcurrentHashMap.newKeySet();

    private BlockEntityDataESP() {
        super("chunkdebug", "Chunk Debug", "Flag chunks/blocks that get block-entity-data packets.", Category.RENDER);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
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

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        flaggedChunks.clear();
        flaggedBlocks.clear();
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
        flaggedChunks.removeIf(k -> Math.abs(ChunkPos.getX(k) - pc.x()) > radius.get() || Math.abs(ChunkPos.getZ(k) - pc.z()) > radius.get());
        flaggedBlocks.removeIf(p -> Math.abs((p.getX() >> 4) - pc.x()) > radius.get() || Math.abs((p.getZ() >> 4) - pc.z()) > radius.get());

        List<int[]> chunkBoxes = new ArrayList<>(flaggedChunks.size());
        for (long key : flaggedChunks) {
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            chunkBoxes.add(new int[]{cx, cz, mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, cx * 16 + 8, cz * 16 + 8)});
        }
        List<BlockPos> blocks = showBlocks.get() ? new ArrayList<>(flaggedBlocks) : List.of();
        if (chunkBoxes.isEmpty() && blocks.isEmpty()) return;

        EspRenderUtils.submitBoxes(context, sink -> {
            for (int[] box : chunkBoxes) {
                int x0 = box[0] * 16;
                int z0 = box[1] * 16;
                sink.box(x0, box[2], z0, x0 + 16, box[2] + 0.2, z0 + 16, 0f, 0.47f, 1f, 0.8f, 2.0f);
            }

            for (BlockPos p : blocks) {
                sink.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                    0.2f, 0.7f, 1f, 0.9f, 2.0f);
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Chunks: " + flaggedChunks.size() + "  Blocks: " + flaggedBlocks.size());
    }
}
