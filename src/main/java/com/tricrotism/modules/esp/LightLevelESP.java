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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks dark, spawnable spots near the player: air cells over solid ground whose
 * block-light level is at or below the threshold. A bounded, throttled scan around
 * the player using the vanilla light engine. Ported (streamlined) from the Meteor
 * addon's light-level-tracker; the per-chunk packet-light storage and sky-light
 * modes are dropped in favour of a direct light-engine query.
 */
public final class LightLevelESP extends Module implements Menu {

    public static final LightLevelESP instance = new LightLevelESP();

    private int threshold;
    private int hRadius;
    private int vRadius;
    private int scanInterval;

    private int scanCounter;
    private volatile List<BlockPos> results = List.of();

    private LightLevelESP() {
        super("lightleveltracker", "Light Level ESP", "Mark dark spawnable spots near you.", "Visual");
        threshold = Config.getInt(baseConfig + ".threshold", 0);
        hRadius = Config.getInt(baseConfig + ".hRadius", 24);
        vRadius = Config.getInt(baseConfig + ".vRadius", 8);
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

        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();

        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        List<BlockPos> found = new ArrayList<>();

        for (int dx = -hRadius; dx <= hRadius; dx++) {
            for (int dz = -hRadius; dz <= hRadius; dz++) {
                for (int dy = -vRadius; dy <= vRadius; dy++) {
                    cell.set(px + dx, py + dy, pz + dz);
                    BlockState state = mc.level.getBlockState(cell);
                    if (!state.isAir()) continue;
                    below.set(cell.getX(), cell.getY() - 1, cell.getZ());
                    if (mc.level.getBlockState(below).isAir()) continue;
                    if (mc.level.getBrightness(LightLayer.BLOCK, cell) <= threshold) {
                        found.add(cell.immutable());
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
                p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.03, p.getZ() + 1,
                1f, 0.15f, 0.15f, 0.8f, 2.0f);
        }

        matrices.popPose();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##lightLevelEnabled", isActive())) toggle();

        threshold = slider("Light <=##llThreshold", threshold, 0, 15, ".threshold");
        hRadius = slider("H Radius##llHRadius", hRadius, 4, 48, ".hRadius");
        vRadius = slider("V Radius##llVRadius", vRadius, 1, 24, ".vRadius");
        scanInterval = slider("Scan Interval##llScan", scanInterval, 5, 60, ".scanInterval");

        ImGui.text("Spots: " + results.size());
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
