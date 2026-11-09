package com.tricrotism.utils;

import imgui.ImColor;

/**
 * Shared color thresholds for server performance metrics.
 */
public final class MetricColors {

    // parsed once rather than per call: both accessors are read from menus that draw every frame
    private static final int GOOD = ImColor.rgb("#00FF00");
    private static final int WARN = ImColor.rgb("#FFFF00");
    private static final int BAD = ImColor.rgb("#FF0000");

    private MetricColors() {}

    public static int tps(float tps) {
        if (tps >= 18.0f) return GOOD;
        if (tps >= 15.0f) return WARN;
        return BAD;
    }

    public static int mspt(float mspt) {
        if (mspt <= 35.0f) return GOOD;
        if (mspt <= 65.0f) return WARN;
        return BAD;
    }
}
