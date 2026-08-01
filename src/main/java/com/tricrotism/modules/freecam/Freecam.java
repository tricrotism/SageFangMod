package com.tricrotism.modules.freecam;

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
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam — detaches the render camera from the player and flies it with
 * WASD/space/shift + mouse, while the player body stays frozen in place both
 * client-side and server-side.
 * <p>
 * Camera position/rotation are applied by {@code FreecamCameraMixin} (reading
 * {@link #getCameraPos()} / {@link #getCameraYaw()} / {@link #getCameraPitch()});
 * mouse rotation is fed in by {@code MouseInputMixin} via
 * {@link #handleMouseDelta(double, double)}; and outbound movement packets are
 * suppressed by {@code ConnectionMixin} while {@link #isEngaged()} so the server
 * keeps you at the detach point.
 */
public final class Freecam extends Module implements Menu {

    public static final Freecam instance = new Freecam();

    private static final float TURN_MULTIPLIER = 0.15f;

    private boolean engaged;
    private double camX, camY, camZ;
    @Getter private float cameraYaw;
    @Getter private float cameraPitch;

    private Vec3 frozenPos;
    private float frozenBodyYaw;
    private float frozenBodyPitch;
    private CameraType savedPerspective;
    private float flySpeed;

    private boolean awaitingKeybind;
    private boolean keyWasDown;

    private Freecam() {
        super("freecam", "Freecam", "Detach the camera and fly it freely; your body stays put.", "Visual");
        flySpeed = (float) Config.getInt(baseConfig + ".speed", 60) / 100f;
    }

    /**
     * True when the camera is detached from the player.
     */
    public boolean isEngaged() {
        return isActive() && engaged;
    }

    public Vec3 getCameraPos() {
        return new Vec3(camX, camY, camZ);
    }

    /**
     * Called by {@code MouseInputMixin} instead of {@code LocalPlayer.turn()}.
     * Accumulates mouse movement onto the free camera angles.
     */
    public void handleMouseDelta(double yRot, double xRot) {
        cameraYaw += (float) (yRot * TURN_MULTIPLIER);
        cameraPitch = Mth.clamp(cameraPitch + (float) (xRot * TURN_MULTIPLIER), -90f, 90f);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) {
            if (engaged) disengage();
            return;
        }
        handleToggleKey();
        if (engaged) {
            freezePlayer();
            flyCamera();
        }
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        if (engaged) disengage();
    }

    private void handleToggleKey() {
        int key = getKeybind();
        if (key == GLFW.GLFW_KEY_UNKNOWN) return;
        boolean down = KeybindUtil.isKeyDown(key);
        if (down && !keyWasDown) {
            if (engaged) disengage();
            else engage();
        }
        keyWasDown = down;
    }

    private void engage() {
        if (mc.player == null) return;
        engaged = true;
        Vec3 eye = mc.player.getEyePosition();
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();
        frozenPos = mc.player.position();
        frozenBodyYaw = mc.player.getYRot();
        frozenBodyPitch = mc.player.getXRot();
        savedPerspective = mc.options.getCameraType();
        if (savedPerspective == CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private void disengage() {
        engaged = false;
        frozenPos = null;
        if (savedPerspective != null) {
            mc.options.setCameraType(savedPerspective);
            savedPerspective = null;
        }
    }

    /**
     * Pins the player at the detach point each tick so it never drifts client-side.
     */
    private void freezePlayer() {
        if (mc.player == null || frozenPos == null) return;
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
        mc.player.setOldPosAndRot(frozenPos, frozenBodyYaw, frozenBodyPitch);
        mc.player.setYRot(frozenBodyYaw);
        mc.player.setXRot(frozenBodyPitch);
    }

    private void flyCamera() {
        var opts = mc.options;
        double rad = Math.toRadians(cameraYaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double mx = 0, my = 0, mz = 0;
        if (opts.keyUp.isDown()) {
            mx += -sin;
            mz += cos;
        }
        if (opts.keyDown.isDown()) {
            mx += sin;
            mz += -cos;
        }
        if (opts.keyRight.isDown()) {
            mx += cos;
            mz += sin;
        }
        if (opts.keyLeft.isDown()) {
            mx += -cos;
            mz += -sin;
        }
        if (opts.keyJump.isDown()) my += 1;
        if (opts.keyShift.isDown()) my -= 1;

        camX += mx * flySpeed;
        camY += my * flySpeed;
        camZ += mz * flySpeed;
    }

    private int getKeybind() {
        return Config.getInt(baseConfig + ".keybind", GLFW.GLFW_KEY_F4);
    }

    private void setKeybind(int key) {
        Config.setProperty(baseConfig + ".keybind", String.valueOf(key));
    }

    @Override
    public void onDeactivate() {
        if (engaged) disengage();
        SageFang.LOGGER.info("[Freecam] Deactivated");
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##freecamEnabled", isActive())) toggle();

        if (isEngaged()) {
            ImGui.text(String.format("Pos: %.1f %.1f %.1f", camX, camY, camZ));
        }

        ImGui.separator();

        float[] speed = {flySpeed};
        if (ImGui.sliderFloat("Speed##freecamSpeed", speed, 0.1f, 3.0f)) {
            flySpeed = speed[0];
            Config.setProperty(baseConfig + ".speed", String.valueOf(Math.round(flySpeed * 100)));
        }

        ImGui.separator();

        int result = KeybindUtil.renderKeybindButton("Freecam Key", "freecamKeybind", getKeybind(), awaitingKeybind);
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
