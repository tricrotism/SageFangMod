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
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Highlights deepslate (and its variants) whose pillar axis is horizontal — i.e.
 * player-placed, since natural deepslate always generates on the Y axis. A
 * base-finding aid. Ported from the Meteor addon's rotated-deepslate-esp,
 * streamlined onto the shared block-scan pattern (tracers / threading / chat
 * feedback dropped).
 */
public final class RotatedDeepslateESP extends Module implements Menu {

    public static final RotatedDeepslateESP instance = new RotatedDeepslateESP();

    private static final Predicate<BlockState> ANY_DEEPSLATE = s ->
        s.is(Blocks.DEEPSLATE) || s.is(Blocks.POLISHED_DEEPSLATE) || s.is(Blocks.DEEPSLATE_BRICKS)
            || s.is(Blocks.DEEPSLATE_TILES) || s.is(Blocks.CHISELED_DEEPSLATE);

    private boolean includeRegular;
    private boolean includePolished;
    private boolean includeBricks;
    private boolean includeTiles;
    private boolean includeChiseled;
    private int chunkRadius;
    private int minY;
    private int maxY;
    private int scanInterval;

    private int scanCounter;
    private volatile List<BlockPos> results = List.of();

    private RotatedDeepslateESP() {
        super("rotateddeepslateesp", "Rotated Deepslate ESP", "Highlight horizontally-placed deepslate (base finder).", "ESP");
        includeRegular = Config.getBool(baseConfig + ".regular", true);
        includePolished = Config.getBool(baseConfig + ".polished", true);
        includeBricks = Config.getBool(baseConfig + ".bricks", true);
        includeTiles = Config.getBool(baseConfig + ".tiles", true);
        includeChiseled = Config.getBool(baseConfig + ".chiseled", true);
        chunkRadius = Config.getInt(baseConfig + ".chunkRadius", 8);
        minY = Config.getInt(baseConfig + ".minY", -64);
        maxY = Config.getInt(baseConfig + ".maxY", 320);
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

    private boolean isTarget(BlockState s) {
        if (!s.hasProperty(BlockStateProperties.AXIS)) return false;
        if (s.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) return false;
        if (includeRegular && s.is(Blocks.DEEPSLATE)) return true;
        if (includePolished && s.is(Blocks.POLISHED_DEEPSLATE)) return true;
        if (includeBricks && s.is(Blocks.DEEPSLATE_BRICKS)) return true;
        if (includeTiles && s.is(Blocks.DEEPSLATE_TILES)) return true;
        return includeChiseled && s.is(Blocks.CHISELED_DEEPSLATE);
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
                    if (section.hasOnlyAir() || !section.maybeHas(ANY_DEEPSLATE)) continue;
                    int sectionBaseY = levelMinY + i * 16;
                    if (sectionBaseY + 15 < minY || sectionBaseY > maxY) continue;

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                int worldY = sectionBaseY + y;
                                if (worldY < minY || worldY > maxY) continue;
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
                0.2f, 0.85f, 0.85f, 0.8f, 2.0f);
        }

        matrices.popPose();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##rotDeepEnabled", isActive())) toggle();
        ImGui.separatorText("Include");
        includeRegular = boolRow("Deepslate##rdRegular", includeRegular, ".regular");
        includePolished = boolRow("Polished##rdPolished", includePolished, ".polished");
        includeBricks = boolRow("Bricks##rdBricks", includeBricks, ".bricks");
        includeTiles = boolRow("Tiles##rdTiles", includeTiles, ".tiles");
        includeChiseled = boolRow("Chiseled##rdChiseled", includeChiseled, ".chiseled");
        ImGui.separator();
        chunkRadius = slider("Chunk Radius##rdChunkRadius", chunkRadius, 2, 16, ".chunkRadius");
        minY = slider("Min Y##rdMinY", minY, -64, 320, ".minY");
        maxY = slider("Max Y##rdMaxY", maxY, -64, 320, ".maxY");
        scanInterval = slider("Scan Interval##rdScan", scanInterval, 5, 100, ".scanInterval");
        ImGui.text("Found: " + results.size());
        ImGui.end();
    }

    private boolean boolRow(String id, boolean value, String key) {
        if (ImGui.checkbox(id, value)) {
            value = !value;
            Config.setProperty(baseConfig + key, String.valueOf(value));
        }
        return value;
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
