package com.tricrotism.modules.profiler;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.ImGuiUtil;
import imgui.ImGui;
import imgui.ImGuiIO;

import java.io.IOException;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The other half of the question {@link FrameProfiler} answers.
 * <p>
 * The profiler says which call path was running when a frame overran. It cannot say whether the
 * frame overran because of that call path or because the collector stopped the world underneath it,
 * and those want opposite responses. This watches the JVM around the frames: what the heap is doing,
 * what the collector is doing, how fast the client is allocating, and whether anything is climbing
 * across collections instead of dropping back.
 * <p>
 * Four readings are worth having on a Minecraft client specifically:
 * <ul>
 *   <li><b>Off-heap buffers.</b> The direct and mapped pools are where LWJGL's buffers live, so this
 *       is the closest thing to a view of the memory a heap profiler cannot see. Native growth with
 *       a flat heap is a different bug from heap growth and is invisible to every other tool here.</li>
 *   <li><b>Collector pauses in a window.</b> A stall the profiler attributed to some innocent render
 *       frame is explained if a collection ran through it. This keeps the last minute of pauses so
 *       the two can be lined up by eye.</li>
 *   <li><b>Allocation rate.</b> The client allocating tens of megabytes a second is the cause of the
 *       collections, so it is the thing to fix rather than the pauses themselves.</li>
 *   <li><b>Post-collection trend.</b> Live memory is what survives a collection, so a rising floor
 *       across collections is a leak and a rising peak is not. Read off the long-lived pool where
 *       one exists and off the whole heap where the collector has no generations, which is what a
 *       client on ZGC or Shenandoah looks like.</li>
 * </ul>
 * <p>
 * <b>Why a thread rather than a tick handler.</b> The interesting moments are the ones where the
 * client is not ticking. A sampler driven by the tick loop stops sampling exactly when a freeze
 * starts and resumes when it ends, which is a record of everything except the event. This runs on
 * its own daemon thread for the same reason {@link FrameProfiler} does. The world counts are the
 * exception and are read on the tick thread into volatiles, since {@code mc.level} is not ours to
 * touch from anywhere else.
 */
public final class MemoryInspector extends Module {

    public static final MemoryInspector instance = new MemoryInspector();

    private static final Path DIRECTORY = Path.of("sagefang-testlog");
    private static final String SAMPLER_THREAD = "sagefang-memory";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final long MB = 1024L * 1024L;
    private static final int TREND_SAMPLES = 24;
    private static final long DEADLOCK_CHECK_MS = 5000L;

    private final Settings.Int intervalMs =
        integer("Sample Every (ms)", "intervalms", "Sampler period. Lower costs more.", 250, 50, 5000);
    private final Settings.Int pauseWindowSec =
        integer("Pause window (s)", "pausewindow", "How far back the collector pause tally reaches.", 60, 10, 600);
    private final Settings.Decimal leakThreshold =
        decimal("Leak slope (MB/GC)", "leakslope",
            "Post-collection growth per collection at or above which the trend is called a leak.", 1.0, 0.05, 64.0);

    private volatile boolean running;
    private Thread sampler;

    private volatile long heapUsed;
    private volatile long heapMax;
    private volatile long nonHeapUsed;
    private volatile long directUsed;
    private volatile long directCount;
    private volatile long mappedUsed;
    private volatile double allocRateMb;
    private volatile long gcCount;
    private volatile long gcMillis;
    private volatile int windowPauses;
    private volatile long windowPauseMillis;
    private volatile long worstPauseMillis;
    private volatile double leakSlopeMb;
    private volatile String longLivedName = "unknown";
    private volatile long longLivedFloor;
    private volatile String deadlock;

    private volatile int entities = -1;
    private volatile int chunks = -1;
    private volatile int blockEntities = -1;

    /**
     * The panel's lines, formatted where the numbers change instead of where they are drawn. The
     * sampler runs at a few hertz and the panel redraws at frame rate, so formatting in
     * {@link #renderExtra} spent a {@code Formatter} and a boxed argument per line per frame.
     */
    private record Display(String heap, String nonHeap, String direct, String mapped,
                           String allocation, String collections, String window,
                           String live, String trend) {}

    private volatile Display display;
    private volatile String world = "World: not in one";

    private final List<long[]> pauses = new ArrayList<>();
    private final double[] trend = new double[TREND_SAMPLES];
    private int trendCount;
    private long lastTrendFloor = -1;

    private long lastHeapUsed;
    private long allocAccum;
    private long allocWindowStart;
    private long lastGcCount;
    private long lastGcMillis;
    private long lastDeadlockCheck;

    private MemoryInspector() {
        super("memoryinspector", "Memory Inspector",
            "Watch the heap, the collector and the off-heap pools around slow frames.", Category.LOGGING);
    }

    @Override
    public void onActivate() {
        reset();
        running = true;

        Thread thread = new Thread(this::sampleLoop, SAMPLER_THREAD);
        thread.setDaemon(true);
        sampler = thread;
        thread.start();
        SageFang.LOGGER.info("Memory inspector sampling every {} ms", intervalMs.get());
    }

    @Override
    public void onDeactivate() {
        running = false;
        Thread thread = sampler;
        sampler = null;
        if (thread == null) return;

        thread.interrupt();
        try {
            thread.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void reset() {
        synchronized (pauses) {
            pauses.clear();
        }

        trendCount = 0;
        lastTrendFloor = -1;
        lastHeapUsed = 0;
        allocAccum = 0;
        allocWindowStart = 0;
        lastGcCount = 0;
        lastGcMillis = 0;
        lastDeadlockCheck = 0;
        deadlock = null;
        leakSlopeMb = 0;
        worstPauseMillis = 0;
        world = "World: not in one";
        publish();
    }

    private void sampleLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                sample();
                Thread.sleep(Math.max(intervalMs.get(), 50));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                SageFang.LOGGER.warn("Memory inspector sample failed", e);
                return;
            }
        }
    }

    private void sample() {
        long now = System.currentTimeMillis();

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        heapUsed = heap.getUsed();
        heapMax = heap.getMax();
        nonHeapUsed = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();

        readBufferPools();
        trackAllocation(now, heap.getUsed());
        trackCollections(now);
        trackDeadlocks(now);
        publish();
    }

    private void publish() {
        display = new Display(
            String.format(Locale.ROOT, "Heap: %d / %d MB", heapUsed / MB, heapMax / MB),
            String.format(Locale.ROOT, "Non-heap: %d MB", nonHeapUsed / MB),
            String.format(Locale.ROOT, "Direct: %d MB in %d buffers", directUsed / MB, directCount),
            String.format(Locale.ROOT, "Mapped: %d MB", mappedUsed / MB),
            String.format(Locale.ROOT, "Allocating: %.1f MB/s", allocRateMb),
            String.format(Locale.ROOT, "Collections: %d, %d ms total", gcCount, gcMillis),
            String.format(Locale.ROOT, "Last %ds: %d collections, %d ms, worst burst %d ms",
                pauseWindowSec.get(), windowPauses, windowPauseMillis, worstPauseMillis),
            String.format(Locale.ROOT, "Live set: %d MB in %s", longLivedFloor / MB, longLivedName),
            trendCount < 4
                ? "Trend: need " + (4 - trendCount) + " more collections"
                : String.format(Locale.ROOT, "Trend: %+.2f MB per collection over %d", leakSlopeMb, trendCount)
        );
    }

    /**
     * The direct and mapped pools, which is where a client's native buffers land. Growth here with a
     * flat heap is the leak class no heap dump will show.
     */
    private void readBufferPools() {
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if ("direct".equals(pool.getName())) {
                directUsed = pool.getMemoryUsed();
                directCount = pool.getCount();
            } else if ("mapped".equals(pool.getName())) {
                mappedUsed = pool.getMemoryUsed();
            }
        }
    }

    /**
     * Heap only ever falls at a collection, so every rise between samples is allocation. Summing the
     * rises over a window is an allocation rate without an agent or a JFR recording.
     */
    private void trackAllocation(long now, long used) {
        if (allocWindowStart == 0) {
            allocWindowStart = now;
            lastHeapUsed = used;
            return;
        }

        if (used > lastHeapUsed) allocAccum += used - lastHeapUsed;
        lastHeapUsed = used;

        long elapsed = now - allocWindowStart;
        if (elapsed < 5000) return;

        allocRateMb = (allocAccum / (double) MB) / (elapsed / 1000.0);
        allocAccum = 0;
        allocWindowStart = now;
    }

    private void trackCollections(long now) {
        long count = 0;
        long millis = 0;

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount();
            long t = gc.getCollectionTime();
            if (c > 0) count += c;
            if (t > 0) millis += t;
        }

        gcCount = count;
        gcMillis = millis;

        if (lastGcCount == 0) {
            lastGcCount = count;
            lastGcMillis = millis;
            return;
        }

        long collections = count - lastGcCount;
        if (collections <= 0) return;

        long spent = millis - lastGcMillis;
        lastGcCount = count;
        lastGcMillis = millis;

        recordPause(now, collections, spent);
        recordTrend();
    }

    private void recordPause(long now, long collections, long spent) {
        long horizon = now - pauseWindowSec.get() * 1000L;
        int windowCount = 0;
        long windowMillis = 0;
        long windowWorst = 0;

        synchronized (pauses) {
            pauses.add(new long[]{now, collections, spent});
            pauses.removeIf(entry -> entry[0] < horizon);

            for (long[] entry : pauses) {
                windowCount += (int) entry[1];
                windowMillis += entry[2];
                if (entry[2] > windowWorst) windowWorst = entry[2];
            }
        }

        windowPauses = windowCount;
        windowPauseMillis = windowMillis;

        // Read off the window like the other two. Kept as a running maximum it never decayed, so a
        // single bad burst stayed on a line labelled "last 60s" for the rest of the session.
        worstPauseMillis = windowWorst;
    }

    /**
     * What survives a collection is the live set, so this reads the post-collection usage rather
     * than the current usage. A generational collector exposes it on the old pool; ZGC and Shenandoah
     * have no old pool, so the whole heap's post-collection usage is the same signal.
     */
    private void recordTrend() {
        long floor = 0;
        String name = null;

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) continue;

            MemoryUsage after = pool.getCollectionUsage();
            if (after == null) continue;

            String pooled = pool.getName();
            boolean longLived = pooled.contains("Old") || pooled.contains("Tenured");

            if (longLived) {
                floor = after.getUsed();
                name = pooled;
                break;
            }

            floor += after.getUsed();
        }

        if (name == null) name = "whole heap";
        longLivedName = name;
        longLivedFloor = floor;

        // A generational collector only refreshes the long-lived pool on a collection that touched
        // it, so young collections would otherwise push the same reading repeatedly and flatten the
        // slope toward zero. Under ZGC, where old collections are rare, that hides a leak outright.
        if (floor == lastTrendFloor) return;
        lastTrendFloor = floor;

        if (trendCount < TREND_SAMPLES) {
            trend[trendCount++] = floor / (double) MB;
        } else {
            System.arraycopy(trend, 1, trend, 0, TREND_SAMPLES - 1);
            trend[TREND_SAMPLES - 1] = floor / (double) MB;
        }

        leakSlopeMb = slope();
    }

    /**
     * Least squares over the post-collection readings, in megabytes per collection. A leak is a
     * positive slope that persists; a busy but healthy client scatters around a flat one.
     */
    private double slope() {
        if (trendCount < 4) return 0;

        double meanX = (trendCount - 1) / 2.0;
        double meanY = 0;
        for (int i = 0; i < trendCount; i++) meanY += trend[i];
        meanY /= trendCount;

        double num = 0;
        double den = 0;
        for (int i = 0; i < trendCount; i++) {
            double dx = i - meanX;
            num += dx * (trend[i] - meanY);
            den += dx * dx;
        }

        return den == 0 ? 0 : num / den;
    }

    private void trackDeadlocks(long now) {
        if (now - lastDeadlockCheck < DEADLOCK_CHECK_MS) return;
        lastDeadlockCheck = now;

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] found = threads.findDeadlockedThreads();

        if (found == null || found.length == 0) {
            deadlock = null;
            return;
        }

        StringBuilder names = new StringBuilder();
        for (var info : threads.getThreadInfo(found, 1)) {
            if (info == null) continue;
            if (!names.isEmpty()) names.append(", ");
            names.append(info.getThreadName());
        }

        deadlock = names.toString();
        SageFang.LOGGER.error("Deadlock detected across threads: {}", deadlock);
    }

    /**
     * World counts, read where the world is legal to read.
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.level == null) {
            if (entities == -1) return;

            entities = -1;
            chunks = -1;
            blockEntities = -1;
            world = "World: not in one";
            return;
        }

        int count = mc.level.getEntityCount();
        int loaded = mc.level.getChunkSource().getLoadedChunksCount();
        int blocks = mc.level.getGloballyRenderedBlockEntities().size();

        if (count == entities && loaded == chunks && blocks == blockEntities) return;

        entities = count;
        chunks = loaded;
        blockEntities = blocks;
        world = "World: " + count + " entities, " + loaded + " chunks, " + blocks + " block entities";
    }

    private String report() {

        String locked = deadlock;
        String out = "SageFang memory inspector " + LocalDateTime.now().format(STAMP) + '\n' +
            String.format(Locale.ROOT, "heap        %d/%d MB%n", heapUsed / MB, heapMax / MB) +
            String.format(Locale.ROOT, "non-heap    %d MB%n", nonHeapUsed / MB) +
            String.format(Locale.ROOT, "direct      %d MB across %d buffers%n", directUsed / MB, directCount) +
            String.format(Locale.ROOT, "mapped      %d MB%n", mappedUsed / MB) +
            String.format(Locale.ROOT, "allocation  %.1f MB/s%n", allocRateMb) +
            String.format(Locale.ROOT, "collections %d totalling %d ms%n", gcCount, gcMillis) +
            String.format(Locale.ROOT, "last %ds     %d collections, %d ms, worst burst %d ms%n",
                pauseWindowSec.get(), windowPauses, windowPauseMillis, worstPauseMillis) +
            String.format(Locale.ROOT, "live set    %d MB in %s, slope %.2f MB per collection over %d%n",
                longLivedFloor / MB, longLivedName, leakSlopeMb, trendCount) +
            String.format(Locale.ROOT, "world       %d entities, %d chunks, %d block entities%n",
                entities, chunks, blockEntities) +
            "deadlock    " + (locked == null ? "none" : locked) + '\n';

        return out;
    }

    private void writeReport() {
        try {
            Files.createDirectories(DIRECTORY);
            Path file = DIRECTORY.resolve("memory-" + System.currentTimeMillis() + ".txt");
            Files.writeString(file, report(), StandardCharsets.UTF_8);
            SageFang.LOGGER.info("Memory report written to {}", file.toAbsolutePath());
        } catch (IOException e) {
            SageFang.LOGGER.warn("Could not write memory report", e);
        }
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        if (!isActive()) {
            ImGui.textUnformatted("Enable to start sampling");
            return;
        }

        Display shown = display;

        ImGui.textUnformatted(shown.heap());
        ImGui.textUnformatted(shown.nonHeap());
        ImGui.textUnformatted(shown.direct());
        if (ImGui.isItemHovered()) {
            ImGuiUtil.setTooltip("LWJGL and NIO buffers. Growth here with a flat heap is a native leak, "
                + "which no heap dump will show you.");
        }
        ImGui.textUnformatted(shown.mapped());

        ImGui.separator();
        ImGui.textUnformatted(shown.allocation());
        ImGui.textUnformatted(shown.collections());
        ImGui.textUnformatted(shown.window());
        if (ImGui.isItemHovered()) {
            ImGuiUtil.setTooltip("Line this up against Frame Profiler's stall times. A stall that "
                + "coincides with a collection was not caused by the call path it sampled.");
        }

        ImGui.separator();
        ImGui.textUnformatted(shown.live());
        ImGui.textUnformatted(shown.trend());
        if (leakSlopeMb >= leakThreshold.get() && trendCount >= 4) {
            ImGui.textUnformatted("Climbing across collections, which is what a leak looks like");
        }

        ImGui.separator();
        ImGui.textUnformatted(world);

        String locked = deadlock;
        if (locked != null) ImGui.textUnformatted("Deadlocked: " + locked);

        if (ImGui.button("Write report##memoryInspectorReport")) writeReport();
    }
}
