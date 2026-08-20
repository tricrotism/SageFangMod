package com.tricrotism.utils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Simple scheduled task executor for delayed command execution.
 * Uses a daemon thread pool so it doesn't block JVM shutdown.
 */
public final class ScheduledTaskRunner {

    private static final ScheduledExecutorService EXECUTOR;

    static {
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(2, r -> {
            Thread t = new Thread(r, "sagefang-scheduler");
            t.setDaemon(true);
            return t;
        });
        pool.setRemoveOnCancelPolicy(true);
        pool.setKeepAliveTime(30, TimeUnit.SECONDS);
        pool.allowCoreThreadTimeOut(true);
        EXECUTOR = pool;
    }

    private ScheduledTaskRunner() {}

    /**
     * Run a task off the main thread as soon as a pool thread is free. Same threading
     * contract as {@link #schedule(Runnable, long)}.
     */
    public static void run(Runnable task) {
        EXECUTOR.execute(task);
    }

    /**
     * Schedule a task to run after the given delay.
     * The task runs off the main thread, so callers must schedule back to
     * the client thread via {@code Minecraft.getInstance().execute()} if needed.
     */
    public static void schedule(Runnable task, long delayMs) {
        EXECUTOR.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }
}
