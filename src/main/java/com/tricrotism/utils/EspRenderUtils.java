package com.tricrotism.utils;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Shared world-space box rendering for ESP modules. Emit into a
 * {@code RenderTypes.lines()} buffer whose pose is already translated so world
 * coordinates are camera-relative (see how Blink's renderer sets up the matrix).
 */
public final class EspRenderUtils {

    private EspRenderUtils() {}

    /**
     * Draws the 12 edges of the axis-aligned box between the two corners.
     */
    public static void drawBox(Matrix4f pose, VertexConsumer lines,
                               double x1, double y1, double z1, double x2, double y2, double z2,
                               float r, float g, float b, float a, float width) {
        // bottom
        edge(pose, lines, x1, y1, z1, x2, y1, z1, r, g, b, a, width);
        edge(pose, lines, x2, y1, z1, x2, y1, z2, r, g, b, a, width);
        edge(pose, lines, x2, y1, z2, x1, y1, z2, r, g, b, a, width);
        edge(pose, lines, x1, y1, z2, x1, y1, z1, r, g, b, a, width);
        // top
        edge(pose, lines, x1, y2, z1, x2, y2, z1, r, g, b, a, width);
        edge(pose, lines, x2, y2, z1, x2, y2, z2, r, g, b, a, width);
        edge(pose, lines, x2, y2, z2, x1, y2, z2, r, g, b, a, width);
        edge(pose, lines, x1, y2, z2, x1, y2, z1, r, g, b, a, width);
        // verticals
        edge(pose, lines, x1, y1, z1, x1, y2, z1, r, g, b, a, width);
        edge(pose, lines, x2, y1, z1, x2, y2, z1, r, g, b, a, width);
        edge(pose, lines, x2, y1, z2, x2, y2, z2, r, g, b, a, width);
        edge(pose, lines, x1, y1, z2, x1, y2, z2, r, g, b, a, width);
    }

    private static void edge(Matrix4f pose, VertexConsumer lines,
                             double ax, double ay, double az, double bx, double by, double bz,
                             float r, float g, float b, float a, float width) {
        float nx = (float) (bx - ax);
        float ny = (float) (by - ay);
        float nz = (float) (bz - az);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return;
        nx /= len;
        ny /= len;
        nz /= len;
        lines.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(width);
        lines.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(width);
    }
}
