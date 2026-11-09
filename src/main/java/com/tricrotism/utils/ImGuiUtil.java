package com.tricrotism.utils;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiPlatformIO;
import imgui.ImGuiStyle;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMonitorCallback;

import java.util.List;

public class ImGuiUtil {

    public static final ImGuiUtil INSTANCE = new ImGuiUtil();
    private GLFWKeyCallback mcKeyCallback;
    private GLFWMonitorCallback mcMonitorCallback;
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw() {
        // Ctrl may be released before Tab, so the release carries no CONTROL mod. Remember the
        // decision made on the press and route the whole press/release pair the same way.
        private boolean tabToGame;

        @Override
        public void keyCallback(final long window, final int key, final int scancode, final int action, final int mods) {
            if (key == GLFW.GLFW_KEY_TAB) {
                if (action == GLFW.GLFW_PRESS) tabToGame = (mods & GLFW.GLFW_MOD_CONTROL) != 0;

                if (tabToGame) {
                    if (action == GLFW.GLFW_RELEASE) tabToGame = false;
                    if (mcKeyCallback != null) mcKeyCallback.invoke(window, key, scancode, action, mods);
                    return;
                }
            }
            super.keyCallback(window, key, scancode, action, mods);
        }

        // Every other callback in the backend chains to the one it displaced, but this one drops it,
        // which leaves Minecraft's MonitorManager deaf to displays being plugged in or unplugged.
        @Override
        public void monitorCallback(final long window, final int event) {
            if (mcMonitorCallback != null) mcMonitorCallback.invoke(window, event);
            super.monitorCallback(window, event);
        }
    };
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    public void create(final long handle) {
        ImGui.createContext();
        ImPlot.createContext();

        final ImGuiIO data = ImGui.getIO();
        data.setIniFilename("sagefang.ini");
        data.setFontGlobalScale(1F);

        data.setConfigFlags(ImGuiConfigFlags.DockingEnable | ImGuiConfigFlags.ViewportsEnable | ImGuiConfigFlags.NoMouseCursorChange);

        // Windows dragged out of Minecraft are real OS windows. Without this they each take a
        // taskbar icon and turn up as separate entries in the alt-tab switcher.
        data.setConfigViewportsNoTaskBarIcon(true);

        mcKeyCallback = GLFW.glfwSetKeyCallback(handle, null);
        GLFW.glfwSetKeyCallback(handle, mcKeyCallback);

        mcMonitorCallback = GLFW.glfwSetMonitorCallback(null);
        GLFW.glfwSetMonitorCallback(mcMonitorCallback);

        imGuiGlfw.init(handle, true);

        data.removeConfigFlags(ImGuiConfigFlags.NavEnableKeyboard | ImGuiConfigFlags.NavEnableGamepad);

        try {
            imGuiGl3.init();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error initializing ImGui", e);
        }

        applyTheme();
    }

    private void applyTheme() {
        ImGuiStyle style = ImGui.getStyle();

        // Rounding
        style.setWindowRounding(4f);
        style.setFrameRounding(2f);
        style.setGrabRounding(2f);
        style.setChildRounding(2f);
        style.setPopupRounding(2f);
        style.setScrollbarRounding(2f);
        style.setTabRounding(2f);

        // Borders
        style.setWindowBorderSize(1f);
        style.setFrameBorderSize(0f);
        style.setChildBorderSize(1f);

        // Crimson accent values
        float cr = 0.70f, cg = 0.08f, cb = 0.08f;   // main crimson
        float dr = 0.50f, dg = 0.04f, db = 0.04f;   // dark crimson
        float lr = 0.85f, lg = 0.15f, lb = 0.15f;   // light crimson (hover/active)

        // Backgrounds
        style.setColor(ImGuiCol.WindowBg, 0.06f, 0.02f, 0.02f, 0.94f);
        style.setColor(ImGuiCol.ChildBg, 0.04f, 0.01f, 0.01f, 0.50f);
        style.setColor(ImGuiCol.PopupBg, 0.08f, 0.03f, 0.03f, 0.94f);

        // Borders
        style.setColor(ImGuiCol.Border, dr, dg, db, 0.60f);
        style.setColor(ImGuiCol.BorderShadow, 0.0f, 0.0f, 0.0f, 0.0f);

        // Title bars
        style.setColor(ImGuiCol.TitleBg, 0.10f, 0.02f, 0.02f, 1.0f);
        style.setColor(ImGuiCol.TitleBgActive, cr, cg, cb, 1.0f);
        style.setColor(ImGuiCol.TitleBgCollapsed, 0.10f, 0.02f, 0.02f, 0.60f);

        // Menu bar
        style.setColor(ImGuiCol.MenuBarBg, 0.10f, 0.02f, 0.02f, 1.0f);

        // Frames (input fields, checkbox bg)
        style.setColor(ImGuiCol.FrameBg, 0.12f, 0.04f, 0.04f, 0.60f);
        style.setColor(ImGuiCol.FrameBgHovered, dr, dg, db, 0.70f);
        style.setColor(ImGuiCol.FrameBgActive, cr, cg, cb, 0.70f);

        // Buttons
        style.setColor(ImGuiCol.Button, dr, dg, db, 0.70f);
        style.setColor(ImGuiCol.ButtonHovered, cr, cg, cb, 0.85f);
        style.setColor(ImGuiCol.ButtonActive, lr, lg, lb, 1.0f);

        // Checkmark, slider grab
        style.setColor(ImGuiCol.CheckMark, lr, lg, lb, 1.0f);
        style.setColor(ImGuiCol.SliderGrab, cr, cg, cb, 1.0f);
        style.setColor(ImGuiCol.SliderGrabActive, lr, lg, lb, 1.0f);

        // Headers (collapsing headers, selectable, tree nodes)
        style.setColor(ImGuiCol.Header, dr, dg, db, 0.60f);
        style.setColor(ImGuiCol.HeaderHovered, cr, cg, cb, 0.70f);
        style.setColor(ImGuiCol.HeaderActive, lr, lg, lb, 0.80f);

        // Separator
        style.setColor(ImGuiCol.Separator, dr, dg, db, 0.50f);
        style.setColor(ImGuiCol.SeparatorHovered, cr, cg, cb, 0.70f);
        style.setColor(ImGuiCol.SeparatorActive, lr, lg, lb, 1.0f);

        // Resize grip
        style.setColor(ImGuiCol.ResizeGrip, dr, dg, db, 0.40f);
        style.setColor(ImGuiCol.ResizeGripHovered, cr, cg, cb, 0.70f);
        style.setColor(ImGuiCol.ResizeGripActive, lr, lg, lb, 1.0f);

        // Tabs
        style.setColor(ImGuiCol.Tab, 0.12f, 0.03f, 0.03f, 0.80f);
        style.setColor(ImGuiCol.TabHovered, cr, cg, cb, 0.80f);

        // Scrollbar
        style.setColor(ImGuiCol.ScrollbarBg, 0.04f, 0.01f, 0.01f, 0.50f);
        style.setColor(ImGuiCol.ScrollbarGrab, dr, dg, db, 0.60f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, cr, cg, cb, 0.70f);
        style.setColor(ImGuiCol.ScrollbarGrabActive, lr, lg, lb, 1.0f);

        // Text
        style.setColor(ImGuiCol.Text, 0.92f, 0.88f, 0.88f, 1.0f);
        style.setColor(ImGuiCol.TextDisabled, 0.50f, 0.35f, 0.35f, 1.0f);

        // Tooltip
        style.setColor(ImGuiCol.TextSelectedBg, cr, cg, cb, 0.40f);

        // Nav highlight
        style.setColor(ImGuiCol.NavHighlight, 0.0f, 0.0f, 0.0f, 0.0f);

        // Plot lines & histogram (for ImGui.plotLines used in graphs)
        style.setColor(ImGuiCol.PlotLines, lr, lg, lb, 1.0f);
        style.setColor(ImGuiCol.PlotLinesHovered, 1.0f, 0.30f, 0.30f, 1.0f);
        style.setColor(ImGuiCol.PlotHistogram, cr, cg, cb, 1.0f);
        style.setColor(ImGuiCol.PlotHistogramHovered, lr, lg, lb, 1.0f);
    }

    /**
     * Whether the cursor sits on a menu the user dragged out of the Minecraft window. Index 0 is
     * always the main viewport, so the scan starts past it.
     */
    private static boolean onSecondaryViewport() {
        final ImGuiPlatformIO platform = ImGui.getPlatformIO();

        for (int i = 1; i < platform.getViewportsSize(); i++) {
            final long handle = platform.getViewports(i).getPlatformHandle();
            if (handle != 0L && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) != 0) return true;
        }

        return false;
    }

    public void draw(final List<Menu> menu) {
        try {
            // ImGui's GLFW callbacks fire whether or not a screen is open, so during normal
            // gameplay, with the cursor grabbed and the pointer hidden and drifting, clicks meant
            // for the world were also landing on the overlay. Ignore the mouse until the cursor is free.
            // Minecraft's grab says nothing about a window dragged out of it, so a focused viewport
            // keeps the mouse either way. NoMouse forces MousePos to -FLT_MAX, and flipping it under
            // a held slider is what made the grab jump.
            final ImGuiIO io = ImGui.getIO();
            if (Minecraft.getInstance().mouseHandler.isMouseGrabbed() && !onSecondaryViewport()) {
                io.addConfigFlags(ImGuiConfigFlags.NoMouse);
            } else {
                io.removeConfigFlags(ImGuiConfigFlags.NoMouse);
            }

            imGuiGl3.newFrame();
            imGuiGlfw.newFrame();
            ImGui.newFrame();

            for (Menu m : menu) {
                m.frame(ImGui.getIO());
            }

            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());

            if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                final long pointer = GLFW.glfwGetCurrentContext();
                ImGui.updatePlatformWindows();
                ImGui.renderPlatformWindowsDefault();

                GLFW.glfwMakeContextCurrent(pointer);
            }
        } catch (Exception e) {
            SageFang.LOGGER.error("Error drawing ImGui for {}", menu.getClass().getSimpleName(), e);
        }
    }

    /**
     * Text drawn as text rather than as a printf format string.
     * <p>
     * imgui-java hands the Java string to ImGui as the format argument with a single {@code NULL}
     * vararg behind it, so any percent sign in the text is parsed as a conversion. A rendered
     * {@code "12.0%  n.m.c.Foo"} reads as {@code %n}, which writes through that {@code NULL} and
     * kills the process, and server-supplied strings reach several of these call sites.
     * {@link ImGui#textUnformatted(String)} is the one entry point that parses nothing, so every
     * variant below routes through it.
     */
    public static void textDisabled(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled));
        ImGui.textUnformatted(text);
        ImGui.popStyleColor();
    }

    public static void textColored(int color, String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, color);
        ImGui.textUnformatted(text);
        ImGui.popStyleColor();
    }

    public static void textColored(float r, float g, float b, float a, String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a);
        ImGui.textUnformatted(text);
        ImGui.popStyleColor();
    }

    public static void textWrapped(String text) {
        ImGui.pushTextWrapPos();
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
    }

    public static void setTooltip(String text) {
        ImGui.beginTooltip();
        ImGui.textUnformatted(text);
        ImGui.endTooltip();
    }

    public void shutdown() {
        imGuiGl3.shutdown();
        ImGui.destroyContext();
        ImPlot.destroyContext();
    }

}
