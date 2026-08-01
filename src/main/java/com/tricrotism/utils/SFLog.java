package com.tricrotism.utils;

import io.avaje.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Central feedback sink for module messages. Depending on the
 * {@code sagefang.log.chat} toggle each line goes to the prefixed chat line (via
 * {@link MessageUtils}) or is kept only in the in-game log window
 * ({@link com.tricrotism.features.menus.LogMenu}) — either way it is always
 * appended to the window's buffer, so nothing is lost when chat output is off.
 */
public final class SFLog {

    private static final String CHAT_KEY = "sagefang.log.chat";
    private static final int MAX_ENTRIES = 500;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Deque<String> ENTRIES = new ArrayDeque<>();

    private SFLog() {}

    /**
     * True when module feedback is also echoed into Minecraft chat.
     */
    public static boolean isChatOutput() {
        return Config.getBool(CHAT_KEY, true);
    }

    public static void setChatOutput(boolean value) {
        Config.setProperty(CHAT_KEY, String.valueOf(value));
    }

    /**
     * Logs a line of module feedback. Safe to call from any thread — the chat hop
     * is scheduled onto the client thread.
     */
    public static void log(String source, String message) {
        String line = "[" + LocalTime.now().format(TIME) + "] " + source + ": " + message;
        synchronized (ENTRIES) {
            ENTRIES.addLast(line);
            while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.removeFirst();
        }

        if (!isChatOutput()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) MessageUtils.sendMessage(mc, Component.literal(message));
        });
    }

    /**
     * Snapshot of the buffered lines, oldest first.
     */
    public static String[] entries() {
        synchronized (ENTRIES) {
            return ENTRIES.toArray(new String[0]);
        }
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }
}
