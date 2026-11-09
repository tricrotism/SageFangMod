package com.tricrotism.modules.chat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Drag and resize the chat windows in place.
 * <p>
 * Positions are numbers in the settings panel, which is a poor way to line a window up against
 * anything. Here the windows are drawn where they will actually sit, with the game still visible
 * behind them, so placement is done by eye.
 * <p>
 * Grabbing the bottom-right corner resizes, anywhere else moves. Windows anchor to the bottom of the
 * screen, so dragging one downward lowers {@code y} rather than raising it.
 */
public final class ChatLayoutScreen extends Screen {

    private static final int GRIP = 8;
    private static final int OUTLINE = 0xFFFFFFFF;
    private static final int OUTLINE_ACTIVE = 0xFF5AA9FF;
    private static final int PANEL = 0x60000000;

    private final SageChat owner;

    private ChatWindow held;
    private boolean resizing;
    private int grabX;
    private int grabY;

    public ChatLayoutScreen(SageChat owner) {
        super(Component.literal("Chat layout"));
        this.owner = owner;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        for (ChatWindow window : owner.windows()) {
            int bottom = height - window.y;
            int top = bottom - window.height;
            int right = window.x + window.width;
            boolean active = window == held || contains(window, mouseX, mouseY);

            graphics.fill(window.x, top, right, bottom, PANEL);
            outline(graphics, window.x, top, right, bottom, active ? OUTLINE_ACTIVE : OUTLINE);
            graphics.fill(right - GRIP, bottom - GRIP, right, bottom, active ? OUTLINE_ACTIVE : OUTLINE);

            graphics.text(font, window.name, window.x + 4, top + 4, 0xFFFFFFFF);
            graphics.text(font, window.width + " x " + window.height, window.x + 4, top + 16, 0xFFAAAAAA);
        }

        graphics.text(font, "Drag to move, corner to resize. Escape saves.", 6, 6, 0xFFFFFFFF);
    }

    private static void outline(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int colour) {
        graphics.fill(left, top, right, top + 1, colour);
        graphics.fill(left, bottom - 1, right, bottom, colour);
        graphics.fill(left, top, left + 1, bottom, colour);
        graphics.fill(right - 1, top, right, bottom, colour);
    }

    private boolean contains(ChatWindow window, double x, double y) {
        int bottom = height - window.y;

        return x >= window.x && x <= window.x + window.width && y >= bottom - window.height && y <= bottom;
    }

    private boolean onGrip(ChatWindow window, double x, double y) {
        int bottom = height - window.y;
        int right = window.x + window.width;

        return x >= right - GRIP && x <= right && y >= bottom - GRIP && y <= bottom;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        List<ChatWindow> windows = owner.windows();

        // topmost first, so the window drawn last is the one you grab
        for (int i = windows.size() - 1; i >= 0; i--) {
            ChatWindow window = windows.get(i);
            if (!contains(window, event.x(), event.y())) continue;

            held = window;
            resizing = onGrip(window, event.x(), event.y());
            grabX = (int) event.x() - window.x;
            grabY = (int) event.y() - (height - window.y);

            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (held == null) return super.mouseDragged(event, dragX, dragY);

        if (resizing) {
            // the top edge stays where it is and the bottom follows the cursor, so y has to move
            // with the height to keep the window anchored to the same distance off the screen bottom
            int top = (height - held.y) - held.height;
            int bottom = Math.max(top + 2 * GRIP, (int) event.y());

            held.width = Math.max(40, (int) event.x() - held.x);
            held.height = bottom - top;
            held.y = height - bottom;
        } else {
            int bottom = (int) event.y() - grabY;

            held.x = Math.clamp((int) event.x() - grabX, 0, Math.max(0, width - held.width));
            held.y = Math.clamp(height - bottom, 0, Math.max(0, height - held.height));
        }

        held.syncBuffers();

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (held == null) return super.mouseReleased(event);

        held = null;
        resizing = false;
        owner.save();

        return true;
    }

    @Override
    public void onClose() {
        owner.save();
        super.onClose();
    }
}
