package com.tricrotism.modules.profiler;

import com.tricrotism.SageFang;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * Where a resource reload's time goes: each reload listener's prepare and apply time, every shader
 * compile and program link by name, and how often and for how long packs were searched for files.
 * <p>
 * Fed by the profiler mixins ({@code ReloadProfilingMixin}, {@code ProfiledReloadFinishMixin},
 * {@code ShaderCompileTimingMixin}, {@code ShaderLinkTimingMixin}, {@code PackListingTimingMixin}) and
 * read by the remote control's {@code reload} command. A new reload clears the previous numbers, so a
 * report describes one reload plus any pipelines compiled on first use after it.
 * <p>
 * Shader compiles and links are timed separately because some drivers (AMD's among them) defer the
 * real compile to link time, where the client then blocks on {@code glGetProgramiv}. A slow pack
 * shader shows up under links, not compiles, on those drivers.
 */
public final class ReloadProfiler {

    private static final int TOP = 10;

    private record Timing(String name, long nanos) {}

    private record Stage(String name, long prepareNanos, long applyNanos) {}

    private static final ConcurrentLinkedQueue<Timing> SHADERS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<Timing> LINKS = new ConcurrentLinkedQueue<>();
    private static final LongAdder LISTING_CALLS = new LongAdder();
    private static final LongAdder LISTING_NANOS = new LongAdder();
    private static final ThreadLocal<long[]> LISTING_START = ThreadLocal.withInitial(() -> new long[1]);
    private static volatile List<Stage> stages = List.of();
    private static volatile long startNanos;
    private static volatile long endNanos;

    private ReloadProfiler() {}

    public static void begin() {
        SHADERS.clear();
        LINKS.clear();
        LISTING_CALLS.reset();
        LISTING_NANOS.reset();
        stages = List.of();
        endNanos = 0;
        startNanos = System.nanoTime();
    }

    /**
     * @param names each reload listener's name, in the same order as the two timing lists
     */
    public static void finish(List<String> names, List<Long> prepareNanos, List<Long> applyNanos) {
        List<Stage> done = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++)
            done.add(new Stage(names.get(i), prepareNanos.get(i), applyNanos.get(i)));
        done.sort(Comparator.comparingLong((Stage s) -> s.prepareNanos + s.applyNanos).reversed());
        stages = done;
        endNanos = System.nanoTime();
        SageFang.LOGGER.info("Reload profile: {}", report());
    }

    public static void shaderCompiled(String name, long nanos) {
        SHADERS.add(new Timing(name, nanos));
    }

    public static void programLinked(String name, long nanos) {
        LINKS.add(new Timing(name, nanos));
    }

    public static void listingStart() {
        LISTING_START.get()[0] = System.nanoTime();
    }

    public static void listingEnd() {
        LISTING_NANOS.add(System.nanoTime() - LISTING_START.get()[0]);
        LISTING_CALLS.increment();
    }

    /**
     * @return one line: wall time, pack listing, shader compiles and links with the slowest named, and
     * the slowest reload listeners as prepare/apply milliseconds
     */
    public static String report() {
        if (startNanos == 0) return "error no reload seen";
        long end = endNanos == 0 ? System.nanoTime() : endNanos;
        return String.format(Locale.ROOT, "%s wall=%dms listing=%d/%dms compiles=%s links=%s stages=%s",
            endNanos == 0 ? "running" : "done", (end - startNanos) / 1_000_000,
            LISTING_CALLS.sum(), LISTING_NANOS.sum() / 1_000_000,
            timings(SHADERS), timings(LINKS), stages());
    }

    // Summed time across threads, so listing can exceed the wall time while workers run in parallel.
    private static String timings(ConcurrentLinkedQueue<Timing> queue) {
        List<Timing> all = new ArrayList<>(queue);
        long total = 0;
        for (Timing t : all) total += t.nanos;
        all.sort(Comparator.comparingLong(Timing::nanos).reversed());
        StringJoiner top = new StringJoiner(",", "[", "]");
        for (int i = 0; i < Math.min(TOP, all.size()); i++)
            top.add(all.get(i).name + ":" + all.get(i).nanos / 1_000_000);
        return all.size() + "/" + total / 1_000_000 + "ms" + top;
    }

    private static String stages() {
        StringJoiner out = new StringJoiner(",", "[", "]");
        List<Stage> list = stages;
        for (int i = 0; i < Math.min(TOP, list.size()); i++) {
            Stage s = list.get(i);
            out.add(s.name + ":" + s.prepareNanos / 1_000_000 + "/" + s.applyNanos / 1_000_000);
        }
        return out.toString();
    }
}
