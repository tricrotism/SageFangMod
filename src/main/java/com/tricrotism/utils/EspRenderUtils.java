package com.tricrotism.utils;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tricrotism.config.SageFangConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * Shared world-space box rendering for ESP modules. Draw through
 * {@link #submitBoxes(LevelRenderContext, BoxSource)}, which handles the camera-relative transform
 * and the fill/outline split.
 */
public final class EspRenderUtils {

    /**
     * Fraction of the caller's alpha used for the fill, which stacks across the box's six faces.
     */
    private static final float FILL_ALPHA = 0.30f;

    /**
     * Colour multiplier applied to the outline when it sits on top of a fill.
     */
    private static final float OUTLINE_DARKEN = 0.45f;

    /**
     * Boxes shorter than this, in blocks, draw as a single top rectangle.
     */
    private static final double FLAT_HEIGHT = 0.05;

    /**
     * Vanilla {@code RenderPipelines.LINES} with the depth state dropped. A null depth state makes
     * the backend disable both the depth test and depth writes, so ESP geometry stays visible
     * through terrain and never occludes anything drawn after it.
     */
    private static final RenderPipeline LINES_NO_DEPTH = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("sagefang", "pipeline/lines_no_depth"))
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withVertexShader("core/rendertype_lines")
        .withFragmentShader("core/rendertype_lines")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
        .withPrimitiveTopology(PrimitiveTopology.LINES)
        .withDepthStencilState(Optional.empty())
        .build();

    /**
     * Vanilla {@code RenderPipelines.DEBUG_FILLED_BOX} with the depth state dropped and culling
     * disabled. The box faces have to stay visible from inside, since chunk-sized ESP volumes
     * usually contain the camera.
     */
    private static final RenderPipeline QUADS_NO_DEPTH = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("sagefang", "pipeline/quads_no_depth"))
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(Optional.empty())
        .build();

    private static final RenderType LINES_THROUGH_WALLS = RenderType.create(
        "sagefang_lines_no_depth",
        RenderSetup.builder(LINES_NO_DEPTH)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup());

    private static final RenderType QUADS_THROUGH_WALLS = RenderType.create(
        "sagefang_quads_no_depth",
        RenderSetup.builder(QUADS_NO_DEPTH)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup());

    private EspRenderUtils() {}

    /**
     * Line render type that ignores the depth buffer.
     */
    public static RenderType linesThroughWalls() {
        return LINES_THROUGH_WALLS;
    }

    /**
     * Receives the boxes a {@link BoxSource} wants drawn.
     */
    @FunctionalInterface
    public interface BoxSink {
        void box(double x1, double y1, double z1, double x2, double y2, double z2,
                 float r, float g, float b, float a, float width);
    }

    /**
     * Emits a batch of boxes. Called once per draw pass, so it must be repeatable. Read from a
     * snapshot rather than mutating as you go.
     */
    @FunctionalInterface
    public interface BoxSource {
        void emit(BoxSink sink);
    }

    /**
     * Submits a batch of world-space boxes in camera-relative coordinates. With Filled ESP off this
     * is one outline pass; with it on, the source is replayed as a translucent fill followed by a
     * darkened outline.
     * <p>
     * The camera is subtracted here, in double precision, rather than pushed onto the pose stack as a
     * translation. A {@link Matrix4f} holds floats, so a matrix carrying the camera position rounds
     * it, and the vertices multiplied through it are absolute world coordinates that were rounded to
     * float on the way in as well. Both errors grow with distance from the origin and the matrix one
     * changes every frame, which is what made boxes drift and shimmer as you walked rather than sit
     * on their blocks. Subtracting first keeps the numbers that reach the buffer small, where float
     * has resolution to spare.
     */
    public static void submitBoxes(LevelRenderContext context, BoxSource source) {
        var collector = context.submitNodeCollector();
        var matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        double cx = camera.x;
        double cy = camera.y;
        double cz = camera.z;

        // Boxes behind or beside the camera still cost every vertex, so skip them. Most of them are
        // off screen whenever the ESP covers the area around the player.
        Frustum frustum = context.levelState().cameraRenderState.cullFrustum;

        boolean filled = SageFangConfig.isEspFilled();
        if (filled) {
            collector.submitCustomGeometry(matrices, QUADS_THROUGH_WALLS, (pose, buf) ->
                source.emit((x1, y1, z1, x2, y2, z2, r, g, b, a, width) -> {
                    if (!visible(frustum, x1, y1, z1, x2, y2, z2)) return;
                    fillBox(pose.pose(), buf,
                        x1 - cx, y1 - cy, z1 - cz, x2 - cx, y2 - cy, z2 - cz,
                        r, g, b, a * FILL_ALPHA);
                }));
        }
        float shade = filled ? OUTLINE_DARKEN : 1f;
        collector.submitCustomGeometry(matrices, LINES_THROUGH_WALLS, (pose, buf) ->
            source.emit((x1, y1, z1, x2, y2, z2, r, g, b, a, width) -> {
                if (!visible(frustum, x1, y1, z1, x2, y2, z2)) return;
                drawBox(pose.pose(), buf,
                    x1 - cx, y1 - cy, z1 - cz, x2 - cx, y2 - cy, z2 - cz,
                    r * shade, g * shade, b * shade, a, width);
            }));
    }

    /**
     * The same test as {@code Frustum.isVisible(AABB)}, without building an AABB per box.
     *
     * @return true when the box is inside or crosses the frustum, or there is no frustum to test
     */
    private static boolean visible(Frustum frustum, double x1, double y1, double z1, double x2, double y2, double z2) {
        if (frustum == null) return true;
        int result = frustum.cubeInFrustum(x1, y1, z1, x2, y2, z2);
        return result == FrustumIntersection.INSIDE || result == FrustumIntersection.INTERSECT;
    }

    /**
     * Draws the 12 edges of the axis-aligned box between the two corners.
     */
    public static void drawBox(Matrix4f pose, VertexConsumer lines,
                               double x1, double y1, double z1, double x2, double y2, double z2,
                               float r, float g, float b, float a, float width) {
        // A box this flat draws its top and bottom outlines on top of each other, so one ring of
        // 8 vertices replaces 24. Light Level ESP marks thousands of these a frame.
        if (y2 - y1 < FLAT_HEIGHT) {
            edge(pose, lines, x1, y2, z1, x2, y2, z1, r, g, b, a, width);
            edge(pose, lines, x2, y2, z1, x2, y2, z2, r, g, b, a, width);
            edge(pose, lines, x2, y2, z2, x1, y2, z2, r, g, b, a, width);
            edge(pose, lines, x1, y2, z2, x1, y2, z1, r, g, b, a, width);
            return;
        }
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

    /**
     * Draws the 6 faces of the axis-aligned box between the two corners. Culling is off for this
     * render type, so winding does not matter. Each face is emitted as one corner ring.
     */
    public static void fillBox(Matrix4f pose, VertexConsumer quads,
                               double x1, double y1, double z1, double x2, double y2, double z2,
                               float r, float g, float b, float a) {
        if (y2 - y1 < FLAT_HEIGHT) {
            face(pose, quads, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
            return;
        }
        face(pose, quads, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        face(pose, quads, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        face(pose, quads, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        face(pose, quads, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        face(pose, quads, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        face(pose, quads, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private static void face(Matrix4f pose, VertexConsumer quads,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz,
                             float r, float g, float b, float a) {
        vertex(quads, pose, (float) ax, (float) ay, (float) az).setColor(r, g, b, a);
        vertex(quads, pose, (float) bx, (float) by, (float) bz).setColor(r, g, b, a);
        vertex(quads, pose, (float) cx, (float) cy, (float) cz).setColor(r, g, b, a);
        vertex(quads, pose, (float) dx, (float) dy, (float) dz).setColor(r, g, b, a);
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
        vertex(lines, pose, (float) ax, (float) ay, (float) az).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(width);
        vertex(lines, pose, (float) bx, (float) by, (float) bz).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(width);
    }

    /**
     * {@code VertexConsumer.addVertex(Matrix4fc, x, y, z)} allocates a Vector3f for every vertex,
     * which made this file half the render thread with the ESPs on. Same arithmetic as JOML's
     * {@code mulPositionGeneric}, so the output is bit-identical, without the allocation.
     */
    private static VertexConsumer vertex(VertexConsumer buffer, Matrix4f m, float x, float y, float z) {
        return buffer.addVertex(
            org.joml.Math.fma(m.m00(), x, org.joml.Math.fma(m.m10(), y, org.joml.Math.fma(m.m20(), z, m.m30()))),
            org.joml.Math.fma(m.m01(), x, org.joml.Math.fma(m.m11(), y, org.joml.Math.fma(m.m21(), z, m.m31()))),
            org.joml.Math.fma(m.m02(), x, org.joml.Math.fma(m.m12(), y, org.joml.Math.fma(m.m22(), z, m.m32()))));
    }
}
