package com.tricrotism.utils;

import imgui.ImColor;

/**
 * Shared color thresholds for server performance metrics.
 */
public final class MetricColors {

    private MetricColors() {}

    public static int tps(float tps) {
        if (tps >= 18.0f) return ImColor.rgb("#00FF00");
        if (tps >= 15.0f) return ImColor.rgb("#FFFF00");
        return ImColor.rgb("#FF0000");
    }

    public static int mspt(float mspt) {
        if (mspt <= 35.0f) return ImColor.rgb("#00FF00");
        if (mspt <= 65.0f) return ImColor.rgb("#FFFF00");
        return ImColor.rgb("#FF0000");
    }
}
