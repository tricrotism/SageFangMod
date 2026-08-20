package com.tricrotism.modules.freecam;

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
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam detaches the render camera from the player and flies it with
 * WASD/space/shift + mouse, while the player body stays frozen in place both
 * client-side and server-side.
 * <p>
 * The camera detaches as soon as the module is active (menu checkbox or keybind).
 * <p>
 * Camera position/rotation are applied by {@code FreecamCameraMixin} (reading
 * {@link #getCameraPos()} / {@link #getCameraYaw()} / {@link #getCameraPitch()});
 * mouse rotation is fed in by {@code MouseInputMixin} via
 * {@link #handleMouseDelta(double, double)}; and outbound movement packets are
 * suppressed by {@code ConnectionMixin} while {@link #isEngaged()} so the server
 * keeps you at the detach point.
 */
public final class Freecam extends Module {

    public static final Freecam instance = new Freecam();

    private final Settings.Decimal flySpeed = decimal("Speed", "speed", "Freecam fly speed", 0.6, 0.1, 3.0);

    private final Settings.Key keybind = key("Freecam Key", "keybind", "Activation key", GLFW.GLFW_KEY_F4);

    private static final float TURN_MULTIPLIER = 0.15f;
    /**
     * Per-tick velocity retention. Lower is snappier, higher glides longer.
     */
    private static final double FRICTION = 0.82;
    /**
     * Below this the camera is treated as stopped, so it never creeps.
     */
    private static final double VELOCITY_EPSILON = 1e-4;

    private boolean engaged;
    private double camX, camY, camZ;
    private double prevCamX, prevCamY, prevCamZ;
    private double velX, velY, velZ;
    @Getter private float cameraYaw;
    @Getter private float cameraPitch;

    private Vec3 frozenPos;
    private float frozenBodyYaw;
    private float frozenBodyPitch;
    private CameraType savedPerspective;

    private boolean keyWasDown;

    private Freecam() {
        super("freecam", "Freecam", "Detach the camera and fly it freely; your body stays put.", Category.RENDER);
    }

    /**
     * True when the camera is detached from the player.
     */
    public boolean isEngaged() {
        return isActive() && engaged;
    }

    /**
     * The fly step lands once per tick, so the render camera interpolates between the
     * previous and current position. Otherwise the view steps at 20 Hz.
     */
    public Vec3 getCameraPos(float partialTicks) {
        return new Vec3(
            Mth.lerp(partialTicks, prevCamX, camX),
            Mth.lerp(partialTicks, prevCamY, camY),
            Mth.lerp(partialTicks, prevCamZ, camZ));
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
        handleToggleKey();

        if (!isActive()) {
            if (engaged) disengage();
            return;
        }
        // Engaging here rather than in onActivate covers being enabled outside a world.
        if (!engaged) engage();
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
        int key = keybind.get();
        if (key == GLFW.GLFW_KEY_UNKNOWN) return;
        boolean down = KeybindUtil.isKeyDown(key);
        if (down && !keyWasDown) toggle();
        keyWasDown = down;
    }

    private void engage() {
        if (mc.player == null) return;
        engaged = true;
        Vec3 eye = mc.player.getEyePosition();
        camX = prevCamX = eye.x;
        camY = prevCamY = eye.y;
        camZ = prevCamZ = eye.z;
        velX = velY = velZ = 0;
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
     * <p>
     * The movement keys still drive vanilla's own input handling while they fly the camera,
     * so the body's crouch/sprint state is reset here too. Otherwise you watch your own
     * ghost sneak and sprint in place. This runs after the tick, so it is the state the
     * next frame renders.
     */
    private void freezePlayer() {
        if (mc.player == null || frozenPos == null) return;
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
        mc.player.setOldPosAndRot(frozenPos, frozenBodyYaw, frozenBodyPitch);
        mc.player.setYRot(frozenBodyYaw);
        mc.player.setXRot(frozenBodyPitch);

        mc.player.input.keyPresses = Input.EMPTY;
        mc.player.setShiftKeyDown(false);
        mc.player.setSprinting(false);
        mc.player.setPose(Pose.STANDING);
    }

    private void flyCamera() {
        prevCamX = camX;
        prevCamY = camY;
        prevCamZ = camZ;

        var opts = mc.options;
        double rad = Math.toRadians(cameraYaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double dx = 0, dy = 0, dz = 0;
        if (opts.keyUp.isDown()) {
            dx += -sin;
            dz += cos;
        }
        if (opts.keyDown.isDown()) {
            dx += sin;
            dz += -cos;
        }
        if (opts.keyRight.isDown()) {
            dx += -cos;
            dz += -sin;
        }
        if (opts.keyLeft.isDown()) {
            dx += cos;
            dz += sin;
        }
        if (opts.keyJump.isDown()) dy += 1;
        if (opts.keyShift.isDown()) dy -= 1;

        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-6) {
            dx /= len;
            dy /= len;
            dz /= len;
        }

        double accel = flySpeed.get().doubleValue() * (1 - FRICTION);
        velX = velX * FRICTION + dx * accel;
        velY = velY * FRICTION + dy * accel;
        velZ = velZ * FRICTION + dz * accel;

        if (Math.abs(velX) < VELOCITY_EPSILON) velX = 0;
        if (Math.abs(velY) < VELOCITY_EPSILON) velY = 0;
        if (Math.abs(velZ) < VELOCITY_EPSILON) velZ = 0;

        camX += velX;
        camY += velY;
        camZ += velZ;
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

        flySpeed.render();

        ImGui.separator();

        keybind.render();

        ImGui.end();
    }
}
