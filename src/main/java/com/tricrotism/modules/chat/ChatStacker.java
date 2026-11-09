package com.tricrotism.modules.chat;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.text.MiniMessage;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Folds a repeated chat message into one line with a {@code (N)} count, as long as the earlier copy
 * is still near the bottom of chat. Runs of blank lines collapse to one.
 * <p>
 * {@code ChatStackerMixin} passes every new {@link GuiMessage} to {@link #stack} before vanilla
 * stores and splits it. The count lives in the stored message's text, so it survives a rescale that
 * rebuilds the lines. Render thread only.
 */
public final class ChatStacker extends Module {

    public static final ChatStacker instance = new ChatStacker();

    private static final String SEPARATOR_CHARS = "-=+*_~";

    private final Settings.Int recency =
        integer("Recency (lines)", "recency", "How many recent chat lines to search for a repeat", 12, 1, 100);

    // stack() evicts every entry without a line in the recency window, so this stays small.
    private final Map<GuiMessage, Stack> stacks = new IdentityHashMap<>();
    private GuiMessage lastBlank;
    private int scan;

    private static final class Stack {
        final Component original;
        final int count;
        int seenScan;

        Stack(Component original, int count, int seenScan) {
            this.original = original;
            this.count = count;
            this.seenScan = seenScan;
        }
    }

    private ChatStacker() {
        super("chatstacker", "Chat Stacker", "Show repeated chat messages once with a (N) count.", Category.UTILITY);
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    public void reset() {
        stacks.clear();
        lastBlank = null;
    }

    /**
     * Removes the earlier copy from {@code allMessages} and {@code lines}, both newest first.
     *
     * @return the message vanilla should store instead of {@code message}
     */
    public GuiMessage stack(GuiMessage message, List<GuiMessage> allMessages, List<GuiMessage.Line> lines,
                            Predicate<GuiMessage> filter) {
        if (!filter.test(message)) return message;

        String plain = message.content().getString();
        if (plain.isBlank()) {
            if (lastBlank != null && !allMessages.isEmpty() && allMessages.getFirst() == lastBlank) {
                allMessages.removeFirst();
                removeLines(lines, 0, lastBlank);
            }
            lastBlank = message;
            return message;
        }
        lastBlank = null;
        if (isSeparator(plain)) return message;

        int scan = ++this.scan;
        int window = Math.min(recency.get(), lines.size());
        GuiMessage match = null;
        Stack matchStack = null;
        int matchAt = -1;
        GuiMessage previous = null;

        for (int i = 0; i < window; i++) {
            GuiMessage parent = lines.get(i).parent();
            if (parent == previous) continue;
            previous = parent;

            Stack stack = stacks.get(parent);
            if (stack != null) stack.seenScan = scan;
            if (match == null && (stack != null ? stack.original : parent.content()).equals(message.content())) {
                match = parent;
                matchStack = stack;
                matchAt = i;
            }
        }

        for (Iterator<Stack> it = stacks.values().iterator(); it.hasNext(); ) {
            if (it.next().seenScan != scan) it.remove();
        }

        if (match == null) return message;

        int count = matchStack == null ? 2 : matchStack.count + 1;
        stacks.remove(match);
        removeLines(lines, matchAt, match);
        for (int i = 0; i < allMessages.size(); i++) {
            if (allMessages.get(i) == match) {
                allMessages.remove(i);
                break;
            }
        }

        Component suffix = MinecraftClientAudiences.of().asNative(
            MiniMessage.format("<gray> (<count>)", Placeholder.unparsed("count", String.valueOf(count))));
        GuiMessage stacked = new GuiMessage(message.addedTime(), message.content().copy().append(suffix),
            message.signature(), message.source(), message.tag());
        stacks.put(stacked, new Stack(message.content(), count, scan));
        return stacked;
    }

    // A message's lines are contiguous, so the run starting at from is all of them.
    private static void removeLines(List<GuiMessage.Line> lines, int from, GuiMessage parent) {
        int to = from;
        while (to < lines.size() && lines.get(to).parent() == parent) to++;
        lines.subList(from, to).clear();
    }

    private static boolean isSeparator(String plain) {
        String stripped = plain.strip();
        for (int i = 0; i < stripped.length(); i++) {
            if (SEPARATOR_CHARS.indexOf(stripped.charAt(i)) < 0) return false;
        }
        return true;
    }
}
