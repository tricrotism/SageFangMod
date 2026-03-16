package com.tricrotism.utils;

import com.tricrotism.config.SageFangConfig;
import imgui.ImGui;
import imgui.extension.implot.ImPlot;
import imgui.extension.implot.flag.ImPlotAxis;
import imgui.extension.implot.flag.ImPlotCond;
import imgui.extension.implot.flag.ImPlotFlags;

/**
 * Shared graph rendering helpers used by ServerInfoMenu, PlayerInfoMenu, and InfoMenu.
 */
public final class GraphRenderer {

    private GraphRenderer() {}

    /**
     * Renders a simple ImGui.plotLines graph (for standalone menus).
     */
    public static void plotLines(String label, RingBuffer buffer, float yMin, float yMax) {
        float[] data = buffer.toArray();
        if (data.length < 2) return;

        float effectiveMax = Float.isNaN(yMax) ? Float.MAX_VALUE : yMax;
        ImGui.plotLines("##" + label, data, data.length, 0,
            label + ": " + String.format("%.1f", buffer.last()),
            yMin, effectiveMax,
            SageFangConfig.getGraphWidth(), SageFangConfig.getGraphHeight());
    }

    /**
     * Renders a full ImPlot graph (for merged info menu).
     */
    public static void plotImPlot(String label, RingBuffer buffer, float yMin, float yMax) {
        if (buffer.size() == 0) return;

        float[] values = buffer.toArray();
        int w = SageFangConfig.getGraphWidth();
        int h = SageFangConfig.getGraphHeight();

        if (ImPlot.beginPlot(label, w, h,
            ImPlotFlags.NoLegend | ImPlotFlags.NoMouseText | ImPlotFlags.NoInputs | ImPlotFlags.NoFrame)) {
            ImPlot.setupAxisLimits(ImPlotAxis.X1, 0, SageFangConfig.getGraphHistory(), ImPlotCond.Always);
            ImPlot.setupAxisLimits(ImPlotAxis.Y1, yMin, yMax, ImPlotCond.Always);
            ImPlot.plotLine(label, values);
            ImPlot.endPlot();
        }
    }
}
