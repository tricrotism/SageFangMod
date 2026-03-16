package com.tricrotism.modules.blink;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a frozen "ghost" of the player at the position Blink was activated,
 * plus a thin breadcrumb trail showing the path taken while packets are held.
 * <p>
 * The ghost is a real {@link RemotePlayer} entity added client-side only — it
 * renders through the normal entity pipeline (skin, armor, pose) with zero
 * extra draw code. The breadcrumb trail is drawn as colored line segments
 * in {@link WorldRenderEvents#BEFORE_DEBUG_RENDER}.
 */
public final class BlinkRenderer {

    // Breadcrumb recording — one position per tick, capped at 30 seconds
    private static final int MAX_BREADCRUMBS = 600;

    // Fake player entity ID — well outside the server-assigned range
    private static final int FAKE_ENTITY_ID = Integer.MAX_VALUE - 7;

    // Trail colors (RGBA) — gradient from green (start) to red (end)
    private static final float TRAIL_ALPHA = 0.7f;

    private final List<Vec3> breadcrumbs = new ArrayList<>();
    private RemotePlayer fakePlayer;

    // ── lifecycle ────────────────────────────────────────────────────

    public void onActivate() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 startPos = mc.player.position();
        breadcrumbs.clear();
        breadcrumbs.add(startPos);

        spawnFakePlayer(mc, startPos);
    }

    public void onTick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 pos = mc.player.position();
        // Only record if we actually moved (avoids clutter when standing still)
        if (breadcrumbs.isEmpty() || breadcrumbs.getLast().distanceToSqr(pos) > 0.01) {
            if (breadcrumbs.size() < MAX_BREADCRUMBS) {
                breadcrumbs.add(pos);
            }
        }
    }

    public void onDeactivate() {
        removeFakePlayer();
        breadcrumbs.clear();
    }

    // ── fake player ─────────────────────────────────────────────────

    private void spawnFakePlayer(Minecraft mc, Vec3 pos) {
        removeFakePlayer(); // clean up any stale ghost

        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        fakePlayer = new RemotePlayer(level, mc.player.getGameProfile());
        fakePlayer.setId(FAKE_ENTITY_ID);
        fakePlayer.setPos(pos);
        fakePlayer.setYRot(mc.player.getYRot());
        fakePlayer.setXRot(mc.player.getXRot());
        fakePlayer.setYHeadRot(mc.player.getYHeadRot());
        fakePlayer.setYBodyRot(mc.player.yBodyRot);

        // Copy inventory so the ghost wears the same gear
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            fakePlayer.getInventory().setItem(i, mc.player.getInventory().getItem(i).copy());
        }

        level.addEntity(fakePlayer);
    }

    private void removeFakePlayer() {
        if (fakePlayer != null) {
            fakePlayer.discard();
            fakePlayer = null;
        }
    }

    // ── breadcrumb trail rendering ──────────────────────────────────

    /**
     * Register this once during mod init. Draws the breadcrumb trail
     * as colored line segments in world space.
     */
    public void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderTrail);
    }

    private void renderTrail(WorldRenderContext context) {
        if (!Blink.instance.isActive() || breadcrumbs.size() < 2) return;

        var consumers = context.consumers();
        if (consumers == null) return;

        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = matrices.last().pose();

        VertexConsumer lines = consumers.getBuffer(RenderType.lines());
        int total = breadcrumbs.size();

        for (int i = 0; i < total - 1; i++) {
            Vec3 from = breadcrumbs.get(i);
            Vec3 to = breadcrumbs.get(i + 1);

            // Gradient: green at start → red at end
            float t = (float) i / (total - 1);
            float r = t;
            float g = 1.0f - t;
            float b = 0.0f;

            // Offset Y slightly above feet so the line is visible on the ground
            float y1 = (float) from.y + 0.05f;
            float y2 = (float) to.y + 0.05f;

            // Direction vector for the normal (required by RenderType.lines())
            float dx = (float) (to.x - from.x);
            float dy = y2 - y1;
            float dz = (float) (to.z - from.z);
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-6f) continue;
            float nx = dx / len;
            float ny = dy / len;
            float nz = dz / len;

            lines.addVertex(pose, (float) from.x, y1, (float) from.z)
                .setColor(r, g, b, TRAIL_ALPHA)
                .setNormal(nx, ny, nz);
            lines.addVertex(pose, (float) to.x, y2, (float) to.z)
                .setColor(r, g, b, TRAIL_ALPHA)
                .setNormal(nx, ny, nz);
        }

        matrices.popPose();
    }
}
