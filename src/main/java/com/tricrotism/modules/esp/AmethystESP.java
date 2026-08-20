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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Highlights amethyst (buds, clusters and budding amethyst) in loaded chunks,
 * which is how you find geodes. Ported from the Meteor addon's AmethystESP,
 * streamlined: a throttled chunk-section scan (using the palette {@code maybeHas}
 * fast-path) with an immutable snapshot swapped to the render thread. The per-tier
 * colouring / sticky-timeout of the original are dropped.
 */
public final class AmethystESP extends Module {

    public static final AmethystESP instance = new AmethystESP();

    private final Settings.Int chunkRadius =
        integer("Chunk Radius", "chunkRadius", "Chunk radius to scan", 8, 2, 16);
    private final Settings.Int minY = integer("Min Y", "minY", "Lowest scanned layer", -64, -64, 320);
    private final Settings.Int maxY = integer("Max Y", "maxY", "Highest scanned layer", 64, -64, 320);
    private final Settings.Int scanInterval =
        integer("Scan Interval (ticks)", "scanInterval", "Ticks between rescans", 20, 5, 100);

    private static final Predicate<BlockState> IS_AMETHYST = s ->
        s.is(Blocks.SMALL_AMETHYST_BUD) || s.is(Blocks.MEDIUM_AMETHYST_BUD)
            || s.is(Blocks.LARGE_AMETHYST_BUD) || s.is(Blocks.AMETHYST_CLUSTER)
            || s.is(Blocks.BUDDING_AMETHYST);


    private int scanCounter;
    private volatile List<BlockPos> results = List.of();

    private AmethystESP() {
        super("amethystesp", "Amethyst ESP", "Highlight amethyst in loaded chunks.", Category.RENDER);
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
                    if (section.hasOnlyAir() || !section.maybeHas(IS_AMETHYST)) continue;
                    int sectionBaseY = levelMinY + i * 16;
                    if (sectionBaseY + 15 < minY.get() || sectionBaseY > maxY.get()) continue;

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                int worldY = sectionBaseY + y;
                                if (worldY < minY.get() || worldY > maxY.get()) continue;
                                if (IS_AMETHYST.test(section.getBlockState(x, y, z))) {
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
                    0.72f, 0.42f, 0.92f, 0.8f, 2.0f);
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Found: " + results.size());
    }
}
