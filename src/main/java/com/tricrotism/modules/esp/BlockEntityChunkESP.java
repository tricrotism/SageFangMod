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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights every block entity (chests, spawners, shulkers, hoppers…) carried in
 * incoming chunk packets, at their exact positions, which is how you spot stashes
 * as chunks stream in. Ported from the Meteor addon's block-entity-chunk-esp; the
 * block-type filter and logging options are dropped for a clean UI.
 */
public final class BlockEntityChunkESP extends Module {

    public static final BlockEntityChunkESP instance = new BlockEntityChunkESP();

    private final Settings.Int chunkRadius =
        integer("Chunk Radius", "chunkRadius", "Forget chunks further away than this", 16, 2, 32);
    private final Settings.Int minBlockEntities =
        integer("Min Block Entities", "minBlockEntities", "Ignore chunks with fewer than this many", 1, 1, 20);

    private final Map<Long, List<BlockPos>> chunkEntities = new ConcurrentHashMap<>();

    // built on the tick, read by the draw call, swapped whole rather than mutated
    private volatile List<BlockPos> positions = List.of();

    private BlockEntityChunkESP() {
        super("blockentitychunkesp", "Block Entity ESP", "Highlight block entities from incoming chunk packets.", Category.RENDER);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> clear());
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
        if (positions.size() < minBlockEntities.get()) return;
        chunkEntities.put(ChunkPos.pack(cx, cz), positions);
    }

    @Override
    public void onActivate() {
        chunkEntities.clear();
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        clear();
    }

    private void clear() {
        chunkEntities.clear();
        positions = List.of();
    }

    @Override
    public void onDeactivate() {
        chunkEntities.clear();
        positions = List.of();
    }

    /**
     * The radius prune and the flatten run once a tick and publish the list the draw call reads.
     * In {@code render} they walked the whole map and copied every tracked position at frame rate,
     * which across a 16 chunk radius is comfortably ten thousand references a frame.
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            positions = List.of();
            return;
        }

        int radius = chunkRadius.get();
        ChunkPos playerChunk = ChunkPos.containing(mc.player.blockPosition());
        chunkEntities.keySet().removeIf(k ->
            Math.abs(ChunkPos.getX(k) - playerChunk.x()) > radius
                || Math.abs(ChunkPos.getZ(k) - playerChunk.z()) > radius);

        if (chunkEntities.isEmpty()) {
            positions = List.of();
            return;
        }

        List<BlockPos> built = new ArrayList<>();
        for (List<BlockPos> chunk : chunkEntities.values()) built.addAll(chunk);

        positions = List.copyOf(built);
    }

    private void render(LevelRenderContext context) {
        List<BlockPos> snapshot = positions;
        if (!isActive() || snapshot.isEmpty()) return;

        EspRenderUtils.submitBoxes(context, sink -> {
            for (BlockPos p : snapshot) {
                sink.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                    1f, 0.78f, 0.2f, 0.7f, 2.0f);
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textUnformatted("Chunks: " + chunkEntities.size());
    }
}
