package com.tricrotism.modules.latency;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import imgui.ImGui;
import imgui.ImGuiIO;

/**
 * Scales how many game ticks the client runs per real second, which scales the rate of movement
 * packets reaching the server with it.
 * <p>
 * Above 1.0 the client banks distance faster than wall-clock allows; below 1.0 it accrues credit it
 * can spend later. A movement budget that clamps positive accumulation but not negative is the
 * counter, so the interesting sweep is not a single high multiplier but an alternation either side
 * of 1.0 that nets out to roughly normal speed.
 */
public final class Timer extends Module {

    public static final Timer instance = new Timer();

    private final Settings.Decimal multiplier =
        decimal("Multiplier", "multiplier", "Game ticks run per real tick", 1.0, 0.1, 5.0);

    private double residual;
    private long scaledTicks;
    private long realTicks;

    private Timer() {
        super("timer", "Timer", "Scale the client tick rate, and with it the movement packet rate.",
            Category.NETWORK);
    }

    @Override
    public void onActivate() {
        residual = 0.0;
        scaledTicks = 0L;
        realTicks = 0L;
        TestLog.event("timer_enable", "multiplier", multiplier.get());
    }

    @Override
    public void onDeactivate() {
        residual = 0.0;
        TestLog.event("timer_disable", "realTicks", realTicks, "scaledTicks", scaledTicks);
    }

    /**
     * Called from TimerMixin with the tick count vanilla decided to run. Truncating each call
     * independently would lose the remainder every time and drift the effective rate well below the
     * configured one, so the fraction is carried forward.
     */
    public int scale(int ticks) {
        if (!isActive() || ticks <= 0) return ticks;

        realTicks += ticks;
        residual += ticks * multiplier.get();
        int out = (int) residual;
        residual -= out;
        scaledTicks += out;
        return out;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Ticks: " + scaledTicks + " sent / " + realTicks + " real");
        if (realTicks > 0) {
            ImGui.text(String.format("Effective: %.3fx", scaledTicks / (double) realTicks));
        }
        ImGui.textDisabled("Alternate around 1.0 to probe budget clamping.");
    }
}
