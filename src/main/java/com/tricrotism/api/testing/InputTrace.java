package com.tricrotism.api.testing;

import com.tricrotism.SageFang;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A recording of genuine click timing, stored as the gaps between presses.
 * <p>
 * This is the other half of the verification: a statistical check is only as good as the human input
 * it declines to flag, and the only honest source of that input is a person clicking. A trace is
 * recorded once from real hands and then replayed as often as the thresholds move, which is what
 * makes a false-positive result reproducible rather than anecdotal.
 * <p>
 * Intervals rather than absolute stamps, because that is what survives being replayed at a different
 * wall-clock time and it is the quantity the checks actually read.
 */
public final class InputTrace {

    private static final Path DIRECTORY = Path.of("sagefang-testlog", "traces");

    private InputTrace() {}

    private static Path fileFor(String name) {
        return DIRECTORY.resolve(name.replaceAll("[^A-Za-z0-9._-]", "_") + ".trace");
    }

    /**
     * Writes {@code intervals}, one millisecond gap per line. Returns false if it could not.
     */
    public static boolean save(String name, List<Long> intervals) {
        try {
            Files.createDirectories(DIRECTORY);
            StringBuilder out = new StringBuilder(intervals.size() * 5);
            for (long interval : intervals) out.append(interval).append('\n');
            Files.writeString(fileFor(name), out, StandardCharsets.UTF_8);
            SageFang.LOGGER.info("Wrote input trace '{}' ({} intervals)", name, intervals.size());
            return true;
        } catch (IOException e) {
            SageFang.LOGGER.error("Could not write input trace '{}'", name, e);
            return false;
        }
    }

    /**
     * Reads a trace, or an empty list if it is absent or unreadable.
     */
    public static List<Long> load(String name) {
        Path file = fileFor(name);
        if (!Files.isRegularFile(file)) return List.of();

        List<Long> intervals = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    intervals.add(Long.parseLong(trimmed));
                } catch (NumberFormatException e) {
                    SageFang.LOGGER.warn("Skipping malformed interval '{}' in trace '{}'", trimmed, name);
                }
            }
        } catch (IOException e) {
            SageFang.LOGGER.error("Could not read input trace '{}'", name, e);
            return List.of();
        }
        return intervals;
    }

    /**
     * Standard deviation of {@code intervals} in milliseconds; NaN below two samples.
     */
    public static double deviation(List<Long> intervals) {
        if (intervals.size() < 2) return Double.NaN;

        double sum = 0.0;
        for (long interval : intervals) sum += interval;
        double mean = sum / intervals.size();

        double variance = 0.0;
        for (long interval : intervals) {
            double d = interval - mean;
            variance += d * d;
        }
        return Math.sqrt(variance / intervals.size());
    }

    /**
     * Mean interval in milliseconds; NaN when empty.
     */
    public static double mean(List<Long> intervals) {
        if (intervals.isEmpty()) return Double.NaN;
        double sum = 0.0;
        for (long interval : intervals) sum += interval;
        return sum / intervals.size();
    }
}
