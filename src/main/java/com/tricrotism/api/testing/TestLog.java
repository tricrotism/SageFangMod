package com.tricrotism.api.testing;

import com.tricrotism.SageFang;
import io.avaje.config.Config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side ground truth for anticheat testing. Every cheat module records what it actually
 * transmitted as JSONL with a monotonic timestamp: rotations, attack timings, ack decisions. A
 * server-side flag can then be diffed against what the client really did. Without this you learn
 * that a check fired, not why.
 * <p>
 * The session seed is written as the first line of every log. Copy it into {@code test.seed} to
 * replay a run exactly: the statistical checks need hundreds of samples, and a flag you cannot
 * reproduce is a flag you cannot debug.
 */
public final class TestLog {

    private static final Path DIRECTORY = Path.of("sagefang-testlog");
    private static final int FLUSH_EVERY = 50;

    private static final long seed = resolveSeed();
    private static final long originNanos = System.nanoTime();
    private static final AtomicInteger sinceFlush = new AtomicInteger();

    private static BufferedWriter writer;
    private static boolean failed;

    private TestLog() {}

    private static long resolveSeed() {
        String configured = Config.get("test.seed", "");
        if (!configured.isBlank()) {
            try {
                return Long.parseLong(configured.trim());
            } catch (NumberFormatException e) {
                SageFang.LOGGER.warn("test.seed '{}' is not a long, generating one instead", configured);
            }
        }
        return System.nanoTime();
    }

    /**
     * The session seed. Set {@code test.seed} to this value to replay the run.
     */
    public static long seed() {
        return seed;
    }

    /**
     * A reproducible random stream for {@code name}. Streams are independent, so adding a module does
     * not shift the numbers another module draws.
     */
    public static Random rng(String name) {
        return new Random(seed * 31L + name.hashCode());
    }

    /**
     * Appends one event. {@code keyValues} are alternating key/value pairs, e.g.
     * {@code event("attack", "target", id, "reach", 3.02)}.
     */
    public static void event(String type, Object... keyValues) {
        if (failed) return;
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be alternating key/value pairs");
        }

        StringBuilder line = new StringBuilder(96);
        line.append("{\"t\":").append(System.nanoTime() - originNanos)
            .append(",\"event\":").append(quote(type));
        for (int i = 0; i < keyValues.length; i += 2) {
            line.append(',').append(quote(String.valueOf(keyValues[i]))).append(':').append(value(keyValues[i + 1]));
        }
        line.append('}');
        write(line.toString());
    }

    private static synchronized void write(String line) {
        if (failed) return;
        try {
            if (writer == null) open();
            writer.write(line);
            writer.newLine();
            if (sinceFlush.incrementAndGet() >= FLUSH_EVERY) {
                sinceFlush.set(0);
                writer.flush();
            }
        } catch (IOException e) {
            failed = true;
            SageFang.LOGGER.error("Test log write failed, disabling for this session", e);
        }
    }

    private static void open() throws IOException {
        Files.createDirectories(DIRECTORY);
        Path file = DIRECTORY.resolve("run-" + seed + ".jsonl");
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        writer.write("{\"t\":0,\"event\":\"session\",\"seed\":" + seed + "}");
        writer.newLine();
        Runtime.getRuntime().addShutdownHook(new Thread(TestLog::close, "sagefang-testlog-close"));
        SageFang.LOGGER.info("Test log opened at {} (seed {})", file.toAbsolutePath(), seed);
    }

    private static synchronized void close() {
        if (writer == null) return;
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            SageFang.LOGGER.error("Test log close failed", e);
        } finally {
            writer = null;
        }
    }

    private static String value(Object raw) {
        if (raw instanceof Number || raw instanceof Boolean) return String.valueOf(raw);
        return quote(String.valueOf(raw));
    }

    private static String quote(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
