package com.tricrotism.modules.freelook;

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
import lombok.Getter;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * LabyMod-style free look. Look around without changing player
 * movement direction. Switches to third-person while active.
 * <p>
 * Camera rotation is overridden via {@code FreeLookCameraMixin} which reads
 * {@link #getCameraYaw()} / {@link #getCameraPitch()}.
 * Player body rotation is suppressed via {@code MouseInputMixin}
 * which calls {@link #handleMouseDelta(double, double)}.
 */
public class FreeLook extends Module {

    public static final FreeLook instance = new FreeLook();

    private final Settings.Key keybind = key("Free Look Key", "keybind", "Activation key", GLFW.GLFW_KEY_C);

    private final Settings.Bool holdMode =
        bool("Hold Mode", "holdMode", "Hold: free look while key held; toggle otherwise", true);

    private static final float TURN_MULTIPLIER = 0.15f;


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

    private boolean keyWasDown;

    public FreeLook() {
        super("freelook", "Free Look", "Look around without changing movement direction.", Category.RENDER);
        migrateKeybind();
    }

    private void migrateKeybind() {
    }

    /**
     * True when the camera is decoupled from the player body.
     */
    public boolean isFreeLookEngaged() {
        return isActive() && freeLookEngaged;
    }

    /**
     * Called by {@code MouseInputMixin} instead of {@code LocalPlayer.turn()}.
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

        int key = keybind.get();
        if (key == GLFW.GLFW_KEY_UNKNOWN) return;

        boolean down = KeybindUtil.isKeyDown(key);
        if (holdMode.get()) {
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
    protected void renderExtra(ImGuiIO io) {
        if (isActive() && freeLookEngaged) {
            ImGui.text(String.format("Yaw: %.1f  Pitch: %.1f", cameraYaw, cameraPitch));
        }
        keybind.render();
    }
}
