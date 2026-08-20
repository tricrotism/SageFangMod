package com.tricrotism.utils;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Shared keybind helpers: key name resolution, ImGui rebind button + scan loop.
 */
public final class KeybindUtil {

    private KeybindUtil() {}

    // Return codes for renderKeybindButton
    /**
     * No change this frame.
     */
    public static final int NO_CHANGE = Integer.MIN_VALUE;
    /**
     * User clicked the button. Caller should enter awaiting mode.
     */
    public static final int START_LISTENING = Integer.MIN_VALUE + 1;
    /**
     * User pressed Escape. Caller should clear the bind.
     */
    public static final int CLEAR_BIND = Integer.MIN_VALUE + 2;

    /**
     * Human-readable key name. Returns "None" for GLFW_KEY_UNKNOWN.
     */
    public static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "None";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R-Alt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "Key " + key;
        };
    }

    /**
     * Returns true if the key is currently held down and no screen is open.
     * Prevents keybinds from firing while typing in chat, signs, etc.
     */
    public static boolean isKeyDown(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return false;
        if (Minecraft.getInstance().gui.screen() != null) return false;
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    /**
     * Scans all keys for the first one currently pressed.
     *
     * @return GLFW key code if a key is pressed, CLEAR_BIND if escape, NO_CHANGE if nothing.
     */
    public static int scanForKeyPress() {
        long window = Minecraft.getInstance().getWindow().handle();
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            if (k == GLFW.GLFW_KEY_UNKNOWN) continue;
            if (GLFW.glfwGetKey(window, k) == GLFW.GLFW_PRESS) {
                return (k == GLFW.GLFW_KEY_ESCAPE) ? CLEAR_BIND : k;
            }
        }
        return NO_CHANGE;
    }

    /**
     * Renders a keybind button + label. Handles scan internally when awaiting.
     *
     * @return START_LISTENING if button clicked, CLEAR_BIND if escape pressed,
     * a GLFW key code if a key was bound, or NO_CHANGE.
     */
    public static int renderKeybindButton(String label, String imguiId, int currentKey, boolean awaiting) {
        String btnText = awaiting ? "Press a key..." : keyName(currentKey);
        if (ImGui.button(btnText + "##" + imguiId, 160, 0)) {
            return START_LISTENING;
        }
        ImGui.sameLine();
        ImGui.text(label);

        if (awaiting) {
            return scanForKeyPress();
        }
        return NO_CHANGE;
    }
}
