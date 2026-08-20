package com.tricrotism.modules.combat;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.rotation.RotationManager;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Attacks distant targets by claiming to stand next to them and then claiming to come back.
 * <p>
 * This is the interesting one for a rewinding server, because it defeats reach without ever
 * exceeding it. Every attack is sent from a position that genuinely is within arm's length of the
 * victim, so a distance check measures a legal hit and a rotation check sees a rotation pointing
 * straight at them. Nothing about the interaction itself is wrong. What is wrong is the travel: two
 * position claims per attack, tens of blocks apart, inside a single tick.
 * <p>
 * That makes it purely a movement problem, and therefore invisible to everything currently shipped.
 * A prediction engine catches it on the first hop because no sequence of legal ticks covers the
 * distance; so would a far cruder test on the delta between consecutive position claims. Which of
 * those you want is worth deciding deliberately, and this module is how you compare them.
 * <p>
 * The client's own position is never changed, only the packets, so the camera stays put and there is
 * no rubber-banding to fight. The return claim is sent in the same tick and the real movement packet
 * follows it as usual, so the server's view converges again immediately.
 */
public final class TPAura extends Module {

    public static final TPAura instance = new TPAura();

    /**
     * Above the aura, since a hop only makes sense pointed at the thing it hopped to.
     */
    private static final int ROTATION_PRIORITY = 130;

    private final Settings.Decimal range =
        decimal("Range", "range", "How far a target may be to hop to it", 12.0, 3.0, 40.0);
    private final Settings.Decimal standoff =
        decimal("Standoff", "standoff", "Distance to claim to stand at from the target", 2.0, 0.5, 3.0);
    private final Settings.Int delayTicks =
        integer("Delay (ticks)", "delay", "Ticks between hops", 4, 1, 20);
    private final Settings.Bool rotate =
        bool("Server Rotations", "rotate", "Point the transmitted rotation at the target", true);

    private int cooldown;
    private int hops;
    private double lastDistance;

    private TPAura() {
        super("tpaura", "TP Aura", "Hop to distant targets by packet and attack; a movement problem.",
            Category.COMBAT);
    }

    @Override
    public void onActivate() {
        hops = 0;
        cooldown = 0;
        TestLog.event("tpaura_enable", "range", range.get(), "standoff", standoff.get(),
            "delayTicks", delayTicks.get());
    }

    @Override
    public void onDeactivate() {
        TestLog.event("tpaura_disable", "hops", hops);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.getConnection() == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        Entity target = findTarget();
        if (target == null) return;

        Vec3 real = mc.player.position();
        Vec3 aim = target.getEyePosition();
        Vec3 stand = standPosition(real, target);
        lastDistance = real.distanceTo(stand);

        if (rotate.get()) {
            Vec3 eye = stand.add(0.0, mc.player.getEyeHeight(), 0.0);
            RotationManager.request(RotationManager.yawTo(eye, aim), RotationManager.pitchTo(eye, aim),
                ROTATION_PRIORITY);
        }

        boolean onGround = mc.player.onGround();
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(stand, onGround, false));
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(real, onGround, false));

        cooldown = delayTicks.get();
        hops++;

        TestLog.event("tpaura_hop",
            "targetId", target.getId(),
            "hopDistance", lastDistance,
            "standX", stand.x, "standY", stand.y, "standZ", stand.z,
            "realX", real.x, "realY", real.y, "realZ", real.z,
            "rotated", rotate.get());
    }

    /**
     * A point {@link #standoff} blocks from the target, on the line back toward where we really are.
     */
    private Vec3 standPosition(Vec3 real, Entity target) {
        Vec3 targetFeet = target.position();
        Vec3 toUs = real.subtract(targetFeet);
        double length = toUs.length();
        if (length < 1.0E-4) return targetFeet;
        return targetFeet.add(toUs.scale(standoff.get() / length));
    }

    private Entity findTarget() {
        Vec3 eye = mc.player.getEyePosition();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || entity.isRemoved()) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;

            double distance = eye.distanceTo(entity.getEyePosition());
            if (distance > range.get() || distance >= bestDistance) continue;

            best = entity;
            bestDistance = distance;
        }
        return best;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Hops: " + hops);
        ImGui.text(String.format("Last hop: %.2f blocks", lastDistance));
    }
}
