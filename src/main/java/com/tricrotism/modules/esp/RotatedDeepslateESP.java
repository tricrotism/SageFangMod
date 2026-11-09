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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;

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

    /**
     * The exact states worth flagging: horizontal-axis states of the included blocks. Testing the
     * palette against these rather than against any deepslate skips every section of natural,
     * Y-axis deepslate, which is most of the world below Y 0, without visiting a block of it.
     */
    private Set<BlockState> targets() {
        Set<BlockState> states = Collections.newSetFromMap(new IdentityHashMap<>());
        addHorizontal(states, includeRegular.get(), Blocks.DEEPSLATE);
        addHorizontal(states, includePolished.get(), Blocks.POLISHED_DEEPSLATE);
        addHorizontal(states, includeBricks.get(), Blocks.DEEPSLATE_BRICKS);
        addHorizontal(states, includeTiles.get(), Blocks.DEEPSLATE_TILES);
        addHorizontal(states, includeChiseled.get(), Blocks.CHISELED_DEEPSLATE);
        return states;
    }

    private static void addHorizontal(Set<BlockState> states, boolean include, Block block) {
        if (!include) return;
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            if (state.hasProperty(BlockStateProperties.AXIS)
                && state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
                states.add(state);
            }
        }
    }

    private void scan() {
        if (mc.player == null || mc.level == null) return;

        Set<BlockState> targets = targets();
        if (targets.isEmpty()) {
            results = List.of();
            return;
        }

        ChunkPos pc = mc.player.chunkPosition();
        int levelMinY = mc.level.getMinY();
        int radius = chunkRadius.get();
        int lowY = minY.get();
        int highY = maxY.get();
        List<BlockPos> found = new ArrayList<>();

        for (int cx = pc.x() - radius; cx <= pc.x() + radius; cx++) {
            for (int cz = pc.z() - radius; cz <= pc.z() + radius; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = mc.level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                int baseX = cx << 4;
                int baseZ = cz << 4;

                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(targets::contains)) continue;
                    int sectionBaseY = levelMinY + i * 16;
                    if (sectionBaseY + 15 < lowY || sectionBaseY > highY) continue;

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                int worldY = sectionBaseY + y;
                                if (worldY < lowY || worldY > highY) continue;
                                if (targets.contains(section.getBlockState(x, y, z))) {
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
        ImGui.textUnformatted("Found: " + results.size());
    }
}
