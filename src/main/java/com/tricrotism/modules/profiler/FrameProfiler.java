package com.tricrotism.modules.profiler;

import com.tricrotism.SageFang;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Finds out why the client stutters, rather than that it does.
 * <p>
 * The client counterpart to a server tick watchdog, built the same way and for the same reason: a
 * frame-time number tells you a frame was slow and nothing about what made it slow. This samples the
 * render thread's own stack while the slow frame is still running, so the answer is the call path
 * that was actually executing rather than whatever was running by the time anyone looked.
 * <p>
 * Vanilla is in scope and is usually the answer. Attribution buckets every sample by who owns the
 * executing frame, so "the client is slow" resolves into chunk meshing, entity rendering, a mod, or
 * this mod. This mod is included deliberately, since a profiler that cannot indict its own
 * process is not measuring the thing you care about.
 * <p>
 * No agent and no native code: {@link ThreadMXBean} is in the JDK and can read any thread's stack
 * from another thread. That is also the one real cost. A stack capture is not free, so the sampler
 * runs on its own thread at a chosen interval and never on the render thread, and the per-frame hook
 * does nothing but arithmetic on two longs.
 * <p>
 * {@code Stalls Only} is the mode that matters. Sampling continuously describes where time goes on
 * average, which is dominated by whatever is normal; sampling only inside frames that have already
 * overrun describes what the stutters are made of, and those are different questions with different
 * answers.
 */
public final class FrameProfiler extends Module {

    public static final FrameProfiler instance = new FrameProfiler();

    private static final Path DIRECTORY = Path.of("sagefang-testlog");
    private static final String SAMPLER_THREAD = "sagefang-profiler";

    private final Settings.Int stallMs =
        integer("Stall (ms)", "stallMs", "Frame time at or above which a frame counts as a stall", 50, 5, 500);
    private final Settings.Int intervalMs =
        integer("Sample Every (ms)", "intervalMs", "Sampler period; lower costs more", 5, 1, 50);
    private final Settings.Int maxDepth =
        integer("Max Depth", "maxDepth", "Stack frames captured per sample", 64, 8, 256);
    private final Settings.Bool stallsOnly =
        bool("Stalls Only", "stallsOnly", "Sample only inside frames that have already overrun", true);

    /**
     * Written by the render thread each frame, read by the sampler.
     */
    private volatile long frameStartNanos;
    private volatile boolean running;

    private final AtomicLong frames = new AtomicLong();
    private final AtomicLong stalls = new AtomicLong();
    private final AtomicLong worstFrameNanos = new AtomicLong();
    private final AtomicLong samples = new AtomicLong();

    private final Map<String, LongAdder> selfMethods = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> stacks = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> owners = new ConcurrentHashMap<>();

    private Thread sampler;
    private String lastReport = "none";

    private FrameProfiler() {
        super("frameprofiler", "Frame Profiler",
            "Sample the render thread during slow frames and rank what was running.", Category.LOGGING);
    }

    /**
     * Called at the head of every frame on the render thread. Deliberately trivial: two volatile
     * writes and some arithmetic, no allocation, no map lookups. A profiler that costs a measurable
     * slice of the frame it is measuring reports its own overhead.
     */
    public void onFrameStart() {
        long now = System.nanoTime();
        long previous = frameStartNanos;
        frameStartNanos = now;
        if (!running || previous == 0L) return;

        long elapsed = now - previous;
        frames.incrementAndGet();
        if (elapsed >= stallMs.get() * 1_000_000L) stalls.incrementAndGet();
        worstFrameNanos.accumulateAndGet(elapsed, Math::max);
    }

    @Override
    public void onActivate() {
        reset();
        running = true;

        Thread thread = new Thread(this::sampleLoop, SAMPLER_THREAD);
        thread.setDaemon(true);
        sampler = thread;
        thread.start();
        SageFang.LOGGER.info("Frame profiler sampling every {} ms", intervalMs.get());
    }

    @Override
    public void onDeactivate() {
        running = false;
        Thread thread = sampler;
        sampler = null;
        if (thread == null) return;

        thread.interrupt();
        try {
            // Bounded: the loop only ever sleeps for the sample interval, so it exits promptly. The
            // join exists so a report is never written while the sampler is still adding to it.
            thread.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        writeReport();
    }

    private void reset() {
        frames.set(0);
        stalls.set(0);
        samples.set(0);
        worstFrameNanos.set(0);
        frameStartNanos = 0L;
        selfMethods.clear();
        stacks.clear();
        owners.clear();
    }

    private void sampleLoop() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long renderThreadId = Minecraft.getInstance().getRunningThread().threadId();

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(Math.max(1, intervalMs.get()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (stallsOnly.get() && !frameOverrunning()) continue;

            ThreadInfo info = threads.getThreadInfo(renderThreadId, maxDepth.get());
            if (info == null) continue;
            StackTraceElement[] trace = info.getStackTrace();
            if (trace.length == 0) continue;

            record(trace);
        }
    }

    /**
     * Whether the frame currently in flight has already exceeded the stall threshold.
     */
    private boolean frameOverrunning() {
        long start = frameStartNanos;
        return start != 0L && System.nanoTime() - start >= stallMs.get() * 1_000_000L;
    }

    private void record(StackTraceElement[] trace) {
        samples.incrementAndGet();
        count(selfMethods, name(trace[0]));
        count(owners, owner(trace));

        // Root-first, semicolon-separated: the folded format speedscope and flamegraph.pl read.
        StringBuilder folded = new StringBuilder(trace.length * 40);
        for (int i = trace.length - 1; i >= 0; i--) {
            folded.append(name(trace[i]));
            if (i > 0) folded.append(';');
        }
        count(stacks, folded.toString());
    }

    private static void count(Map<String, LongAdder> into, String key) {
        into.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    private static String name(StackTraceElement frame) {
        return frame.getClassName() + '.' + frame.getMethodName();
    }

    /**
     * Who the sample belongs to. Taken from the deepest frame that is anybody's code rather than the
     * JDK's, so time spent inside a collection or a string operation is charged to the caller that
     * asked for it instead of to the JDK.
     */
    private static String owner(StackTraceElement[] trace) {
        for (StackTraceElement frame : trace) {
            String cls = frame.getClassName();
            if (cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.")) continue;
            if (cls.startsWith("com.tricrotism.")) return "sagefang";
            if (cls.startsWith("net.minecraft.") || cls.startsWith("com.mojang.")) return "minecraft";
            if (cls.startsWith("org.lwjgl.")) return "lwjgl";
            return "other-mod";
        }
        return "jdk";
    }

    /**
     * Hottest entries of a counter, descending.
     */
    private static List<Map.Entry<String, Long>> top(Map<String, LongAdder> counts, int limit) {
        List<Map.Entry<String, Long>> out = new ArrayList<>(counts.size());
        for (Map.Entry<String, LongAdder> e : counts.entrySet()) {
            out.add(Map.entry(e.getKey(), e.getValue().sum()));
        }
        out.sort(Comparator.<Map.Entry<String, Long>, Long>comparing(Map.Entry::getValue).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }

    /**
     * Writes the folded stacks for an external flame graph, plus a ranked summary. Called on
     * deactivate, after the sampler has stopped.
     */
    private void writeReport() {
        if (samples.get() == 0) {
            lastReport = "no samples";
            return;
        }

        StringBuilder out = new StringBuilder(stacks.size() * 80);
        for (Map.Entry<String, LongAdder> e : stacks.entrySet()) {
            out.append(e.getKey()).append(' ').append(e.getValue().sum()).append('\n');
        }

        try {
            Files.createDirectories(DIRECTORY);
            Path file = DIRECTORY.resolve("profile-" + frames.get() + "f-" + samples.get() + "s.folded");
            Files.writeString(file, out, StandardCharsets.UTF_8);
            lastReport = file.getFileName().toString();
            SageFang.LOGGER.info("Frame profile written to {} ({} samples over {} frames, {} stalls)",
                file.toAbsolutePath(), samples.get(), frames.get(), stalls.get());
        } catch (IOException e) {
            lastReport = "write failed";
            SageFang.LOGGER.error("Could not write frame profile", e);
        }
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        long total = samples.get();
        ImGui.text(String.format("Frames %d | stalls %d | worst %.1f ms",
            frames.get(), stalls.get(), worstFrameNanos.get() / 1_000_000.0));
        ImGui.text("Samples: " + total);
        ImGui.textDisabled("Report is written when you disable the module.");

        if (total == 0) return;

        ImGui.separator();
        ImGui.text("By owner");
        for (Map.Entry<String, Long> e : top(owners, 6)) {
            ImGui.text(String.format("  %-11s %5.1f%%", e.getKey(), 100.0 * e.getValue() / total));
        }

        ImGui.separator();
        ImGui.text("Hottest methods");
        for (Map.Entry<String, Long> e : top(selfMethods, 10)) {
            ImGui.text(String.format("  %5.1f%%  %s", 100.0 * e.getValue() / total, shorten(e.getKey())));
        }

        ImGui.separator();
        ImGui.text("Last report: " + lastReport);
    }

    /**
     * {@code net.minecraft.client.renderer.LevelRenderer.render} to {@code n.m.c.r.LevelRenderer.render}.
     */
    private static String shorten(String qualified) {
        int lastDot = qualified.lastIndexOf('.');
        if (lastDot < 0) return qualified;
        int classDot = qualified.lastIndexOf('.', lastDot - 1);
        if (classDot < 0) return qualified;

        StringBuilder out = new StringBuilder(qualified.length());
        for (String part : qualified.substring(0, classDot).split("\\.")) {
            if (!part.isEmpty()) out.append(part.charAt(0)).append('.');
        }
        return out.append(qualified, classDot + 1, qualified.length()).toString();
    }
}
