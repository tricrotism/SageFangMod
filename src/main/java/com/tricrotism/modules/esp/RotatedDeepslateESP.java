package com.tricrotism.modules.esp;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.EspRenderUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Highlights deepslate (and its variants) whose pillar axis is horizontal, meaning
 * player-placed, since natural deepslate always generates on the Y axis. A
 * base-finding aid. Ported from the Meteor addon's rotated-deepslate-esp,
 * streamlined onto the shared block-scan pattern (tracers / threading / chat
 * feedback dropped).
 */
public final class RotatedDeepslateESP extends Module {

    public static final RotatedDeepslateESP instance = new RotatedDeepslateESP();

    private final Settings.Bool includeRegular = bool("Deepslate", "regular", "Include plain deepslate", true);
    private final Settings.Bool includePolished = bool("Polished", "polished", "Include polished", true);
    private final Settings.Bool includeBricks = bool("Bricks", "bricks", "Include bricks", true);
    private final Settings.Bool includeTiles = bool("Tiles", "tiles", "Include tiles", true);
    private final Settings.Bool includeChiseled = bool("Chiseled", "chiseled", "Include chiseled", true);
    private final Settings.Int chunkRadius =
        integer("Chunk Radius", "chunkRadius", "Chunk radius to scan", 8, 2, 16);
    private final Settings.Int minY = integer("Min Y", "minY", "Lowest scanned layer", -64, -64, 320);
    private final Settings.Int maxY = integer("Max Y", "maxY", "Highest scanned layer", 320, -64, 320);
    private final Settings.Int scanInterval =
        integer("Scan Interval", "scanInterval", "Ticks between rescans", 20, 5, 100);

    private static final Predicate<BlockState> ANY_DEEPSLATE = s ->
        s.is(Blocks.DEEPSLATE) || s.is(Blocks.POLISHED_DEEPSLATE) || s.is(Blocks.DEEPSLATE_BRICKS)
            || s.is(Blocks.DEEPSLATE_TILES) || s.is(Blocks.CHISELED_DEEPSLATE);


    private int scanCounter;
    private volatile List<BlockPos> results = List.of();

    private RotatedDeepslateESP() {
        super("rotateddeepslateesp", "Rotated Deepslate ESP", "Highlight horizontally-placed deepslate (base finder).", Category.RENDER);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;
        if (++scanCounter < scanInterval.get()) return;
        scanCounter = 0;
        scan();
    }

    @Override
    public void onDeactivate() {
        results = List.of();
    }

    private boolean isTarget(BlockState s) {
        if (!s.hasProperty(BlockStateProperties.AXIS)) return false;
        if (s.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) return false;
        if (includeRegular.get() && s.is(Blocks.DEEPSLATE)) return true;
        if (includePolished.get() && s.is(Blocks.POLISHED_DEEPSLATE)) return true;
        if (includeBricks.get() && s.is(Blocks.DEEPSLATE_BRICKS)) return true;
        if (includeTiles.get() && s.is(Blocks.DEEPSLATE_TILES)) return true;
        return includeChiseled.get() && s.is(Blocks.CHISELED_DEEPSLATE);
    }

    private void scan() {
        if (mc.player == null || mc.level == null) return;

        ChunkPos pc = mc.player.chunkPosition();
        int levelMinY = mc.level.getMinY();
        List<BlockPos> found = new ArrayList<>();

        for (int cx = pc.x() - chunkRadius.get(); cx <= pc.x() + chunkRadius.get(); cx++) {
            for (int cz = pc.z() - chunkRadius.get(); cz <= pc.z() + chunkRadius.get(); cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = mc.level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                int baseX = cx << 4;
                int baseZ = cz << 4;

                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(ANY_DEEPSLATE)) continue;
                    int sectionBaseY = levelMinY + i * 16;
                    if (sectionBaseY + 15 < minY.get() || sectionBaseY > maxY.get()) continue;

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                int worldY = sectionBaseY + y;
                                if (worldY < minY.get() || worldY > maxY.get()) continue;
                                if (isTarget(section.getBlockState(x, y, z))) {
                                    found.add(new BlockPos(baseX + x, worldY, baseZ + z));
                                }
                            }
                        }
                    }
                }
            }
        }
        results = found;
    }

    private void render(LevelRenderContext context) {
        List<BlockPos> snapshot = results;
        if (!isActive() || snapshot.isEmpty()) return;

        EspRenderUtils.submitBoxes(context, sink -> {
            for (BlockPos p : snapshot) {
                sink.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                    0.2f, 0.85f, 0.85f, 0.8f, 2.0f);
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Found: " + results.size());
    }
}
