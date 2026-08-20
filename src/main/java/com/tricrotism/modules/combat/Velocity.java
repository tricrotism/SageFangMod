package com.tricrotism.modules.combat;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Takes less knockback than the server sent.
 * <p>
 * There is no check for this yet, and that is the point of building it. Whiteout already has the
 * hard half in {@code PendingVelocity}, which holds the knockback the server has sent but the client
 * may not have applied and offers both outcomes as candidates until the bracketing transaction comes
 * back. That machinery exists so a prediction engine does not flag an honest player mid-hit; it is
 * also exactly what is needed to notice a player who never took the knockback at all, since the
 * candidate the client ended up matching is observable.
 * <p>
 * The scale is a percentage rather than a switch because the detectable case and the useful case are
 * different. Zero is trivial to catch once anything looks, and no serious client ships it; the
 * region worth tuning against is the high end, where enough knockback is taken to look normal and
 * enough is shed to win the fight.
 * <p>
 * Only the motion packet is touched. Explosion knockback arrives on its own packet and is left
 * alone, so a run stays about one mechanism.
 */
public final class Velocity extends Module {

    public static final Velocity instance = new Velocity();

    private final Settings.Int horizontal =
        integer("Horizontal (%)", "horizontal", "Share of the sent horizontal knockback to keep", 0, 0, 100);
    private final Settings.Int vertical =
        integer("Vertical (%)", "vertical", "Share of the sent vertical knockback to keep", 0, 0, 100);

    private int modified;

    private Velocity() {
        super("velocity", "Velocity", "Take reduced knockback; nothing checks this yet.",
            Category.COMBAT);
    }

    /**
     * Called on the network thread. Returns true when the caller should drop the packet, having
     * already scheduled the scaled version.
     */
    public boolean captureInbound(Packet<?> packet) {
        if (!isActive() || !(packet instanceof ClientboundSetEntityMotionPacket(int id1, Vec3 sent))) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || id1 != mc.player.getId()) return false;

        Vec3 kept = new Vec3(
            sent.x * horizontal.get() / 100.0,
            sent.y * vertical.get() / 100.0,
            sent.z * horizontal.get() / 100.0);
        modified++;

        TestLog.event("velocity",
            "sentX", sent.x, "sentY", sent.y, "sentZ", sent.z,
            "keptX", kept.x, "keptY", kept.y, "keptZ", kept.z,
            "horizontalPercent", horizontal.get(),
            "verticalPercent", vertical.get());

        // Applied on the client thread: the packet handler this replaces would have run there, and
        // player motion is not ours to touch from netty.
        mc.execute(() -> {
            if (mc.player != null) mc.player.setDeltaMovement(kept);
        });
        return true;
    }

    @Override
    public void onActivate() {
        modified = 0;
        TestLog.event("velocity_enable", "horizontal", horizontal.get(), "vertical", vertical.get());
    }

    @Override
    public void onDeactivate() {
        TestLog.event("velocity_disable", "modified", modified);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Knockbacks reduced: " + modified);
    }
}
