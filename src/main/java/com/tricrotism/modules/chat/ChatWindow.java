package com.tricrotism.modules.chat;

import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One chat window: where it sits, what it accepts, and the lines it has kept.
 * <p>
 * Windows are independent rather than tabs. Every message is offered to every window and lands in
 * each one whose filter accepts it, so a whisper can show in both the main window and a mentions
 * window without either knowing about the other.
 * <p>
 * Messages are kept twice, the way vanilla keeps them: the source components so a width change can
 * re-wrap them, and the wrapped visual lines the renderer walks. Wrapping on arrival rather than on
 * draw keeps the per-frame path free of allocation.
 * <p>
 * Fields are public and mutable because the settings panel edits them directly and a pair of
 * accessors per field would only be ceremony. Everything here is touched on the render thread only:
 * messages arrive through {@code ChatComponent}, drawing happens during gui extraction, and the
 * panel runs in the ImGui frame.
 */
public final class ChatWindow {

    public record Source(Component text, int addedAt, String stamp) {}

    public record Line(FormattedCharSequence text, int addedAt) {}

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss");

    // unit and record separators, so a filter pattern can hold any printable character
    private static final String FIELD = String.valueOf((char) 0x1F);
    private static final String RECORD = String.valueOf((char) 0x1E);

    private final Deque<Source> sources = new ArrayDeque<>();
    private final List<Line> visual = new ArrayList<>();
    // How many visual lines each source wrapped to, parallel to sources.
    private final Deque<Integer> lineCounts = new ArrayDeque<>();

    private int wrappedAt = -1;
    private boolean wrappedStamps;

    public String name;
    public Pattern filter;
    public int x;
    public int y;
    public int width;
    public int height;
    public boolean timestamps;

    /**
     * Edit buffers for the settings panel. Held here rather than built per frame, since the panel
     * redraws at frame rate and ImGui wants a stable object to write into.
     */
    public final ImString nameBuffer = new ImString(32);
    public final ImString filterBuffer = new ImString(128);
    public final ImInt xBuffer = new ImInt();
    public final ImInt yBuffer = new ImInt();
    public final ImInt widthBuffer = new ImInt();
    public final ImInt heightBuffer = new ImInt();
    public final ImBoolean timestampBuffer = new ImBoolean();

    public ChatWindow(String name, String filter, int x, int y, int width, int height, boolean timestamps) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.timestamps = timestamps;
        setFilter(filter);
        syncBuffers();
    }

    /**
     * An unparseable pattern leaves the window accepting everything rather than throwing, since the
     * text is typed a character at a time and most prefixes of a real pattern are invalid.
     */
    public void setFilter(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            filter = null;
            return;
        }

        try {
            filter = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            filter = null;
        }
    }

    public String filterPattern() {
        return filter == null ? "" : filter.pattern();
    }

    public boolean accepts(String plain) {
        return filter == null || filter.matcher(plain).find();
    }

    public void add(Component text, int tick, int history, Font font) {
        rewrapIfStale(font);

        Source source = new Source(text, tick, STAMP.format(LocalTime.now()));
        sources.addLast(source);
        wrap(source, font);

        // Each source's lines are contiguous and oldest first, so trimming drops a prefix of visual.
        while (sources.size() > history) {
            sources.removeFirst();
            visual.subList(0, lineCounts.removeFirst()).clear();
        }
    }

    /**
     * Rebuilds the visual lines whenever the width or the timestamp setting changes, since the wrap
     * points move with them.
     */
    public void rewrap(Font font) {
        visual.clear();
        lineCounts.clear();
        wrappedAt = wrapWidth();
        wrappedStamps = timestamps;

        for (Source source : sources) wrap(source, font);
    }

    private void wrap(Source source, Font font) {
        int before = visual.size();
        for (FormattedCharSequence wrapped : ComponentRenderUtils.wrapComponents(display(source), wrappedAt, font)) {
            visual.add(new Line(wrapped, source.addedAt()));
        }
        lineCounts.addLast(visual.size() - before);
    }

    /**
     * The stamp is prepended before wrapping rather than drawn separately, so a long line wraps
     * around it the way the rest of the text does.
     */
    private Component display(Source source) {
        if (!timestamps) return source.text();

        // The stamp is a sibling of the message, not its parent. Appending to a styled literal makes
        // the message inherit that style wherever it does not set its own colour.
        return Component.empty()
            .append(Component.literal(source.stamp() + " ").withStyle(ChatFormatting.DARK_GRAY))
            .append(source.text());
    }

    public void rewrapIfStale(Font font) {
        if (wrapWidth() != wrappedAt || timestamps != wrappedStamps) rewrap(font);
    }

    private int wrapWidth() {
        return Math.max(1, width - 8);
    }

    /**
     * Visual lines oldest first, so the renderer walks it backwards from the bottom of the window.
     */
    public List<Line> lines() {
        return visual;
    }

    public void clear() {
        sources.clear();
        visual.clear();
        lineCounts.clear();
    }

    public void syncBuffers() {
        nameBuffer.set(name);
        filterBuffer.set(filterPattern());
        xBuffer.set(x);
        yBuffer.set(y);
        widthBuffer.set(width);
        heightBuffer.set(height);
        timestampBuffer.set(timestamps);
    }

    public void applyBuffers() {
        name = nameBuffer.get();
        setFilter(filterBuffer.get());
        x = xBuffer.get();
        y = yBuffer.get();
        width = widthBuffer.get();
        height = heightBuffer.get();
        timestamps = timestampBuffer.get();
    }

    /**
     * Windows persist as one config value rather than a key per field, since the count is not known
     * ahead of time and the settings layer is built around fixed keys.
     */
    public String encode() {
        return name + FIELD + filterPattern() + FIELD + x + FIELD + y + FIELD + width + FIELD + height
            + FIELD + timestamps;
    }

    /**
     * Trailing fields are optional so a config written by an older build still loads, with anything
     * it did not know about left at its default.
     */
    public static ChatWindow decode(String raw) {
        String[] parts = raw.split(FIELD, -1);
        if (parts.length < 6) return null;

        try {
            return new ChatWindow(parts[0], parts[1],
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]), Integer.parseInt(parts[5]),
                parts.length > 6 && Boolean.parseBoolean(parts[6]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String join(Iterable<ChatWindow> windows) {
        StringBuilder out = new StringBuilder();

        for (ChatWindow window : windows) {
            if (!out.isEmpty()) out.append(RECORD);
            out.append(window.encode());
        }

        return out.toString();
    }

    public static String[] split(String raw) {
        return raw.isBlank() ? new String[0] : raw.split(RECORD, -1);
    }
}
