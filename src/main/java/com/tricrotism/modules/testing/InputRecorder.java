package com.tricrotism.modules.testing;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.InputTrace;
import com.tricrotism.api.testing.TestLog;
import imgui.ImGui;
import imgui.ImGuiIO;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the gaps between real attack clicks, straight from the mouse callback.
 * <p>
 * Timing is taken where the press actually arrives rather than on the client tick, which matters
 * more than it looks: a tick is 50 ms and butterfly clicking sits near that, so tick sampling would
 * alias away the very jitter the recording exists to preserve.
 * <p>
 * The live deviation readout is the useful part while recording. It is the same statistic an
 * interval-consistency check compares against its floor, so a minute of honest clicking says
 * directly how much room there is between real hands and the threshold, which is a number worth
 * having before choosing one rather than after.
 */
public final class InputRecorder extends Module {

    public static final InputRecorder instance = new InputRecorder();

    /**
     * Two presses more than a second apart are separate bursts, not one interval.
     */
    private static final long BURST_GAP_MS = 1_000L;

    private final Settings.Text traceName =
        text("Trace Name", "trace", "File the recording is saved under, in sagefang-testlog/traces",
            "human", 64);

    private final List<Long> intervals = new ArrayList<>();
    private long lastPressMs;

    private InputRecorder() {
        super("inputrecorder", "Input Recorder", "Record genuine click timing for false-positive runs.",
            Category.LOGGING);
    }

    /**
     * Called from the mouse callback on the render thread. Only presses of the attack button count;
     * releases and every other button are someone doing something else.
     */
    public void onMouseButton(int button, int action) {
        if (!isActive() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS) return;

        long now = System.currentTimeMillis();
        if (lastPressMs != 0L) {
            long gap = now - lastPressMs;
            if (gap <= BURST_GAP_MS) intervals.add(gap);
        }
        lastPressMs = now;
    }

    @Override
    public void onActivate() {
        intervals.clear();
        lastPressMs = 0L;
        TestLog.event("recorder_start", "trace", traceName.get());
    }

    @Override
    public void onDeactivate() {
        if (intervals.isEmpty()) {
            TestLog.event("recorder_stop", "trace", traceName.get(), "intervals", 0);
            return;
        }

        boolean saved = InputTrace.save(traceName.get(), intervals);
        TestLog.event("recorder_stop",
            "trace", traceName.get(),
            "intervals", intervals.size(),
            "meanMs", InputTrace.mean(intervals),
            "deviationMs", InputTrace.deviation(intervals),
            "saved", saved);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Intervals: " + intervals.size());
        if (intervals.size() < 2) {
            ImGui.text("Click to record...");
            return;
        }

        double mean = InputTrace.mean(intervals);
        ImGui.text(String.format("Mean: %.1f ms (%.1f CPS)", mean, 1000.0 / mean));
        ImGui.text(String.format("Deviation: %.2f ms", InputTrace.deviation(intervals)));
    }
}
