package com.tricrotism.modules.esp;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.utils.EspRenderUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights blocks that receive server block-update packets: a red box for each
 * recent update and a green box on the most recent. Updates within the exclusion
 * range around the player are ignored. Ported from the Meteor addon's
 * block-update-highlighter (Meteor's Renderer3D replaced with SageFang's
 * {@code LevelRenderEvents} + {@code EspRenderUtils}).
 */
public final class BlockUpdateESP extends Module {

    public static final BlockUpdateESP instance = new BlockUpdateESP();

    private final Settings.Int exclusionRange =
        integer("Exclusion Range", "exclusionRange", "Ignore updates within this many blocks", 8, 0, 64);

    private static final long DISPLAY_MS = 2_000L;

    private final Map<BlockPos, Long> updated = new ConcurrentHashMap<>();
    private volatile BlockPos lastUpdated;

    private BlockUpdateESP() {
        super("blockupdatehighlighter", "Block Update ESP", "Highlight blocks the server updates.", Category.RENDER);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::render);
    }

    /**
     * Called from ConnectionMixin on the network thread when a block update arrives.
     */
    public void onBlockUpdate(ClientboundBlockUpdatePacket packet) {
        if (!isActive()) return;
        BlockPos pos = packet.getPos().immutable();
        updated.put(pos, System.currentTimeMillis());
        lastUpdated = pos;
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        updated.clear();
        lastUpdated = null;
    }

    @Override
    public void onDeactivate() {
        updated.clear();
        lastUpdated = null;
    }

    private void render(LevelRenderContext context) {
        if (!isActive() || updated.isEmpty()) return;

        long now = System.currentTimeMillis();
        updated.values().removeIf(t -> now - t > DISPLAY_MS);
        if (updated.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BlockPos last = lastUpdated;
        List<BlockPos> snapshot = new ArrayList<>(updated.size());
        for (BlockPos pos : updated.keySet()) {
            if (inRange(mc, pos)) snapshot.add(pos);
        }
        if (snapshot.isEmpty()) return;

        EspRenderUtils.submitBoxes(context, sink -> {
            for (BlockPos pos : snapshot) {
                boolean isLast = pos.equals(last);
                float r = isLast ? 0f : 1f;
                float g = isLast ? 1f : 0f;
                sink.box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                    r, g, 0f, 0.7f, 2.0f);
            }
        });
    }

    private boolean inRange(Minecraft mc, BlockPos pos) {
        if (exclusionRange.get() <= 0) return true;
        double dx = Math.abs(mc.player.getX() - (pos.getX() + 0.5));
        double dy = Math.abs(mc.player.getY() - (pos.getY() + 0.5));
        double dz = Math.abs(mc.player.getZ() - (pos.getZ() + 0.5));
        return dx > exclusionRange.get() || dy > exclusionRange.get() || dz > exclusionRange.get();
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Tracked: " + updated.size());
    }
}
