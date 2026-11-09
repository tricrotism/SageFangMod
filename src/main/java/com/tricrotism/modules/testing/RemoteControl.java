package com.tricrotism.modules.testing;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Setting;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.mixin.accessors.HudAccessor;
import com.tricrotism.modules.profiler.ReloadProfiler;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.*;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A loopback line protocol for driving the client from a script.
 * <p>
 * The other testing modules answer questions a person asks by hand. This one lets an external
 * harness ask them: toggle a module, move a slider, hold a key for a number of ticks, look
 * somewhere, join a server, and read the player's state back. The point is a false-positive soak
 * that a script can repeat identically after every threshold change, which a hand at the keyboard
 * cannot.
 * <p>
 * Every command runs on the render thread and answers one line. Key holds are tick-accurate: a
 * {@code script} is a queue of key sets with tick counts, advanced from the client tick rather than
 * from the socket's clock, so the movement it produces is the movement a vanilla client would
 * produce from the same presses.
 * <p>
 * Driving a server-side vehicle needs more than presses, so there are four readbacks the harness
 * cannot get any other way: {@code vehicle} for what the client believes it is riding,
 * {@code entities} for what the server actually spawned nearby, {@code messages} for chat and
 * action bar text, and {@code screenshot} for whether a model is on screen at all. Mouse piloting
 * gets {@code sweep}, which moves the look angle by a fixed step per tick, because a server reading
 * the view as a control stick needs motion rather than one jump to an angle.
 * <p>
 * Bound to the loopback address only and off unless {@code remote.port} is set in the config, since
 * anything that can toggle modules can also make the account look like a cheat.
 */
public final class RemoteControl {

    private static final int TIMEOUT_SECONDS = 10;
    private static final int MESSAGE_HISTORY = 200;
    private static final Pattern HTTP_REQUEST_LINE = Pattern.compile("[A-Z]+ \\S+ HTTP/\\d.*");

    private final Minecraft mc = Minecraft.getInstance();
    private final Deque<Step> script = new ArrayDeque<>();
    private final Deque<String> messages = new ArrayDeque<>();
    private Step current;
    private int remaining;
    private float sweepYaw;
    private float sweepPitch;
    private int sweepTicks;
    private double[] smoothSamples = new double[0];
    private int smoothFrames;
    private int smoothTarget;
    private int smoothModel = -1;

    private static final com.sun.management.ThreadMXBean THREADS =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    // Render thread only: perf runs through mc.submit and the samples come from the frame hook.
    private long[] perfNanos = new long[0];
    private int perfFrames;
    private int perfTarget;
    private long perfLastNanos;
    private long perfStartNanos;
    private long perfStartAllocated;
    private long[] perfStartGc = new long[0];
    private String perfReport = "";

    private record Step(List<KeyMapping> keys, int ticks) {}

    private RemoteControl() {}

    public static void start(int port) {
        RemoteControl control = new RemoteControl();
        SageFang.EVENT_BUS.subscribe(control);
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
            control.record(overlay ? "bar" : "sys", message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, bound, received) ->
            control.record("chat", message.getString()));
        LevelExtractionEvents.END_EXTRACTION.register(context -> control.frame());
        Thread thread = new Thread(() -> control.serve(port), "sagefang-remote");
        thread.setDaemon(true);
        thread.start();
    }

    private void serve(int port) {
        try (ServerSocket server = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            SageFang.LOGGER.info("Remote control listening on {}", server.getLocalSocketAddress());
            while (true) {
                try (Socket socket = server.accept()) {
                    session(socket);
                } catch (IOException e) {
                    SageFang.LOGGER.warn("Remote control session ended: {}", e.toString());
                }
            }
        } catch (IOException e) {
            SageFang.LOGGER.error("Remote control failed to bind port {}", port, e);
        }
    }

    private void session(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        String line;
        try {
            while ((line = in.readLine()) != null) {
                String command = line.trim();
                if (command.isEmpty()) continue;
                // Binding to loopback doesn't stop a web page. A cross-origin POST to 127.0.0.1 would
                // run every body line as a command, and a browser always opens with an HTTP request line.
                if (HTTP_REQUEST_LINE.matcher(command).matches()) {
                    SageFang.LOGGER.warn("Remote control refused an HTTP request: {}", command);
                    return;
                }
                out.println(execute(command).replace('\n', ' '));
            }
        } finally {
            // A harness that dies mid-script must not leave keys held on the server.
            mc.execute(this::stop);
        }
    }

    private String execute(String command) {
        try {
            return mc.submit(() -> handle(command)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            return "error " + cause;
        }
    }

    private String handle(String line) {
        String[] parts = line.split("\\s+", 2);
        String verb = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1] : "";
        return switch (verb) {
            case "ping" -> "pong";
            case "modules" -> modules();
            case "module" -> module(rest);
            case "settings" -> settings(rest);
            case "set" -> set(rest);
            case "keys" -> keys(rest);
            case "click" -> click(rest);
            case "script" -> script(rest);
            case "busy" -> String.valueOf(remaining + script.stream().mapToInt(Step::ticks).sum());
            case "stop" -> stop();
            case "look" -> look(rest);
            case "sweep" -> sweep(rest);
            case "swing" -> swing();
            case "pos" -> pos();
            case "vehicle" -> vehicle();
            case "entities" -> entities(rest);
            case "messages" -> messages(rest);
            case "actionbar" -> actionBar();
            case "smooth" -> smooth(rest);
            case "perf" -> perf(rest);
            case "reload" -> ReloadProfiler.report();
            case "screenshot" -> screenshot(rest);
            case "chat" -> chat(rest);
            case "connect" -> connect(rest);
            case "disconnect" -> disconnect();
            case "screen" -> mc.level == null ? "menu" : "world";
            case "close" -> close();
            case "perspective" -> perspective(rest);
            case "respawn" -> respawn();
            case "state" -> state();
            case "useblock" -> useBlock(rest);
            case "slot" -> slot(rest);
            case "held" -> mc.player == null ? "none" : mc.player.getMainHandItem().toString();
            case "container" -> container();
            case "clickslot" -> clickSlot(rest);
            default -> "error unknown command";
        };
    }

    private String modules() {
        StringJoiner out = new StringJoiner(" ");
        for (Module module : Module.getRegistry()) {
            out.add(module.id + "=" + (module.isActive() ? "on" : "off"));
        }
        return out.toString();
    }

    private Module find(String id) {
        for (Module module : Module.getRegistry()) {
            if (module.id.equalsIgnoreCase(id)) return module;
        }
        return null;
    }

    private String module(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length < 2) return "error usage: module <id> on|off|toggle";
        Module module = find(args[0]);
        if (module == null) return "error no module " + args[0];
        boolean want = switch (args[1]) {
            case "on" -> true;
            case "off" -> false;
            default -> !module.isActive();
        };
        if (module.isActive() != want) module.toggle();
        return module.id + "=" + (module.isActive() ? "on" : "off");
    }

    private String settings(String id) {
        Module module = find(id.trim());
        if (module == null) return "error no module " + id;
        StringJoiner out = new StringJoiner(" ");
        for (Setting<?> setting : module.getSettings()) {
            String key = setting.key().substring(setting.key().lastIndexOf('.') + 1);
            Object value = setting instanceof Settings.Mode mode ? mode.get() + ":" + mode.option() : setting.get();
            out.add(key + "=" + value);
        }
        return out.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String set(String rest) {
        String[] args = rest.split("\\s+", 3);
        if (args.length < 3) return "error usage: set <module> <key> <value>";
        Module module = find(args[0]);
        if (module == null) return "error no module " + args[0];
        for (Setting setting : module.getSettings()) {
            if (!setting.key().endsWith("." + args[1])) continue;
            Object value = switch (setting) {
                case Settings.Bool ignored -> Boolean.parseBoolean(args[2]);
                case Settings.Int ignored -> Integer.parseInt(args[2]);
                case Settings.Mode ignored -> Integer.parseInt(args[2]);
                case Settings.Key ignored -> Integer.parseInt(args[2]);
                case Settings.Decimal ignored -> Double.parseDouble(args[2]);
                default -> args[2];
            };
            setting.set(value);
            return args[1] + "=" + setting.get();
        }
        return "error no setting " + args[1];
    }

    private Map<String, KeyMapping> keyMap() {
        return Map.of(
            "forward", mc.options.keyUp,
            "back", mc.options.keyDown,
            "left", mc.options.keyLeft,
            "right", mc.options.keyRight,
            "jump", mc.options.keyJump,
            "sneak", mc.options.keyShift,
            "sprint", mc.options.keySprint,
            "attack", mc.options.keyAttack,
            "use", mc.options.keyUse);
    }

    private List<KeyMapping> parseKeys(String spec) {
        List<KeyMapping> keys = new ArrayList<>();
        if (spec.equals("none")) return keys;
        Map<String, KeyMapping> map = keyMap();
        for (String name : spec.split("\\+")) {
            KeyMapping key = map.get(name);
            if (key == null) throw new IllegalArgumentException("no key " + name);
            keys.add(key);
        }
        return keys;
    }

    private void apply(List<KeyMapping> keys) {
        for (KeyMapping key : keyMap().values()) key.setDown(keys.contains(key));
    }

    private String keys(String spec) {
        script.clear();
        current = null;
        remaining = 0;
        apply(parseKeys(spec.trim()));
        return "ok";
    }

    private String click(String name) {
        KeyMapping key = keyMap().get(name.trim());
        if (key == null) return "error no key " + name;
        KeyMapping.click(KeyMappingHelper.getBoundKeyOf(key));
        return "ok";
    }

    private String script(String spec) {
        Deque<Step> steps = new ArrayDeque<>();
        for (String step : spec.trim().split(",")) {
            String[] pair = step.split(":");
            if (pair.length != 2) return "error step " + step + " wants keys:ticks";
            steps.add(new Step(parseKeys(pair[0]), Integer.parseInt(pair[1])));
        }
        script.addAll(steps);
        return "queued " + steps.size();
    }

    private String stop() {
        script.clear();
        current = null;
        remaining = 0;
        sweepTicks = 0;
        apply(List.of());
        return "ok";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (sweepTicks > 0 && mc.player != null) {
            sweepTicks--;
            mc.player.setYRot(mc.player.getYRot() + sweepYaw);
            mc.player.setXRot(Math.clamp(mc.player.getXRot() + sweepPitch, -90.0f, 90.0f));
        }

        if (remaining > 0) {
            remaining--;
            if (remaining > 0) return;
        }
        Step next = script.poll();
        if (next == null) {
            if (current != null) {
                current = null;
                apply(List.of());
            }
            return;
        }
        current = next;
        remaining = next.ticks();
        apply(next.keys());
    }

    private String look(String rest) {
        if (mc.player == null) return "error no player";
        String[] args = rest.trim().split("\\s+");
        if (args.length != 2) return "error usage: look <yaw> <pitch>";
        mc.player.setYRot(Float.parseFloat(args[0]));
        mc.player.setXRot(Float.parseFloat(args[1]));
        return "ok";
    }

    /**
     * Turns the view by a fixed step every tick for a number of ticks.
     * <p>
     * A server that reads the view as a control stick reads the offset from where the thing being
     * driven is already pointing, so one jump to an absolute angle is a single frame of deflection
     * that the deadzone may swallow whole. Holding a stick over means moving the mouse, and this is
     * that motion: pitch is clamped the way the game clamps it, yaw is left to wrap.
     */
    private String sweep(String rest) {
        String[] args = rest.trim().split("\\s+");
        if (args.length != 3) return "error usage: sweep <yaw-per-tick> <pitch-per-tick> <ticks>";
        sweepYaw = Float.parseFloat(args[0]);
        sweepPitch = Float.parseFloat(args[1]);
        sweepTicks = Integer.parseInt(args[2]);
        return "ok";
    }

    /**
     * Swings the main hand, which is the packet a left click on nothing sends.
     * <p>
     * The attack keybind route goes through the click counter the frame loop consumes, the same
     * reason {@link #useBlock} calls the interaction directly. A server that triggers on a left
     * click sees this as one.
     */
    private String swing() {
        if (mc.player == null) return "error no player";
        mc.player.swing(InteractionHand.MAIN_HAND);
        return "ok";
    }

    /**
     * Dismisses whatever screen is open, which is usually the pause menu.
     * <p>
     * A window nobody is clicking in loses focus and the game pauses itself over the world, so a
     * harness that only ever drives from a socket takes screenshots of the pause menu until it says
     * this. Answers what it closed, so a caller can tell a dismissal from a no-op.
     */
    private String close() {
        if (mc.gui.screen() == null) return "none";
        String name = mc.gui.screen().getClass().getSimpleName();
        mc.gui.setScreen(null);
        return "closed " + name;
    }

    /**
     * {@code perspective [first|back|front]} sets the camera the way F5 cycles it, or with no
     * argument names the current one.
     */
    private String perspective(String rest) {
        String name = rest.trim().toLowerCase(Locale.ROOT);
        if (!name.isEmpty()) {
            CameraType type = switch (name) {
                case "first" -> CameraType.FIRST_PERSON;
                case "back" -> CameraType.THIRD_PERSON_BACK;
                case "front" -> CameraType.THIRD_PERSON_FRONT;
                default -> null;
            };
            if (type == null) return "error usage: perspective [first|back|front]";
            mc.options.setCameraType(type);
        }
        return mc.options.getCameraType().name().toLowerCase(Locale.ROOT);
    }

    private String pos() {
        if (mc.player == null) return "none";
        Vec3 p = mc.player.position();
        Vec3 v = mc.player.getDeltaMovement();
        return String.format(Locale.ROOT,
            "%.5f %.5f %.5f yaw=%.2f pitch=%.2f ground=%b v=%.5f,%.5f,%.5f sprint=%b sneak=%b hp=%.1f",
            p.x, p.y, p.z, mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(),
            v.x, v.y, v.z, mc.player.isSprinting(), mc.player.isShiftKeyDown(), mc.player.getHealth());
    }

    /**
     * What the client believes it is riding.
     * <p>
     * The position and rotation here are the ones the client was sent, so they are the only honest
     * answer to whether a server-side movement model arrived. {@link #pos} reports the player, who
     * is carried along and so says nothing about the ride.
     */
    private String vehicle() {
        if (mc.player == null) return "error no player";
        Entity mount = mc.player.getVehicle();
        if (mount == null) return "none";

        StringJoiner riders = new StringJoiner(",");
        riders.setEmptyValue("none");
        for (Entity rider : mount.getPassengers()) riders.add(rider.getId() + ":" + rider.getName().getString());

        Vec3 p = mount.position();
        Vec3 v = mount.getDeltaMovement();
        return String.format(Locale.ROOT,
            "id=%d type=%s %.4f %.4f %.4f yaw=%.2f pitch=%.2f v=%.4f,%.4f,%.4f riders=%s",
            mount.getId(), mount.getType().toShortString(), p.x, p.y, p.z,
            mount.getYRot(), mount.getXRot(), v.x, v.y, v.z, riders);
    }

    /**
     * Nearby entities as {@code id:type:x,y,z}, optionally narrowed to types containing a substring.
     * <p>
     * Reads the render list, so it sees exactly what the client was told exists. That is what tells
     * a mount apart from the display entities a model library hangs off it, and an invisible model
     * apart from one that was never spawned.
     */
    private String entities(String rest) {
        if (mc.player == null || mc.level == null) return "error no player";
        String spec = rest.trim();
        String[] args = spec.isEmpty() ? new String[0] : spec.split("\\s+");
        double radius = args.length > 0 ? Double.parseDouble(args[0]) : 32.0;
        String filter = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        double furthest = radius * radius;

        StringJoiner out = new StringJoiner(" ");
        out.setEmptyValue("none");
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || entity.distanceToSqr(mc.player) > furthest) continue;
            String type = entity.getType().toShortString();
            if (!filter.isEmpty() && !type.toLowerCase(Locale.ROOT).contains(filter)) continue;
            Vec3 p = entity.position();
            out.add(String.format(Locale.ROOT, "%d:%s:%.2f,%.2f,%.2f", entity.getId(), type, p.x, p.y, p.z));
        }
        return out.toString();
    }

    private static final int SMOOTH_STRIDE = 9;

    /**
     * Records what the rider sees, one sample per rendered frame, then reports how steady it was.
     * <p>
     * {@code smooth <frames> [entity-id]} starts a recording, {@code smooth} reads it back. A tick
     * readback cannot see stutter: the server moves a ride twenty times a second and everything
     * between those moves is client interpolation, which is exactly where a ride looks rough or not.
     * So this samples the camera every frame, and optionally one entity's rendered position relative
     * to it, which is how a model shaking against the viewer shows up.
     * <p>
     * The report: {@code speed} mean blocks per second and its coefficient of variation (0 is glass
     * smooth), {@code frozen} frames that did not move at all, {@code jerk} the 95th percentile and
     * worst frame-to-frame change of velocity, {@code offset} the entity's mean position relative to
     * the camera, and {@code shake} the spread and worst per-frame jump of that offset. {@code view}
     * is where the entity sits in the frame, in degrees right and up of the crosshair, and
     * {@code drift} how far it wanders from there (RMS and worst): a chase camera that holds its
     * subject still on screen reads near zero even while the mouse sweeps.
     */
    private String smooth(String rest) {
        String[] args = rest.trim().isEmpty() ? new String[0] : rest.trim().split("\\s+");
        if (args.length == 0) {
            return smoothFrames < smoothTarget ? "recording " + smoothFrames + "/" + smoothTarget : smoothReport();
        }
        smoothTarget = Integer.parseInt(args[0]);
        smoothModel = args.length > 1 ? Integer.parseInt(args[1]) : -1;
        smoothSamples = new double[smoothTarget * SMOOTH_STRIDE];
        smoothFrames = 0;
        return "recording";
    }

    /**
     * {@code perf <frames>} records that many frame times, {@code perf} reads them back. The report
     * gives frame-time percentiles in milliseconds, megabytes allocated per second across every
     * thread, and per-collector GC counts and milliseconds over the same window. It says how much a
     * change moved the numbers, not where the time went. Attach JFR for that.
     */
    private String perf(String rest) {
        String spec = rest.trim();
        if (spec.isEmpty()) {
            if (perfTarget == 0) return "error no recording";
            return perfFrames < perfTarget ? "recording " + perfFrames + "/" + perfTarget : perfReport;
        }
        perfTarget = Integer.parseInt(spec);
        perfNanos = new long[perfTarget];
        perfFrames = 0;
        perfLastNanos = 0;
        perfStartNanos = System.nanoTime();
        perfStartAllocated = THREADS.getTotalThreadAllocatedBytes();
        perfStartGc = gcTotals();
        return "recording";
    }

    private void perfFrame() {
        long now = System.nanoTime();
        if (perfLastNanos != 0) perfNanos[perfFrames++] = now - perfLastNanos;
        perfLastNanos = now;
        if (perfFrames == perfTarget) perfReport = perfReport(now);
    }

    private String perfReport(long endNanos) {
        double seconds = (endNanos - perfStartNanos) / 1e9;
        double allocatedMb = (THREADS.getTotalThreadAllocatedBytes() - perfStartAllocated) / 1048576.0;
        long[] gcEnd = gcTotals();
        StringJoiner gc = new StringJoiner(",");
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        for (int i = 0; i < collectors.size(); i++) {
            gc.add(collectors.get(i).getName().replace(' ', '_') + ":" + (gcEnd[i * 2] - perfStartGc[i * 2])
                + "/" + (gcEnd[i * 2 + 1] - perfStartGc[i * 2 + 1]) + "ms");
        }

        long[] sorted = Arrays.copyOf(perfNanos, perfFrames);
        Arrays.sort(sorted);
        return String.format(Locale.ROOT,
            "frames=%d fps=%.1f p50=%.2f p95=%.2f p99=%.2f p999=%.2f max=%.2f allocMBs=%.1f gc=%s",
            perfFrames, perfFrames / seconds, percentile(sorted, 0.50), percentile(sorted, 0.95),
            percentile(sorted, 0.99), percentile(sorted, 0.999), sorted[sorted.length - 1] / 1e6,
            allocatedMb / seconds, gc);
    }

    private static double percentile(long[] sorted, double q) {
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(q * sorted.length))] / 1e6;
    }

    // Collection count and time per collector, pairwise, in the bean list's order.
    private static long[] gcTotals() {
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        long[] totals = new long[collectors.size() * 2];
        for (int i = 0; i < collectors.size(); i++) {
            totals[i * 2] = collectors.get(i).getCollectionCount();
            totals[i * 2 + 1] = collectors.get(i).getCollectionTime();
        }
        return totals;
    }

    private void frame() {
        if (perfFrames < perfTarget) perfFrame();
        if (smoothFrames >= smoothTarget || mc.player == null || mc.level == null) return;
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        int at = smoothFrames * SMOOTH_STRIDE;
        smoothSamples[at] = System.nanoTime() / 1e9;
        smoothSamples[at + 1] = camera.x;
        smoothSamples[at + 2] = camera.y;
        smoothSamples[at + 3] = camera.z;
        smoothSamples[at + 7] = mc.gameRenderer.mainCamera().yRot();
        smoothSamples[at + 8] = mc.gameRenderer.mainCamera().xRot();

        Entity model = smoothModel < 0 ? null : mc.level.getEntity(smoothModel);
        if (model == null) {
            smoothSamples[at + 4] = Double.NaN;
        } else {
            Vec3 drawn = model.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            smoothSamples[at + 4] = drawn.x - camera.x;
            smoothSamples[at + 5] = drawn.y - camera.y;
            smoothSamples[at + 6] = drawn.z - camera.z;
        }
        smoothFrames++;
    }

    private String smoothReport() {
        int n = smoothFrames;
        if (n < 3) return "error too few frames";
        double[] s = smoothSamples;
        double[] speeds = new double[n - 1];
        double[] jerks = new double[n - 2];
        int frozen = 0;
        double pvx = 0, pvy = 0, pvz = 0;

        for (int i = 1; i < n; i++) {
            int a = i * SMOOTH_STRIDE, b = (i - 1) * SMOOTH_STRIDE;
            double dt = Math.max(1e-6, s[a] - s[b]);
            double dx = s[a + 1] - s[b + 1], dy = s[a + 2] - s[b + 2], dz = s[a + 3] - s[b + 3];
            double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (moved < 1e-6) frozen++;
            speeds[i - 1] = moved / dt;
            double vx = dx / dt, vy = dy / dt, vz = dz / dt;
            if (i > 1) {
                double jx = vx - pvx, jy = vy - pvy, jz = vz - pvz;
                jerks[i - 2] = Math.sqrt(jx * jx + jy * jy + jz * jz);
            }
            pvx = vx;
            pvy = vy;
            pvz = vz;
        }

        double mean = java.util.Arrays.stream(speeds).average().orElse(0);
        double variance = java.util.Arrays.stream(speeds).map(v -> (v - mean) * (v - mean)).average().orElse(0);
        java.util.Arrays.sort(jerks);
        double seconds = s[(n - 1) * SMOOTH_STRIDE] - s[0];

        String report = String.format(Locale.ROOT,
            "frames=%d fps=%.1f speed=%.2f cv=%.3f frozen=%d jerk95=%.2f jerkMax=%.2f",
            n, (n - 1) / seconds, mean, mean > 0 ? Math.sqrt(variance) / mean : 0,
            frozen, jerks[(int) (jerks.length * 0.95)], jerks[jerks.length - 1]);

        if (Double.isNaN(s[4])) return report;

        double sx = 0, sy = 0, sz = 0, worst = 0;
        for (int i = 0; i < n; i++) {
            int a = i * SMOOTH_STRIDE;
            sx += s[a + 4];
            sy += s[a + 5];
            sz += s[a + 6];
            if (i > 0) {
                int b = a - SMOOTH_STRIDE;
                double jx = s[a + 4] - s[b + 4], jy = s[a + 5] - s[b + 5], jz = s[a + 6] - s[b + 6];
                worst = Math.max(worst, Math.sqrt(jx * jx + jy * jy + jz * jz));
            }
        }
        double mx = sx / n, my = sy / n, mz = sz / n, spread = 0;
        for (int i = 0; i < n; i++) {
            int a = i * SMOOTH_STRIDE;
            double ex = s[a + 4] - mx, ey = s[a + 5] - my, ez = s[a + 6] - mz;
            spread += ex * ex + ey * ey + ez * ez;
        }
        double[] right = new double[n], up = new double[n];
        double sr = 0, su = 0;
        for (int i = 0; i < n; i++) {
            int a = i * SMOOTH_STRIDE;
            double yaw = Math.toRadians(s[a + 7]), pitch = Math.toRadians(s[a + 8]);
            double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            double ux = -Math.sin(yaw) * Math.sin(pitch), uy = Math.cos(pitch), uz = Math.cos(yaw) * Math.sin(pitch);
            double along = s[a + 4] * fx + s[a + 5] * fy + s[a + 6] * fz;
            right[i] = Math.toDegrees(Math.atan2(s[a + 4] * rx + s[a + 6] * rz, along));
            up[i] = Math.toDegrees(Math.atan2(s[a + 4] * ux + s[a + 5] * uy + s[a + 6] * uz, along));
            sr += right[i];
            su += up[i];
        }
        double mr = sr / n, mu = su / n, drift = 0, driftMax = 0;
        for (int i = 0; i < n; i++) {
            double d = Math.hypot(right[i] - mr, up[i] - mu);
            drift += d * d;
            driftMax = Math.max(driftMax, d);
        }
        return report + String.format(Locale.ROOT,
            " offset=%.2f,%.2f,%.2f shake=%.3f shakeMax=%.3f view=%.2f,%.2f drift=%.3f driftMax=%.3f",
            mx, my, mz, Math.sqrt(spread / n), worst, mr, mu, Math.sqrt(drift / n), driftMax);
    }

    private void record(String kind, String text) {
        synchronized (messages) {
            messages.addLast(kind + " " + text);
            if (messages.size() > MESSAGE_HISTORY) messages.removeFirst();
        }
    }

    /**
     * The most recent received lines, newest last, tagged {@code chat}, {@code sys} or {@code bar}.
     * <p>
     * A harness checking for one message clears the buffer first and reads it back after.
     * <p>
     * {@code bar} is a system message carrying the overlay flag, which is the old way of writing to
     * the action bar and not the one a modern server uses. For the action bar itself, ask
     * {@link #actionBar}.
     */
    private String messages(String rest) {
        String spec = rest.trim();
        synchronized (messages) {
            if (spec.equals("clear")) {
                messages.clear();
                return "ok";
            }
            int wanted = spec.isEmpty() ? 10 : Integer.parseInt(spec);
            int skip = Math.max(0, messages.size() - wanted);
            StringJoiner out = new StringJoiner(" | ");
            out.setEmptyValue("none");
            int index = 0;
            for (String line : messages) {
                if (index++ >= skip) out.add(line);
            }
            return out.toString();
        }
    }

    /**
     * What the action bar says right now, and how many ticks it has left before it fades.
     * <p>
     * Read from the HUD rather than captured from the wire, because
     * {@code ClientboundSetActionBarTextPacket} has no receive event: it goes straight into a
     * private field. A readout a server refreshes every tick is therefore always current here, and
     * a stale one is visible as a falling time.
     */
    private String actionBar() {
        HudAccessor hud = (HudAccessor) mc.gui.hud;
        Component message = hud.sagefang$overlayMessage();
        if (message == null) return "none";
        return hud.sagefang$overlayMessageTime() + " " + message.getString();
    }

    /**
     * Grabs the framebuffer to {@code screenshots/<name>.png} and answers the path.
     * <p>
     * Whether a model draws is the one question no readback answers, because the client is told an
     * entity exists whether or not anything renders for it. The file is written on the IO pool after
     * the readback completes, so the path is returned before it exists and a failure lands in the
     * client log rather than in the answer.
     */
    private String screenshot(String rest) {
        String spec = rest.trim();
        String name = (spec.isEmpty() ? "remote" : spec.replaceAll("[^A-Za-z0-9._-]", "_")) + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
            message -> SageFang.LOGGER.info("Screenshot: {}", message.getString()));
        return "path " + new File(new File(mc.gameDirectory, "screenshots"), name).getAbsolutePath();
    }

    private String chat(String message) {
        if (mc.getConnection() == null) return "error not connected";
        if (message.startsWith("/")) {
            mc.getConnection().sendCommand(message.substring(1));
        } else {
            mc.getConnection().sendChat(message);
        }
        return "ok";
    }

    private String connect(String address) {
        String target = address.trim();
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target),
            new ServerData("remote", target, ServerData.Type.OTHER), false, null);
        return "connecting";
    }

    /**
     * The player states the server's refusals key off, so a run that produced no flags can be told
     * apart from a run the server declined to judge.
     */
    private String state() {
        if (mc.player == null) return "none";
        return String.format(Locale.ROOT,
            "gliding=%b swimming=%b inWater=%b inLava=%b climbing=%b riding=%b vehicle=%s sprint=%b sneak=%b ground=%b",
            mc.player.isFallFlying(), mc.player.isSwimming(), mc.player.isInWater(), mc.player.isInLava(),
            mc.player.onClimbable(), mc.player.isPassenger(),
            mc.player.getVehicle() == null ? "none" : mc.player.getVehicle().getType().toString(),
            mc.player.isSprinting(), mc.player.isShiftKeyDown(), mc.player.onGround());
    }

    /**
     * Right-clicks a block the way the game does, rather than by pressing a key.
     * <p>
     * The key route goes through the click counter that the frame loop consumes, which a script
     * driving the client from outside cannot reliably feed. Calling the interaction directly is the
     * same call vanilla makes once it has decided a click happened, so the packets that leave the
     * client are the packets a player would have produced.
     */
    private String useBlock(String rest) {
        if (mc.player == null || mc.gameMode == null) return "error no player";
        String[] args = rest.trim().split(" +");
        if (args.length < 3) return "error usage: useblock <x> <y> <z> [face]";
        BlockPos pos = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        Direction face = args.length > 3 ? Direction.valueOf(args[3].toUpperCase(Locale.ROOT)) : Direction.UP;
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        InteractionResult result = mc.gameMode.useItemOn(
            mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        mc.player.swing(InteractionHand.MAIN_HAND);
        return "used " + result;
    }

    private String slot(String rest) {
        if (mc.player == null) return "error no player";
        mc.player.getInventory().setSelectedSlot(Integer.parseInt(rest.trim()));
        return "slot " + mc.player.getInventory().getSelectedSlot();
    }

    /**
     * The open menu's title and every non-empty slot as {@code index:name}, the readback that lets a
     * harness assert what a {@link #clickSlot} changed.
     */
    private String container() {
        if (mc.player == null) return "error no player";
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return "none";
        StringJoiner out = new StringJoiner(" | ", "title=" + screen.getTitle().getString() + " | ", "");
        List<Slot> slots = mc.player.containerMenu.slots;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            if (!stack.isEmpty()) out.add(i + ":" + stack.getHoverName().getString());
        }
        return out.toString();
    }

    /**
     * Clicks a slot in the open menu the way a player does, {@code clickslot <slot> <button> <action>}.
     */
    private String clickSlot(String rest) {
        if (mc.player == null || mc.gameMode == null) return "error no player";
        String[] args = rest.trim().split("\\s+");
        if (args.length != 3) return "error usage: clickslot <slot> <button> <action>";
        ContainerInput action = ContainerInput.valueOf(args[2].toUpperCase(Locale.ROOT));
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,
            Integer.parseInt(args[0]), Integer.parseInt(args[1]), action, mc.player);
        return "ok";
    }

    private String respawn() {
        if (mc.player == null) return "error no player";
        if (!mc.player.isDeadOrDying()) return "alive";
        mc.player.respawn();
        return "ok";
    }

    private String disconnect() {
        if (mc.level == null) return "error not in a world";
        mc.disconnectWithProgressScreen();
        return "ok";
    }
}
