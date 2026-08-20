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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks dark, spawnable spots near the player: air cells over solid ground whose
 * block-light level is at or below the threshold.get(). A bounded, throttled scan around
 * the player using the vanilla light engine. Ported (streamlined) from the Meteor
 * addon's light-level-tracker; the per-chunk packet-light storage and sky-light
 * modes are dropped in favour of a direct light-engine query.
 */
public final class LightLevelESP extends Module {

    public static final LightLevelESP instance = new LightLevelESP();

    private final Settings.Int threshold =
        integer("Light <=", "threshold", "Flag spots at or below this light level", 0, 0, 15);
    private final Settings.Int hRadius = integer("H Radius", "hRadius", "Horizontal scan radius", 24, 4, 48);
    private final Settings.Int vRadius = integer("V Radius", "vRadius", "Vertical scan radius", 8, 1, 24);
    private final Settings.Int scanInterval =
        integer("Scan Interval", "scanInterval", "Ticks between rescans", 20, 5, 60);


    private int scanCounter;
    private volatile List<BlockPos> results = List.of();

    private LightLevelESP() {
        super("lightleveltracker", "Light Level ESP", "Mark dark spawnable spots near you.", Category.RENDER);
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

        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();

        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        List<BlockPos> found = new ArrayList<>();

        for (int dx = -hRadius.get(); dx <= hRadius.get(); dx++) {
            for (int dz = -hRadius.get(); dz <= hRadius.get(); dz++) {
                for (int dy = -vRadius.get(); dy <= vRadius.get(); dy++) {
                    cell.set(px + dx, py + dy, pz + dz);
                    BlockState state = mc.level.getBlockState(cell);
                    if (!state.isAir()) continue;
                    below.set(cell.getX(), cell.getY() - 1, cell.getZ());
                    if (mc.level.getBlockState(below).isAir()) continue;
                    if (mc.level.getBrightness(LightLayer.BLOCK, cell) <= threshold.get()) {
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

        EspRenderUtils.submitBoxes(context, sink -> {
            for (BlockPos p : snapshot) {
                sink.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.03, p.getZ() + 1,
                    1f, 0.15f, 0.15f, 0.8f, 2.0f);
            }
        });
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Spots: " + results.size());
    }
}
