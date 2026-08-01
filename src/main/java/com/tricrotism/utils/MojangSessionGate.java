package com.tricrotism.utils;

import com.tricrotism.SageFang;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serializes Mojang {@code session/minecraft/join} &rarr; {@code hasJoined}
 * handshakes across SageFang's social integrations (currently LabyMod).
 *
 * <p>Mojang binds a single {@code serverId} to an account session per join, so
 * concurrent joins overwrite each other and the loser's third-party server then
 * sees {@code hasJoined} fail ("invalid session" / 401). Each integration holds
 * this single-permit gate from just before its {@code joinServer} until its
 * remote verification settles, guaranteeing the windows never overlap.
 *
 * <p>A watchdog auto-releases a forgotten or stuck ticket after
 * {@value #MAX_HOLD_MS} ms so a dropped callback can never lock the gate
 * permanently. {@link Ticket#release()} is idempotent, so the watchdog and the
 * normal release path coexist safely.
 */
public final class MojangSessionGate {

    private static final Semaphore PERMIT = new Semaphore(1, true);
    private static final ScheduledExecutorService WATCHDOG =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mojang-session-gate-watchdog");
            t.setDaemon(true);
            return t;
        });
    /**
     * Hard cap on how long any one handshake may hold the gate (covers LabyMod's 2s/4s/8s rate-limit backoff).
     */
    private static final long MAX_HOLD_MS = 20_000;

    private MojangSessionGate() {}

    /**
     * Blocks until the gate is free, then returns a one-shot {@link Ticket}. The
     * caller must {@link Ticket#release()} once its Mojang verification settles
     * (success or failure); a watchdog releases it regardless after
     * {@value #MAX_HOLD_MS} ms.
     */
    public static Ticket acquire() throws InterruptedException {
        PERMIT.acquire();
        Ticket ticket = new Ticket();
        WATCHDOG.schedule(() -> {
            if (ticket.releaseInternal()) {
                SageFang.LOGGER.warn("[MojangSessionGate] watchdog released a stuck session ticket after {}ms", MAX_HOLD_MS);
            }
        }, MAX_HOLD_MS, TimeUnit.MILLISECONDS);
        return ticket;
    }

    /**
     * A held permit, releasable exactly once. Extra releases (e.g. watchdog after
     * a normal release) are no-ops.
     */
    public static final class Ticket {
        private final AtomicBoolean done = new AtomicBoolean();

        private Ticket() {}

        /**
         * Releases the gate the first time it is called; subsequent calls are no-ops.
         */
        public void release() {
            releaseInternal();
        }

        private boolean releaseInternal() {
            if (done.compareAndSet(false, true)) {
                PERMIT.release();
                return true;
            }
            return false;
        }
    }
}
