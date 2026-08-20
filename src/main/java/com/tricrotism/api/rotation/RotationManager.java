package com.tricrotism.api.rotation;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.events.world.TickEvent;
import io.avaje.config.Config;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side rotations: what the client transmits in its movement packets, decoupled from where the
 * camera actually points. This is the shared primitive under aura, scaffold, nuker and auto-place.
 * The server derives every interaction raytrace from the transmitted rotation, so a module that wants
 * an interaction to be geometrically valid has to move the transmitted rotation, not the camera.
 * <p>
 * Modules must call {@link #request} from {@link TickEvent.Pre}. {@code LocalPlayer.sendPosition()}
 * runs inside {@code Minecraft.tick()}, so a request made from {@link TickEvent.Post} would not be
 * transmitted until the following tick.
 * <p>
 * {@link #setMaxStep(float)} bounds how far the transmitted rotation may move per tick. It is the
 * main knob to sweep when testing an aim check: at 180° the rotation snaps instantly, which is
 * trivially detectable; small steps trade detectability for time-to-target.
 */
public final class RotationManager {

    public static final RotationManager instance = new RotationManager();

    private static final String MAX_STEP_KEY = "rotation.maxStep";
    private static final String QUANTIZE_KEY = "rotation.quantize";

    private static volatile float targetYaw;
    private static volatile float targetPitch;
    private static volatile int priority = Integer.MIN_VALUE;
    private static volatile boolean requested;

    /**
     * Last rotation handed to the movement packet, and the anchor the next step interpolates from.
     */
    private static float sentYaw;
    private static float sentPitch;
    private static boolean anchored;

    private static boolean resolvedThisTick;
    @Getter private static float maxStep = (float) Double.parseDouble(Config.get(MAX_STEP_KEY, "180.0"));
    @Getter private static boolean quantize = Config.getBool(QUANTIZE_KEY, false);

    private RotationManager() {}

    /**
     * Asks for {@code yaw}/{@code pitch} to be transmitted this tick. The highest priority request in
     * a tick wins; ties go to the first caller.
     */
    public static void request(float yaw, float pitch, int requestPriority) {
        if (requested && requestPriority <= priority) return;
        targetYaw = yaw;
        targetPitch = pitch;
        priority = requestPriority;
        requested = true;
    }

    /**
     * Whether a spoofed rotation should replace the real one in this tick's movement packet.
     */
    public static boolean isTransmitting() {
        return requested;
    }

    public static float yaw() {
        resolve();
        return sentYaw;
    }

    public static float pitch() {
        resolve();
        return sentPitch;
    }

    /**
     * Steps the transmitted rotation toward the request, at most {@link #maxStep} degrees. Runs once
     * per tick. {@code sendPosition()} reads the rotation several times and every read has to agree,
     * or the delta test and the {@code yRotLast} bookkeeping disagree with the packet that was sent.
     */
    private static void resolve() {
        if (resolvedThisTick || !requested) return;
        resolvedThisTick = true;

        if (!anchored) {
            sentYaw = targetYaw;
            sentPitch = targetPitch;
            anchored = true;
            return;
        }

        sentYaw = step(sentYaw, targetYaw);
        sentPitch = Mth.clamp(step(sentPitch, targetPitch), -90f, 90f);
    }

    private static float step(float from, float to) {
        float delta = Mth.wrapDegrees(to - from);
        delta = Mth.clamp(delta, -maxStep, maxStep);
        if (quantize) {
            double lattice = latticeStep();
            delta = (float) (Math.round(delta / lattice) * lattice);
        }
        return from + delta;
    }

    /**
     * The smallest rotation a mouse can produce, which is vanilla's own sensitivity curve: one count
     * of movement turns the player by this much, and every real rotation is a whole number of them.
     */
    private static double latticeStep() {
        double f = Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2;
        return f * f * f * 8.0 * 0.15;
    }

    /**
     * Drops the request and remembers the transmitted rotation, so the next step interpolates from
     * where the server last saw us rather than snapping.
     */
    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (requested) {
            resolve();
        } else {
            anchored = false;
        }
        requested = false;
        priority = Integer.MIN_VALUE;
        resolvedThisTick = false;
    }

    /**
     * Yaw, in degrees, that points from {@code from} to {@code to}.
     */
    public static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
    }

    /**
     * Pitch, in degrees, that points from {@code from} to {@code to}.
     */
    public static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) -Math.toDegrees(Math.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)));
    }

    public static void setQuantize(boolean enabled) {
        quantize = enabled;
        Config.setProperty(QUANTIZE_KEY, String.valueOf(enabled));
    }

    public static void setMaxStep(float degrees) {
        maxStep = Mth.clamp(degrees, 0.1f, 180f);
        Config.setProperty(MAX_STEP_KEY, String.valueOf(maxStep));
    }
}
