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
import net.minecraft.client.Minecraft;
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
 * Finds "old"/base chunks by counting player-activity blocks: cobbled deepslate
 * (never generated naturally), horizontally-rotated deepslate (player-placed),
 * end stone in the overworld, and anomalous amounts of deepslate. Any chunk that
 * exceeds a per-type threshold is flagged and drawn as a column. Ported and
 * streamlined from the Meteor addon's chunk-finder (multithreading, notifications,
 * trial-chamber logic and per-block highlighting dropped).
 */
public final class BaseChunkESP extends Module {

    public static final BaseChunkESP instance = new BaseChunkESP();

    private final Settings.Bool detectDeepslate =
        bool("Deepslate", "detectDeepslate", "Flag chunks by deepslate count", false);
    private final Settings.Int deepslateThreshold =
        integer("Deepslate >=", "deepslateThreshold", "Blocks needed to flag", 200, 1, 2000);
    private final Settings.Bool detectCobbled =
        bool("Cobbled Deepslate", "detectCobbled", "Flag chunks by cobbled deepslate", true);
    private final Settings.Int cobbledThreshold =
        integer("Cobbled >=", "cobbledThreshold", "Blocks needed to flag", 1, 1, 64);
    private final Settings.Bool detectRotated =
        bool("Rotated Deepslate", "detectRotated", "Flag chunks by player-placed deepslate", true);
    private final Settings.Int rotatedThreshold =
        integer("Rotated >=", "rotatedThreshold", "Blocks needed to flag", 1, 1, 64);
    private final Settings.Bool detectEndStone =
        bool("End Stone", "detectEndStone", "Flag chunks by end stone", true);
    private final Settings.Int endStoneThreshold =
        integer("End Stone >=", "endStoneThreshold", "Blocks needed to flag", 1, 1, 64);
    private final Settings.Int chunkRadius =
        integer("Chunk Radius", "chunkRadius", "Chunk radius to scan", 12, 2, 24);
    private final Settings.Int scanInterval =
        integer("Scan Interval", "scanInterval", "Ticks between rescans", 40, 10, 200);

    private static final Predicate<BlockState> TARGETS = s ->
        s.is(Blocks.DEEPSLATE) || s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.END_STONE);


    private int scanCounter;
    private volatile List<Long> flagged = List.of();

    private BaseChunkESP() {
        super("chunkfinder", "Chunk Finder", "Flag old/base chunks by deepslate/cobbled/end-stone counts.", Category.RENDER);
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
        flagged = List.of();
    }

    private void scan() {
        if (mc.player == null || mc.level == null) return;

        ChunkPos pc = mc.player.chunkPosition();
        List<Long> found = new ArrayList<>();

        for (int cx = pc.x() - chunkRadius.get(); cx <= pc.x() + chunkRadius.get(); cx++) {
            for (int cz = pc.z() - chunkRadius.get(); cz <= pc.z() + chunkRadius.get(); cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                if (isFlagged(mc.level.getChunk(cx, cz))) found.add(ChunkPos.pack(cx, cz));
            }
        }
        flagged = found;
    }

    private boolean isFlagged(LevelChunk chunk) {
        int deepslate = 0, cobbled = 0, rotated = 0, endStone = 0;
        LevelChunkSection[] sections = chunk.getSections();

        for (LevelChunkSection section : sections) {
            if (section.hasOnlyAir() || !section.maybeHas(TARGETS)) continue;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        BlockState s = section.getBlockState(x, y, z);
                        if (s.is(Blocks.DEEPSLATE)) {
                            deepslate++;
                            if (s.hasProperty(BlockStateProperties.AXIS)
                                && s.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
                                rotated++;
                            }
                        } else if (s.is(Blocks.COBBLED_DEEPSLATE)) {
                            cobbled++;
                        } else if (s.is(Blocks.END_STONE)) {
                            endStone++;
                        }
                    }
                }
            }
            if (flagged(deepslate, cobbled, rotated, endStone)) return true;
        }
        return flagged(deepslate, cobbled, rotated, endStone);
    }

    private boolean flagged(int deepslate, int cobbled, int rotated, int endStone) {
        return (detectDeepslate.get() && deepslate >= deepslateThreshold.get())
            || (detectCobbled.get() && cobbled >= cobbledThreshold.get())
            || (detectRotated.get() && rotated >= rotatedThreshold.get())
            || (detectEndStone.get() && endStone >= endStoneThreshold.get());
    }

    private void render(LevelRenderContext context) {
        List<Long> snapshot = flagged;
        if (!isActive() || snapshot.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        double y0 = mc.level.getMinY();
        double y1 = mc.level.getMinY() + mc.level.getHeight();

        EspRenderUtils.submitBoxes(context, sink -> {
            for (long key : snapshot) {
                double x0 = ChunkPos.getX(key) << 4;
                double z0 = ChunkPos.getZ(key) << 4;
                sink.box(x0, y0, z0, x0 + 16, y1, z0 + 16, 1f, 0.55f, 0.1f, 0.7f, 2.0f);
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Flagged: " + flagged.size());
    }
}
