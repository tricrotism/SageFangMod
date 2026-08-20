package com.tricrotism.modules.testing;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.InputTrace;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Attacks on the timing of a recorded human, rather than on a generated one.
 * <p>
 * This is the false-positive gate, and it is the half of the methodology that a cheat module cannot
 * supply. A generated distribution answers "does the check fire on a generator"; only a
 * replayed recording answers "does it stay quiet on a person", and the two questions have to be
 * asked of the same check with the same thresholds before either answer means anything.
 * <p>
 * Everything else is deliberately vanilla (real range, real cooldown, no rotation spoofing), so
 * that a flag during a replay is about the timing and cannot be about anything else. If this module
 * ever trips an interval check, the threshold is wrong, not the player.
 * <p>
 * Timing is honoured against the wall clock rather than the tick, since a trace of hand clicking
 * carries gaps that do not divide into fifty milliseconds and rounding them to ticks would quantise
 * the recording into exactly the regularity being tested for.
 */
public final class InputReplay extends Module {

    public static final InputReplay instance = new InputReplay();

    private final Settings.Text traceName =
        text("Trace Name", "trace", "Recording to replay, from sagefang-testlog/traces", "human", 64);
    private final Settings.Bool loop =
        bool("Loop", "loop", "Start the trace again when it runs out", true);
    private final Settings.Decimal range =
        decimal("Range", "range", "Maximum eye-to-eye distance to a target", 3.0, 2.0, 4.5);

    private List<Long> intervals = List.of();
    private int index;
    private long nextAttackMs;
    private int attacks;

    private InputReplay() {
        super("inputreplay", "Input Replay", "Replay recorded human click timing as attacks.",
            Category.COMBAT);
    }

    @Override
    public void onActivate() {
        intervals = InputTrace.load(traceName.get());
        index = 0;
        attacks = 0;
        nextAttackMs = 0L;
        TestLog.event("replay_start",
            "trace", traceName.get(),
            "intervals", intervals.size(),
            "meanMs", InputTrace.mean(intervals),
            "deviationMs", InputTrace.deviation(intervals));
    }

    @Override
    public void onDeactivate() {
        TestLog.event("replay_stop", "trace", traceName.get(), "attacks", attacks);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || intervals.isEmpty()) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (index >= intervals.size()) {
            if (!loop.get()) return;
            index = 0;
        }

        long now = System.currentTimeMillis();
        if (nextAttackMs == 0L) nextAttackMs = now;
        if (now < nextAttackMs) return;

        Entity target = nearestTarget();
        if (target == null) return;

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        attacks++;

        long interval = intervals.get(index);
        index++;
        // Advance from the scheduled time, not from now, so a tick boundary landing late does not
        // stretch the trace a little further with every click.
        nextAttackMs += interval;
        if (nextAttackMs < now) nextAttackMs = now;

        TestLog.event("replay_attack",
            "targetId", target.getId(),
            "intervalMs", interval,
            "index", index,
            "trace", traceName.get());
    }

    private Entity nearestTarget() {
        Vec3 eye = mc.player.getEyePosition();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || entity.isRemoved()) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;

            double distance = eye.distanceTo(entity.getEyePosition());
            if (distance > range.get() || distance >= bestDistance) continue;
            if (!mc.player.hasLineOfSight(entity)) continue;

            best = entity;
            bestDistance = distance;
        }
        return best;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        if (intervals.isEmpty()) {
            ImGui.text("No trace loaded (enable to load)");
            return;
        }
        ImGui.text("Trace: " + intervals.size() + " intervals");
        ImGui.text(String.format("Mean: %.1f ms | Deviation: %.2f ms",
            InputTrace.mean(intervals), InputTrace.deviation(intervals)));
        ImGui.text("Position: " + index + " | Attacks: " + attacks);
    }
}
