package com.tricrotism.modules.freelook;

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
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * LabyMod-style free look — look around freely without changing player
 * movement direction. Switches to third-person while active.
 * <p>
 * Camera rotation is overridden via {@code CameraMixin} which reads
 * {@link #getCameraYaw()} / {@link #getCameraPitch()}.
 * Player body rotation is suppressed via {@code MouseHandlerExtMixin}
 * which calls {@link #handleMouseDelta(double, double)}.
 */
public class FreeLook extends Module implements Menu {

    public static final FreeLook instance = new FreeLook();

    private static final float TURN_MULTIPLIER = 0.15f;

    private boolean holdMode;

    private boolean freeLookEngaged;
    /**
     * -- GETTER --
     * Camera yaw in degrees, updated by mouse input while engaged.
     */
    @Getter private float cameraYaw;
    /**
     * -- GETTER --
     * Camera pitch in degrees, clamped to [-90, 90].
     */
    @Getter private float cameraPitch;
    private CameraType savedPerspective;

    private boolean awaitingKeybind;
    private boolean keyWasDown;

    public FreeLook() {
        super("freelook", "Free Look", "Look around without changing movement direction.", "Visual");
        migrateKeybind();
        holdMode = Config.getBool(baseConfig + ".holdMode", true);
    }

    private int getKeybind() {
        return Config.getInt(baseConfig + ".keybind", GLFW.GLFW_KEY_C);
    }

    private void setKeybind(int key) {
        Config.setProperty(baseConfig + ".keybind", String.valueOf(key));
    }

    private void migrateKeybind() {
        if (!Config.getBool(baseConfig + ".keybindV2", false)) {
            Config.setProperty(baseConfig + ".keybind", String.valueOf(GLFW.GLFW_KEY_C));
            Config.setProperty(baseConfig + ".keybindV2", "true");
        }
    }

    /**
     * True when the camera is decoupled from the player body.
     */
    public boolean isFreeLookEngaged() {
        return isActive() && freeLookEngaged;
    }

    /**
     * Called by {@code MouseHandlerExtMixin} instead of {@code LocalPlayer.turn()}.
     * Accumulates mouse movement onto the free camera angles.
     *
     * @param yRot horizontal mouse delta (pre-sensitivity, pre-turn multiplier)
     * @param xRot vertical mouse delta
     */
    public void handleMouseDelta(double yRot, double xRot) {
        cameraYaw += (float) (yRot * TURN_MULTIPLIER);
        cameraPitch = Mth.clamp(cameraPitch + (float) (xRot * TURN_MULTIPLIER), -90f, 90f);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        int key = getKeybind();
        if (key == GLFW.GLFW_KEY_UNKNOWN) return;

        boolean down = KeybindUtil.isKeyDown(key);
        if (holdMode) {
            if (down && !freeLookEngaged) engage();
            else if (!down && freeLookEngaged) disengage();
        } else {
            if (down && !keyWasDown) {
                if (freeLookEngaged) disengage();
                else engage();
            }
        }
        keyWasDown = down;
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        if (freeLookEngaged) disengage();
    }

    private void engage() {
        if (mc.player == null) return;
        freeLookEngaged = true;
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();
        savedPerspective = mc.options.getCameraType();
        if (savedPerspective == CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private void disengage() {
        freeLookEngaged = false;
        if (savedPerspective != null) {
            mc.options.setCameraType(savedPerspective);
            savedPerspective = null;
        }
    }

    @Override
    public void onActivate() {
        SageFang.LOGGER.info("[FreeLook] Activated");
    }

    @Override
    public void onDeactivate() {
        if (freeLookEngaged) disengage();
        SageFang.LOGGER.info("[FreeLook] Deactivated");
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##freelookEnabled", isActive())) toggle();

        if (isActive() && freeLookEngaged) {
            ImGui.text(String.format("Yaw: %.1f  Pitch: %.1f", cameraYaw, cameraPitch));
        }

        ImGui.separator();

        if (ImGui.checkbox("Hold Mode##freelookHold", holdMode)) {
            holdMode = !holdMode;
            Config.setProperty(baseConfig + ".holdMode", String.valueOf(holdMode));
        }
        if (ImGui.isItemHovered())
            ImGui.setTooltip("Hold: free look while key held\nToggle: press to engage, press again to disengage");

        ImGui.separator();

        int result = KeybindUtil.renderKeybindButton("Free Look Key", "freelookKeybind", getKeybind(), awaitingKeybind);
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
