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
 * Finds "old"/base chunks by counting player-activity blocks — cobbled deepslate
 * (never generated naturally), horizontally-rotated deepslate (player-placed),
 * end stone in the overworld, and anomalous amounts of deepslate — and flagging
 * any chunk that exceeds a per-type threshold, drawn as a column. Ported and
 * streamlined from the Meteor addon's chunk-finder (multithreading, notifications,
 * trial-chamber logic and per-block highlighting dropped).
 */
public final class BaseChunkESP extends Module implements Menu {

    public static final BaseChunkESP instance = new BaseChunkESP();

    private static final Predicate<BlockState> TARGETS = s ->
        s.is(Blocks.DEEPSLATE) || s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.END_STONE);

    private boolean detectDeepslate;
    private int deepslateThreshold;
    private boolean detectCobbled;
    private int cobbledThreshold;
    private boolean detectRotated;
    private int rotatedThreshold;
    private boolean detectEndStone;
    private int endStoneThreshold;
    private int chunkRadius;
    private int scanInterval;

    private int scanCounter;
    private volatile List<Long> flagged = List.of();

    private BaseChunkESP() {
        super("chunkfinder", "Chunk Finder", "Flag old/base chunks by deepslate/cobbled/end-stone counts.", "ESP");
        detectDeepslate = Config.getBool(baseConfig + ".detectDeepslate", false);
        deepslateThreshold = Config.getInt(baseConfig + ".deepslateThreshold", 200);
        detectCobbled = Config.getBool(baseConfig + ".detectCobbled", true);
        cobbledThreshold = Config.getInt(baseConfig + ".cobbledThreshold", 1);
        detectRotated = Config.getBool(baseConfig + ".detectRotated", true);
        rotatedThreshold = Config.getInt(baseConfig + ".rotatedThreshold", 1);
        detectEndStone = Config.getBool(baseConfig + ".detectEndStone", true);
        endStoneThreshold = Config.getInt(baseConfig + ".endStoneThreshold", 1);
        chunkRadius = Config.getInt(baseConfig + ".chunkRadius", 12);
        scanInterval = Config.getInt(baseConfig + ".scanInterval", 40);
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
        flagged = List.of();
    }

    private void scan() {
        if (mc.player == null || mc.level == null) return;

        ChunkPos pc = mc.player.chunkPosition();
        List<Long> found = new ArrayList<>();

        for (int cx = pc.x() - chunkRadius; cx <= pc.x() + chunkRadius; cx++) {
            for (int cz = pc.z() - chunkRadius; cz <= pc.z() + chunkRadius; cz++) {
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
        return (detectDeepslate && deepslate >= deepslateThreshold)
            || (detectCobbled && cobbled >= cobbledThreshold)
            || (detectRotated && rotated >= rotatedThreshold)
            || (detectEndStone && endStone >= endStoneThreshold);
    }

    private void render(LevelRenderContext context) {
        List<Long> snapshot = flagged;
        if (!isActive() || snapshot.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        double y0 = mc.level.getMinY();
        double y1 = mc.level.getMinY() + mc.level.getHeight();

        var consumers = context.bufferSource();
        if (consumers == null) return;

        var matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = matrices.last().pose();
        var lines = consumers.getBuffer(RenderTypes.lines());

        for (long key : snapshot) {
            double x0 = ChunkPos.getX(key) << 4;
            double z0 = ChunkPos.getZ(key) << 4;
            EspRenderUtils.drawBox(pose, lines, x0, y0, z0, x0 + 16, y1, z0 + 16, 1f, 0.55f, 0.1f, 0.7f, 2.0f);
        }

        matrices.popPose();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##chunkFinderEnabled", isActive())) toggle();
        ImGui.separatorText("Detect");
        detectDeepslate = boolRow("Deepslate##cfDeep", detectDeepslate, ".detectDeepslate");
        if (detectDeepslate)
            deepslateThreshold = slider("  Deepslate >=##cfDeepT", deepslateThreshold, 1, 2000, ".deepslateThreshold");
        detectCobbled = boolRow("Cobbled Deepslate##cfCob", detectCobbled, ".detectCobbled");
        if (detectCobbled)
            cobbledThreshold = slider("  Cobbled >=##cfCobT", cobbledThreshold, 1, 64, ".cobbledThreshold");
        detectRotated = boolRow("Rotated Deepslate##cfRot", detectRotated, ".detectRotated");
        if (detectRotated)
            rotatedThreshold = slider("  Rotated >=##cfRotT", rotatedThreshold, 1, 64, ".rotatedThreshold");
        detectEndStone = boolRow("End Stone##cfEnd", detectEndStone, ".detectEndStone");
        if (detectEndStone)
            endStoneThreshold = slider("  End Stone >=##cfEndT", endStoneThreshold, 1, 64, ".endStoneThreshold");
        ImGui.separator();
        chunkRadius = slider("Chunk Radius##cfRadius", chunkRadius, 2, 24, ".chunkRadius");
        scanInterval = slider("Scan Interval##cfScan", scanInterval, 10, 200, ".scanInterval");
        ImGui.text("Flagged: " + flagged.size());
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
