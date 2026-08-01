package com.tricrotism.modules.esp;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.world.TickEvent;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Highlights amethyst (buds, clusters and budding amethyst) in loaded chunks —
 * useful for locating geodes. Ported from the Meteor addon's AmethystESP,
 * streamlined: a throttled chunk-section scan (using the palette {@code maybeHas}
 * fast-path) with an immutable snapshot swapped to the render thread. The per-tier
 * colouring / sticky-timeout of the original are dropped.
 */
public final class AmethystESP extends Module implements Menu {

    public static final AmethystESP instance = new AmethystESP();

    private static final Predicate<BlockState> IS_AMETHYST = s ->
        s.is(Blocks.SMALL_AMETHYST_BUD) || s.is(Blocks.MEDIUM_AMETHYST_BUD)
            || s.is(Blocks.LARGE_AMETHYST_BUD) || s.is(Blocks.AMETHYST_CLUSTER)
            || s.is(Blocks.BUDDING_AMETHYST);

    private int chunkRadius;
    private int minY;
    private int maxY;
    private int scanInterval;

    private int scanCounter;
    private volatile List<BlockPos> results = List.of();

    private AmethystESP() {
        super("amethystesp", "Amethyst ESP", "Highlight amethyst in loaded chunks.", "ESP");
        chunkRadius = Config.getInt(baseConfig + ".chunkRadius", 8);
        minY = Config.getInt(baseConfig + ".minY", -64);
        maxY = Config.getInt(baseConfig + ".maxY", 64);
        scanInterval = Config.getInt(baseConfig + ".scanInterval", 20);
        LevelRenderEvents.BEFORE_GIZMOS.register(this::render);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;
        if (++scanCounter < scanInterval) return;
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

        for (int cx = pc.x() - chunkRadius; cx <= pc.x() + chunkRadius; cx++) {
            for (int cz = pc.z() - chunkRadius; cz <= pc.z() + chunkRadius; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = mc.level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                int baseX = cx << 4;
                int baseZ = cz << 4;

                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(IS_AMETHYST)) continue;
                    int sectionBaseY = levelMinY + i * 16;
                    if (sectionBaseY + 15 < minY || sectionBaseY > maxY) continue;

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                int worldY = sectionBaseY + y;
                                if (worldY < minY || worldY > maxY) continue;
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

        var consumers = context.bufferSource();
        if (consumers == null) return;

        var matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = matrices.last().pose();
        var lines = consumers.getBuffer(RenderTypes.lines());

        for (BlockPos p : snapshot) {
            EspRenderUtils.drawBox(pose, lines,
                p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                0.72f, 0.42f, 0.92f, 0.8f, 2.0f);
        }

        matrices.popPose();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##amethystEspEnabled", isActive())) toggle();

        chunkRadius = slider("Chunk Radius##amChunkRadius", chunkRadius, 2, 16, ".chunkRadius");
        minY = slider("Min Y##amMinY", minY, -64, 320, ".minY");
        maxY = slider("Max Y##amMaxY", maxY, -64, 320, ".maxY");
        scanInterval = slider("Scan Interval (ticks)##amScan", scanInterval, 5, 100, ".scanInterval");

        ImGui.text("Found: " + results.size());
        ImGui.end();
    }

    private int slider(String id, int value, int min, int max, String key) {
        int[] v = {value};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt(id, v, min, max)) {
            value = v[0];
            Config.setProperty(baseConfig + key, String.valueOf(value));
        }
        return value;
    }
}
