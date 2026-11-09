package com.tricrotism.modules.chat;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import io.avaje.config.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat as a set of independent windows rather than one box.
 * <p>
 * Each {@link ChatWindow} owns a position, a size and a filter, and every message is offered to all
 * of them, so a trade window and a mentions window can both catch the same line. The defaults match
 * vanilla so a single unfiltered window is indistinguishable from the stock chat: a {@code 0.5}
 * background alpha, vanilla's own row height and Chat Line Spacing option, a background drawn per
 * row rather than over the whole window, and vanilla's squared fade over a 200 tick life. Scrolling
 * rides on vanilla's own scroll position, which keeps working while its renderer is cancelled.
 * <p>
 * <b>Replacing vanilla.</b> While {@link #replaceVanilla} is off this draws alongside the stock chat,
 * which is what lets it be built and compared without losing the real thing. Turning it on cancels
 * {@code ChatComponent.extractRenderState} from the mixin, at which point these windows are the chat.
 * It stays off by default because other chat mods hook the same class.
 */
public final class SageChat extends Module {

    public static final SageChat instance = new SageChat();

    /**
     * Vanilla draws its text this far in from the left edge of the chat box.
     */
    private static final int INDENT = 4;
    private static final int FONT_HEIGHT = 9;

    private final Settings.Bool replaceVanilla =
        bool("Replace vanilla chat", "replacevanilla",
            "Cancel the stock chat and let these windows be the only chat. Conflicts with other chat mods.", false);
    private final Settings.Decimal backgroundOpacity =
        decimal("Background opacity", "background", "Window background alpha. Vanilla is 0.5.", 0.5, 0.0, 1.0);
    private final Settings.Decimal textOpacity =
        decimal("Text opacity", "textopacity", "Message alpha at full brightness. Vanilla is 1.0.", 1.0, 0.0, 1.0);
    private final Settings.Int lineSpacing =
        integer("Line spacing", "linespacing", "Extra pixels between rows, on top of vanilla's own row height.", 0, 0, 12);
    private final Settings.Int history =
        integer("History", "history", "Messages kept per window. Vanilla keeps 100.", 100, 10, 1000);
    private final Settings.Int fadeTicks =
        integer("Fade after (ticks)", "fadeticks", "How long a message stays before fading. Vanilla is 200.", 200, 20, 2000);

    private final List<ChatWindow> windows = new ArrayList<>();

    private int tick;
    private int removing = -1;

    private SageChat() {
        super("sagechat", "Chat Windows",
            "Independent, filtered chat windows drawn with vanilla's spacing and fade.", Category.RENDER);
        load();
    }

    public boolean replacesVanilla() {
        return isActive() && replaceVanilla.get();
    }

    /**
     * The live window list, handed to {@link ChatLayoutScreen} so a drag edits the same objects the
     * renderer reads rather than a copy that has to be merged back.
     */
    public List<ChatWindow> windows() {
        return windows;
    }

    @Override
    public void onDeactivate() {
        for (ChatWindow window : windows) window.clear();
    }

    /**
     * Called from the chat mixin for every message the client accepts, whichever way it arrived.
     */
    public void onMessage(Component text) {
        if (!isActive() || windows.isEmpty()) return;

        String plain = text.getString();
        int cap = history.get();

        for (ChatWindow window : windows) {
            if (window.accepts(plain)) window.add(text, tick, cap, mc.font);
        }
    }

    public void render(GuiGraphicsExtractor graphics, Font font, boolean focused, int lineOffset) {
        if (!isActive() || windows.isEmpty()) return;

        int rowHeight = rowHeight();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        for (ChatWindow window : windows) {
            window.rewrapIfStale(font);
            renderWindow(graphics, font, window, rowHeight, screenHeight, focused, lineOffset);
        }
    }

    /**
     * Vanilla's row step is nine pixels scaled by the player's Chat Line Spacing option, not the
     * eight of {@link ChatComponent#MESSAGE_BOTTOM_TO_MESSAGE_TOP}. Eight overlaps the nine pixel
     * font by a pixel and ignores the option entirely.
     */
    private int rowHeight() {
        return (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0)) + lineSpacing.get();
    }

    /**
     * Windows anchor to the bottom of the screen the way vanilla chat does, so {@code y} is the gap
     * below the window rather than an absolute top. Resizing the game keeps them where they were.
     * <p>
     * The background is one fill per drawn row carrying that row's fade, which is what vanilla does.
     * A single rect over the whole window would stay on screen after every message had faded out.
     */
    private void renderWindow(GuiGraphicsExtractor graphics, Font font, ChatWindow window,
                              int rowHeight, int screenHeight, boolean focused, int lineOffset) {
        List<ChatWindow.Line> lines = window.lines();
        if (lines.isEmpty()) return;

        int bottom = screenHeight - window.y;
        int top = bottom - window.height;
        int visibleRows = Math.max(0, window.height / rowHeight);
        int offset = Math.clamp(lineOffset, 0, Math.max(0, lines.size() - visibleRows));

        int drawn = 0;

        for (int i = lines.size() - 1 - offset; i >= 0; i--) {
            int rowBottom = bottom - drawn * rowHeight;
            int rowTop = rowBottom - rowHeight;
            if (rowTop < top) break;

            ChatWindow.Line line = lines.get(i);
            double fade = fadeOf(line.addedAt(), focused);
            if (fade <= 0.0) break;

            int background = (int) (fade * backgroundOpacity.get() * 255.0) << 24;
            if (background != 0) graphics.fill(window.x, rowTop, window.x + window.width, rowBottom, background);

            int text = (int) (fade * textOpacity.get() * 255.0) << 24 | 0xFFFFFF;
            graphics.text(font, line.text(), window.x + INDENT, rowBottom - FONT_HEIGHT, text);
            drawn++;
        }
    }

    /**
     * Vanilla's curve: full brightness for most of a message's life, then a squared ramp across the
     * final tenth of the window. An open chat screen does not fade at all. Returned as a multiplier
     * rather than an alpha because the row's background and its text both carry it.
     */
    private double fadeOf(int addedAt, boolean focused) {
        if (focused) return 1.0;

        double age = tick - addedAt;
        double fade = Math.clamp((1.0 - age / fadeTicks.get()) * 10.0, 0.0, 1.0);

        return fade * fade;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tick++;
    }

    private void load() {
        windows.clear();

        for (String raw : ChatWindow.split(Config.get(baseConfig + ".windows", ""))) {
            ChatWindow window = ChatWindow.decode(raw);
            if (window != null) windows.add(window);
        }

        if (windows.isEmpty()) windows.add(vanillaLike());
    }

    void save() {
        Config.setProperty(baseConfig + ".windows", ChatWindow.join(windows));
    }

    /**
     * Vanilla's own geometry: {@code getWidth(1.0)} is 320, {@code getHeight(1.0)} is 180, and the
     * box sits 40 pixels off the bottom.
     */
    private static ChatWindow vanillaLike() {
        return new ChatWindow("Chat", "", INDENT, 40, 320, 180, false);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        if (ImGui.button("Add window##sageChatAdd")) {
            windows.add(new ChatWindow("Window " + (windows.size() + 1), "", INDENT, 40, 320, 180, false));
            save();
        }

        ImGui.sameLine();

        if (ImGui.button("Reset to vanilla##sageChatReset")) {
            windows.clear();
            windows.add(vanillaLike());
            save();
        }

        ImGui.sameLine();

        // opening a screen from the ImGui frame would run inside the render pass, so hop to the
        // client's own queue and let it happen between frames
        if (ImGui.button("Edit layout##sageChatLayout")) {
            mc.execute(() -> mc.gui.setScreen(new ChatLayoutScreen(this)));
        }

        for (int i = 0; i < windows.size(); i++) {
            ImGui.pushID(i);
            editWindow(windows.get(i), i);
            ImGui.popID();
        }

        if (removing >= 0 && removing < windows.size()) {
            windows.remove(removing);
            removing = -1;
            save();
        }
    }

    private void editWindow(ChatWindow window, int index) {
        if (!ImGui.treeNode(window.name)) return;

        boolean changed = ImGui.inputText("Name", window.nameBuffer);
        changed |= ImGui.inputText("Filter (regex, blank accepts all)", window.filterBuffer);
        changed |= ImGui.inputInt("X", window.xBuffer);
        changed |= ImGui.inputInt("Y from bottom", window.yBuffer);
        changed |= ImGui.inputInt("Width", window.widthBuffer);
        changed |= ImGui.inputInt("Height", window.heightBuffer);
        changed |= ImGui.checkbox("Timestamps", window.timestampBuffer);

        if (changed) {
            window.applyBuffers();
            save();
        }

        ImGui.textUnformatted("Lines: " + window.lines().size());

        if (ImGui.button("Clear")) window.clear();

        ImGui.sameLine();

        if (ImGui.button("Remove")) removing = index;

        ImGui.treePop();
    }
}
