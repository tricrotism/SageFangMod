package com.tricrotism.modules.zoom;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import lombok.Getter;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * LabyMod-style zoom with smooth FOV transition, scroll-to-zoom,
 * and optional cinematic (smooth) camera while zoomed.
 * <p>
 * The FOV is modified via {@code GameRendererFovMixin} which reads
 * {@link #getInterpolatedFovMultiplier(float)}. Scroll-to-zoom is intercepted
 * by {@code MouseHandlerExtMixin} which calls {@link #handleScroll(double)}.
 */
public class Zoom extends Module implements Menu {

    public static final Zoom instance = new Zoom();

    private static final float TRANSITION_SPEED = 0.3f;
    private static final float SNAP_THRESHOLD = 0.001f;
    private static final float MIN_ZOOM_DISTANCE = 1.1f;
    private static final float MAX_ZOOM_DISTANCE = 50.0f;

    private boolean holdMode;
    private float baseZoomDistance;
    @Getter private boolean scrollToZoom;
    private boolean smoothTransition;
    private boolean cinematicCamera;

    private boolean zoomEngaged;
    private float scrollOffset;
    private float previousFovMultiplier = 1.0f;
    private float currentFovMultiplier = 1.0f;
    private boolean savedSmoothCamera;

    private boolean awaitingKeybind;
    private boolean keyWasDown;

    public Zoom() {
        super("zoom", "Zoom", "Adjustable camera zoom with smooth transitions.", "Visual");
        migrateKeybind();
        loadConfig();
    }

    private void loadConfig() {
        holdMode = Config.getBool(baseConfig + ".holdMode", true);
        baseZoomDistance = Float.parseFloat(Config.get(baseConfig + ".zoomDistance", "4.0"));
        scrollToZoom = Config.getBool(baseConfig + ".scrollToZoom", true);
        smoothTransition = Config.getBool(baseConfig + ".smoothTransition", true);
        cinematicCamera = Config.getBool(baseConfig + ".cinematicCamera", false);
    }

    private void migrateKeybind() {
        if (!Config.getBool(baseConfig + ".keybindV2", false)) {
            Config.setProperty(baseConfig + ".keybind", String.valueOf(GLFW.GLFW_KEY_Z));
            Config.setProperty(baseConfig + ".keybindV2", "true");
        }
    }

    private int getKeybind() {
        return Config.getInt(baseConfig + ".keybind", GLFW.GLFW_KEY_Z);
    }

    private void setKeybind(int key) {
        Config.setProperty(baseConfig + ".keybind", String.valueOf(key));
    }

    /**
     * True when the zoom effect is visually active — either the key is held,
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
     * @param partialTick fractional tick progress (0.0–1.0) within the current render frame
     */
    public float getInterpolatedFovMultiplier(float partialTick) {
        return Mth.lerp(partialTick, previousFovMultiplier, currentFovMultiplier);
    }

    /**
     * Called by {@code MouseHandlerExtMixin} when the player scrolls while zoomed.
     * Adjusts the scroll offset to increase or decrease zoom level.
     *
     * @param delta raw vertical scroll delta (positive = scroll up = zoom in more)
     */
    public void handleScroll(double delta) {
        if (!zoomEngaged) return;
        float direction = (float) Math.signum(delta);
        float speed = scrollOffset <= -1.5f ? 0.05f : 0.25f;
        float scrollDelta = speed * direction;
        float newDistance = baseZoomDistance + scrollOffset + scrollDelta;
        if (newDistance >= MIN_ZOOM_DISTANCE && newDistance <= MAX_ZOOM_DISTANCE) {
            scrollOffset += scrollDelta;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        int key = getKeybind();
        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            boolean down = KeybindUtil.isKeyDown(key);
            if (holdMode) {
                setZoomState(down);
            } else {
                if (down && !keyWasDown) setZoomState(!zoomEngaged);
            }
            keyWasDown = down;
        }

        float effectiveDistance = baseZoomDistance + scrollOffset;
        float target = zoomEngaged ? (1.0f / effectiveDistance) : 1.0f;
        if (smoothTransition) {
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

        if (engaged && cinematicCamera) {
            savedSmoothCamera = mc.options.smoothCamera;
            mc.options.smoothCamera = true;
        } else if (!engaged && cinematicCamera) {
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
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##zoomEnabled", isActive())) toggle();

        if (isActive() && zoomEngaged) {
            float effectiveDistance = baseZoomDistance + scrollOffset;
            ImGui.text(String.format("Zoom: %.1fx (FOV mult: %.3f)", effectiveDistance, currentFovMultiplier));
        }

        ImGui.separator();

        if (ImGui.checkbox("Hold Mode##zoomHold", holdMode)) {
            holdMode = !holdMode;
            Config.setProperty(baseConfig + ".holdMode", String.valueOf(holdMode));
        }
        if (ImGui.isItemHovered())
            ImGui.setTooltip("Hold: zoom while key held\nToggle: press to zoom, press again to unzoom");

        float[] dist = {baseZoomDistance};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderFloat("Zoom Distance##zoomDist", dist, 2.0f, 20.0f, "%.1fx")) {
            baseZoomDistance = dist[0];
            Config.setProperty(baseConfig + ".zoomDistance", String.valueOf(baseZoomDistance));
        }
        if (ImGui.isItemHovered())
            ImGui.setTooltip("FOV divisor. 4x means FOV is divided by 4.");

        if (ImGui.checkbox("Scroll to Zoom##zoomScroll", scrollToZoom)) {
            scrollToZoom = !scrollToZoom;
            Config.setProperty(baseConfig + ".scrollToZoom", String.valueOf(scrollToZoom));
        }
        if (ImGui.isItemHovered())
            ImGui.setTooltip("Use mouse scroll to fine-tune zoom level while zoomed.");

        if (ImGui.checkbox("Smooth Transition##zoomSmooth", smoothTransition)) {
            smoothTransition = !smoothTransition;
            Config.setProperty(baseConfig + ".smoothTransition", String.valueOf(smoothTransition));
        }

        if (ImGui.checkbox("Cinematic Camera##zoomCinematic", cinematicCamera)) {
            cinematicCamera = !cinematicCamera;
            Config.setProperty(baseConfig + ".cinematicCamera", String.valueOf(cinematicCamera));
        }
        if (ImGui.isItemHovered())
            ImGui.setTooltip("Enable smooth mouse movement while zoomed (like F8).");

        ImGui.separator();

        int result = KeybindUtil.renderKeybindButton("Zoom Key", "zoomKeybind", getKeybind(), awaitingKeybind);
        if (result == KeybindUtil.START_LISTENING) {
            awaitingKeybind = true;
        } else if (result == KeybindUtil.CLEAR_BIND) {
            setKeybind(GLFW.GLFW_KEY_UNKNOWN);
            awaitingKeybind = false;
        } else if (result != KeybindUtil.NO_CHANGE) {
            setKeybind(result);
            awaitingKeybind = false;
        }

        ImGui.end();
    }
}
