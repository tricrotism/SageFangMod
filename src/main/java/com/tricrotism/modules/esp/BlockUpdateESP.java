package com.tricrotism.modules.esp;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.utils.EspRenderUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights blocks that receive server block-update packets: a red box for each
 * recent update and a green box on the most recent. Updates within the exclusion
 * range around the player are ignored. Ported from the Meteor addon's
 * block-update-highlighter (Meteor's Renderer3D replaced with SageFang's
 * {@code LevelRenderEvents} + {@code EspRenderUtils}).
 */
public final class BlockUpdateESP extends Module implements Menu {

    public static final BlockUpdateESP instance = new BlockUpdateESP();

    private static final long DISPLAY_MS = 2_000L;

    private final Map<BlockPos, Long> updated = new ConcurrentHashMap<>();
    private volatile BlockPos lastUpdated;
    private int exclusionRange;

    private BlockUpdateESP() {
        super("blockupdatehighlighter", "Block Update ESP", "Highlight blocks the server updates.", "ESP");
        exclusionRange = Config.getInt(baseConfig + ".exclusionRange", 8);
        LevelRenderEvents.BEFORE_GIZMOS.register(this::render);
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

        var consumers = context.bufferSource();
        if (consumers == null) return;

        var matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = matrices.last().pose();
        var lines = consumers.getBuffer(RenderTypes.lines());

        BlockPos last = lastUpdated;
        for (BlockPos pos : updated.keySet()) {
            if (!inRange(mc, pos)) continue;
            boolean isLast = pos.equals(last);
            float r = isLast ? 0f : 1f;
            float g = isLast ? 1f : 0f;
            EspRenderUtils.drawBox(pose, lines,
                pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                r, g, 0f, 0.7f, 2.0f);
        }

        matrices.popPose();
    }

    private boolean inRange(Minecraft mc, BlockPos pos) {
        if (exclusionRange <= 0) return true;
        double dx = Math.abs(mc.player.getX() - (pos.getX() + 0.5));
        double dy = Math.abs(mc.player.getY() - (pos.getY() + 0.5));
        double dz = Math.abs(mc.player.getZ() - (pos.getZ() + 0.5));
        return dx > exclusionRange || dy > exclusionRange || dz > exclusionRange;
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);
        if (ImGui.checkbox("Enabled##blockUpdateEnabled", isActive())) toggle();
        int[] ex = {exclusionRange};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Exclusion Range##buExclusion", ex, 0, 64)) {
            exclusionRange = ex[0];
            Config.setProperty(baseConfig + ".exclusionRange", String.valueOf(exclusionRange));
        }
        ImGui.text("Tracked: " + updated.size());
        ImGui.end();
    }
}
