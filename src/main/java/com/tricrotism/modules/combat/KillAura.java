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
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Attacks nearby entities on an interval, optionally steering the transmitted rotation onto the
 * target through {@link RotationManager}.
 * <p>
 * Built for measuring an anticheat rather than evading one. Every parameter is a slider so a run can
 * sweep for a threshold instead of returning pass/fail, the click interval is drawn from a seeded
 * stream so a flagging run replays exactly, and each attack is written to {@link TestLog} with the
 * rotation that actually went out.
 */
public final class KillAura extends Module {

    public static final KillAura instance = new KillAura();

    /**
     * Beats passive modules but loses to anything the user drives directly.
     */
    private static final int ROTATION_PRIORITY = 100;

    private static final int CONTAINER_FAKE_CLOSE = 1;
    private static final int CONTAINER_SKIP = 2;

    private final Settings.Decimal range =
        decimal("Range", "range", "Maximum eye-to-eye distance to a target", 3.0, 2.0, 6.0);
    private final Settings.Int cpsMin =
        integer("CPS Min", "cpsMin", "Lower bound on clicks per second", 8, 1, 20);
    private final Settings.Int cpsMax =
        integer("CPS Max", "cpsMax", "Upper bound on clicks per second", 12, 1, 20);
    private final Settings.Decimal deviationMs =
        decimal("Deviation (ms)", "deviationMs",
            "Gaussian noise added to each interval; the statistic a consistency check reads",
            0.0, 0.0, 20.0);
    private final Settings.Mode distribution =
        mode("Distribution", "distribution", "Shape of the click-interval sample", 2,
            "Constant", "Uniform", "Gaussian");
    private final Settings.Bool rotate =
        bool("Server Rotations", "rotate",
            "Steer the transmitted rotation onto the target without moving the camera", true);
    private final Settings.Bool requireLineOfSight =
        bool("Require Line of Sight", "requireLineOfSight", "Skip targets behind blocks", true);
    private final Settings.Bool playersOnly =
        bool("Players Only", "playersOnly", "Ignore non-player entities", false);
    private final Settings.Bool respectCooldown =
        bool("Respect Cooldown", "respectCooldown", "Wait for the attack meter to refill", true);
    private final Settings.Int targetCount =
        integer("Targets/Tick", "targets", "Distinct entities to attack inside one client tick", 1, 1, 4);
    private final Settings.Int aimOffset =
        integer("Aim Offset (deg)", "aimOffset",
            "Degrees to aim away from the target; anything under the check's cone is accepted", 0, 0, 90);
    private final Settings.Mode container =
        mode("Container", "container", "What to do about a screen being open when attacking", 0,
            "Ignore Screen", "Fake Close", "Skip While Open");
    private final Settings.Int closeRatio =
        integer("Close Ratio (%)", "closeRatio",
            "Share of attacks that carry their own close, in Fake Close mode", 100, 0, 100);

    private final Random rng = TestLog.rng("killaura");
    private long lastAttackMs;
    private long nextDelayMs;
    private int attacks;
    private final List<Entity> targets = new ArrayList<>();
    private volatile Entity current;

    private KillAura() {
        super("killaura", "Kill Aura", "Attack nearby entities; sweeps reach, CPS and rotation.",
            Category.COMBAT);
    }

    @Override
    public void onActivate() {
        attacks = 0;
        lastAttackMs = 0L;
        nextDelayMs = 0L;
        TestLog.event("killaura_enable", "range", range.get(), "cpsMin", cpsMin.get(), "cpsMax", cpsMax.get(),
            "distribution", distribution.option(), "rotate", rotate.get(),
            "maxStep", RotationManager.getMaxStep(), "requireLineOfSight", requireLineOfSight.get(),
            "targets", targetCount.get(), "container", container.option(),
            "closeRatio", closeRatio.get(), "deviationMs", deviationMs.get());
    }

    @Override
    public void onDeactivate() {
        current = null;
        targets.clear();
        TestLog.event("killaura_disable", "attacks", attacks);
    }

    /**
     * Runs on the pre-tick so a rotation request lands before {@code LocalPlayer.sendPosition()}
     * transmits it later in the same tick.
     */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.level == null || mc.gameMode == null) {
            current = null;
            return;
        }

        collectTargets();
        current = targets.isEmpty() ? null : targets.getFirst();
        if (targets.isEmpty()) return;

        Vec3 eye = mc.player.getEyePosition();
        Entity primary = targets.getFirst();
        Vec3 aim = primary.getEyePosition();
        float yaw = RotationManager.yawTo(eye, aim);
        float pitch = RotationManager.pitchTo(eye, aim);

        // Offset inside the cone rather than onto the target. The cone is wide because it exists to
        // separate looking at something from not looking at it at all, so every degree short of it is
        // accepted exactly as readily as aiming true.
        if (rotate.get()) RotationManager.request(yaw + aimOffset.get(), pitch, ROTATION_PRIORITY);

        long now = System.currentTimeMillis();
        if (lastAttackMs != 0L && now - lastAttackMs < nextDelayMs) return;
        if (respectCooldown.get() && mc.player.getAttackStrengthScale(0f) < 1.0f) return;
        if (container.get() == CONTAINER_SKIP && containerOpen()) return;

        long interval = lastAttackMs == 0L ? 0L : now - lastAttackMs;
        lastAttackMs = now;
        nextDelayMs = sampleDelayMs();

        for (Entity target : targets) {
            attack(target, eye, yaw, pitch, interval);
        }
    }

    /**
     * One attack, preceded by a close packet when that mode is on. The close is sent per attack
     * rather than once per burst on purpose: what a container check reads is the proportion of
     * attacks that carry their own close, and a single close covering several attacks would leave
     * that ratio well under any threshold.
     */
    private void attack(Entity target, Vec3 eye, float yaw, float pitch, long interval) {
        // A proportion rather than every attack. What a container check reads is the share of
        // attacks preceded by their own close, so the interesting run is the one sitting just under
        // whatever share it considers damning, not the one that closes every single time.
        boolean closed = container.get() == CONTAINER_FAKE_CLOSE && rng.nextInt(100) < closeRatio.get();
        if (closed && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
        }

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        attacks++;

        TestLog.event("attack",
            "targetId", target.getId(),
            "distance", eye.distanceTo(target.getEyePosition()),
            "sentYaw", rotate.get() ? RotationManager.yaw() : mc.player.getYRot(),
            "sentPitch", rotate.get() ? RotationManager.pitch() : mc.player.getXRot(),
            "aimYaw", yaw,
            "aimPitch", pitch,
            "intervalMs", interval,
            "nextDelayMs", nextDelayMs,
            "targetsThisTick", targets.size(),
            "containerOpen", containerOpen(),
            "closed", closed,
            "container", container.option());
    }

    /**
     * Whether a real container is open, rather than the player's own inventory menu. This is the
     * state a container check reads, and it is not the same question as whether any screen is up:
     * chat and the pause menu are screens the server never hears about.
     */
    private boolean containerOpen() {
        return mc.player.containerMenu != mc.player.inventoryMenu;
    }

    /**
     * The nearest attackable entities, closest first, up to {@link #targetCount}. Distinct entities
     * inside one tick is the whole of what a multi-target check reads, so the list is capped by
     * target rather than by attack count.
     */
    private void collectTargets() {
        targets.clear();

        Vec3 eye = mc.player.getEyePosition();
        int wanted = targetCount.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || entity.isRemoved()) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (playersOnly.get() && !(entity instanceof Player)) continue;

            double distance = eye.distanceTo(entity.getEyePosition());
            if (distance > range.get()) continue;
            if (requireLineOfSight.get() && !mc.player.hasLineOfSight(entity)) continue;

            targets.add(entity);
        }

        targets.sort(Comparator.comparingDouble(entity -> eye.distanceTo(entity.getEyePosition())));
        if (targets.size() > wanted) targets.subList(wanted, targets.size()).clear();
    }

    /**
     * Milliseconds until the next attack. Gaussian centres on the midpoint with the range spanning
     * roughly four standard deviations, which is the shape a tuned cheat uses to sit under a
     * per-sample CPS threshold, and so the interesting case for a check that flags on shape.
     */
    private long sampleDelayMs() {
        int low = Math.min(cpsMin.get(), cpsMax.get());
        int high = Math.max(cpsMin.get(), cpsMax.get());
        double cps = switch (distribution.get()) {
            case 0 -> high;
            case 1 -> low + rng.nextDouble() * (high - low);
            default -> {
                double mid = (low + high) / 2.0;
                double deviation = Math.max(0.001, (high - low) / 4.0);
                yield Math.clamp(mid + rng.nextGaussian() * deviation, low, high);
            }
        };
        double delay = 1000.0 / Math.max(0.5, cps);
        // Added on top of the distribution rather than replacing it, so shape and spread stay
        // separate knobs. A consistency check keying on standard deviation is measuring exactly this
        // number, which makes it the one to walk across the threshold.
        if (deviationMs.get() > 0.0) delay += rng.nextGaussian() * deviationMs.get();
        return (long) Math.max(0.0, delay);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        float[] step = {RotationManager.getMaxStep()};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderFloat("Max Step (deg/tick)##kaStep", step, 1f, 180f)) {
            RotationManager.setMaxStep(step[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Shared rotation limit; also applies to other rotating modules");
        }

        if (ImGui.checkbox("Quantize Rotations##kaGcd", RotationManager.isQuantize())) {
            RotationManager.setQuantize(!RotationManager.isQuantize());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Snap transmitted rotation deltas to the mouse-sensitivity lattice a real "
                + "client is limited to; shared with other rotating modules");
        }

        Entity t = current;
        ImGui.text("Target: " + (t == null ? "none" : t.getName().getString()));
        ImGui.text("Attacks: " + attacks);
        ImGui.text("Targets in range: " + targets.size());
    }
}
