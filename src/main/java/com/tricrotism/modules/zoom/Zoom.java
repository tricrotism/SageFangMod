package com.tricrotism.modules.zoom;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * LabyMod-style zoom with smooth FOV transition, scroll-to-zoom,
 * and optional cinematic (smooth) camera while zoomed.
 * <p>
 * The FOV is modified via {@code GameRendererFovMixin} which reads
 * {@link #getInterpolatedFovMultiplier(float)}. Scroll-to-zoom is intercepted
 * by {@code MouseInputMixin} which calls {@link #handleScroll(double)}.
 */
public class Zoom extends Module {

    public static final Zoom instance = new Zoom();

    private final Settings.Key keybind = key("Zoom Key", "keybind", "Activation key", GLFW.GLFW_KEY_Z);

    private final Settings.Bool holdMode = bool("Hold Mode", "holdMode", "Hold: zoom while key held; toggle otherwise", true);
    private final Settings.Bool scrollToZoom = bool("Scroll to Zoom", "scrollToZoom", "Fine-tune zoom with scroll", true);
    private final Settings.Bool smoothTransition = bool("Smooth Transition", "smoothTransition", "Ease the zoom in and out", true);
    private final Settings.Bool cinematicCamera = bool("Cinematic Camera", "cinematicCamera", "Enable cinematic camera while zoomed", false);
    private final Settings.Decimal baseZoomDistance = decimal("Zoom Distance", "zoomDistance", "FOV divisor; 4 means FOV/4", 4.0, 2.0, 20.0);

    private static final float TRANSITION_SPEED = 0.3f;
    private static final float SNAP_THRESHOLD = 0.001f;
    private static final float MIN_ZOOM_DISTANCE = 1.1f;
    private static final float MAX_ZOOM_DISTANCE = 1000.0f;


    private boolean zoomEngaged;
    private float scrollOffset;
    private float previousFovMultiplier = 1.0f;
    private float currentFovMultiplier = 1.0f;
    private boolean savedSmoothCamera;

    private boolean keyWasDown;

    public boolean isScrollToZoom() {return scrollToZoom.get();}

    public Zoom() {
        super("zoom", "Zoom", "Adjustable camera zoom with smooth transitions.", Category.RENDER);
        migrateKeybind();
        loadConfig();
    }

    private void loadConfig() {
    }

    private void migrateKeybind() {
    }

    /**
     * True when the zoom effect is visually active: either the key is held,
     * or the FOV is still transitioning back to normal.
     */
    public boolean isZoomEngaged() {
        return isActive() && (zoomEngaged || currentFovMultiplier < 0.999f);
    }

    /**
     * Current FOV multiplier interpolated between previous and current tick
     * using the render partial tick for frame-rate-independent smoothness.
     * 1.0 = normal, lower = zoomed. Read by {@code GameRendererFovMixin}.
     *
     * @param partialTick fractional tick progress (0.0 to 1.0) within the current render frame
     */
    public float getInterpolatedFovMultiplier(float partialTick) {
        return Mth.lerp(partialTick, previousFovMultiplier, currentFovMultiplier);
    }

    /**
     * Called by {@code MouseInputMixin} when the player scrolls while zoomed.
     * Adjusts the scroll offset to increase or decrease zoom level.
     *
     * @param delta raw vertical scroll delta (positive = scroll up = zoom in more)
     */
    public void handleScroll(double delta) {
        if (!zoomEngaged) return;
        float direction = (float) Math.signum(delta);
        float scrollDelta = 0.25f * direction;
        float newDistance = baseZoomDistance.get().floatValue() + scrollOffset + scrollDelta;
        if (newDistance >= MIN_ZOOM_DISTANCE && newDistance <= MAX_ZOOM_DISTANCE) {
            scrollOffset += scrollDelta;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        int key = keybind.get();
        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            boolean down = KeybindUtil.isKeyDown(key);
            if (holdMode.get()) {
                setZoomState(down);
            } else {
                if (down && !keyWasDown) setZoomState(!zoomEngaged);
            }
            keyWasDown = down;
        }

        float effectiveDistance = baseZoomDistance.get().floatValue() + scrollOffset;
        float target = zoomEngaged ? (1.0f / effectiveDistance) : 1.0f;
        if (smoothTransition.get()) {
            previousFovMultiplier = currentFovMultiplier;
            currentFovMultiplier += (target - currentFovMultiplier) * TRANSITION_SPEED;
            if (Math.abs(currentFovMultiplier - target) < SNAP_THRESHOLD) {
                currentFovMultiplier = target;
            }
        } else {
            previousFovMultiplier = target;
            currentFovMultiplier = target;
        }

        if (!zoomEngaged && currentFovMultiplier >= 0.999f) {
            scrollOffset = 0;
        }
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        if (zoomEngaged) setZoomState(false);
    }

    private void setZoomState(boolean engaged) {
        if (this.zoomEngaged == engaged) return;
        this.zoomEngaged = engaged;

        if (engaged && cinematicCamera.get()) {
            savedSmoothCamera = mc.options.smoothCamera;
            mc.options.smoothCamera = true;
        } else if (!engaged && cinematicCamera.get()) {
            mc.options.smoothCamera = savedSmoothCamera;
        }
    }

    @Override
    public void onActivate() {
        SageFang.LOGGER.info("[Zoom] Activated");
    }

    @Override
    public void onDeactivate() {
        if (zoomEngaged) setZoomState(false);
        previousFovMultiplier = 1.0f;
        currentFovMultiplier = 1.0f;
        scrollOffset = 0;
        SageFang.LOGGER.info("[Zoom] Deactivated");
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        if (isActive() && zoomEngaged) {
            float effectiveDistance = baseZoomDistance.get().floatValue() + scrollOffset;
            ImGui.text(String.format("Zoom: %.1fx (FOV mult: %.3f)", effectiveDistance, currentFovMultiplier));
        }
        baseZoomDistance.render();
        keybind.render();
    }
}
