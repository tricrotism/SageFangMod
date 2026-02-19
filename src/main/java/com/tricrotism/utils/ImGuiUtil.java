package com.tricrotism.utils;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImGuiIO;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiCol;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ImGuiUtil {

    public static final ImGuiUtil INSTANCE = new ImGuiUtil();
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    public void create(final long handle) {
        ImGui.createContext();
        ImPlot.createContext();

        final ImGuiIO data = ImGui.getIO();
        data.setIniFilename("sagefang.ini");
        data.setFontGlobalScale(1F);

        data.setConfigFlags(ImGuiConfigFlags.DockingEnable | ImGuiConfigFlags.ViewportsEnable);

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
        style.setColor(ImGuiCol.WindowBg,           0.06f, 0.02f, 0.02f, 0.94f);
        style.setColor(ImGuiCol.ChildBg,            0.04f, 0.01f, 0.01f, 0.50f);
        style.setColor(ImGuiCol.PopupBg,            0.08f, 0.03f, 0.03f, 0.94f);

        // Borders
        style.setColor(ImGuiCol.Border,             dr, dg, db, 0.60f);
        style.setColor(ImGuiCol.BorderShadow,       0.0f, 0.0f, 0.0f, 0.0f);

        // Title bars
        style.setColor(ImGuiCol.TitleBg,            0.10f, 0.02f, 0.02f, 1.0f);
        style.setColor(ImGuiCol.TitleBgActive,      cr, cg, cb, 1.0f);
        style.setColor(ImGuiCol.TitleBgCollapsed,   0.10f, 0.02f, 0.02f, 0.60f);

        // Menu bar
        style.setColor(ImGuiCol.MenuBarBg,          0.10f, 0.02f, 0.02f, 1.0f);

        // Frames (input fields, checkbox bg)
        style.setColor(ImGuiCol.FrameBg,            0.12f, 0.04f, 0.04f, 0.60f);
        style.setColor(ImGuiCol.FrameBgHovered,     dr, dg, db, 0.70f);
        style.setColor(ImGuiCol.FrameBgActive,      cr, cg, cb, 0.70f);

        // Buttons
        style.setColor(ImGuiCol.Button,             dr, dg, db, 0.70f);
        style.setColor(ImGuiCol.ButtonHovered,      cr, cg, cb, 0.85f);
        style.setColor(ImGuiCol.ButtonActive,       lr, lg, lb, 1.0f);

        // Checkmark, slider grab
        style.setColor(ImGuiCol.CheckMark,          lr, lg, lb, 1.0f);
        style.setColor(ImGuiCol.SliderGrab,         cr, cg, cb, 1.0f);
        style.setColor(ImGuiCol.SliderGrabActive,   lr, lg, lb, 1.0f);

        // Headers (collapsing headers, selectable, tree nodes)
        style.setColor(ImGuiCol.Header,             dr, dg, db, 0.60f);
        style.setColor(ImGuiCol.HeaderHovered,      cr, cg, cb, 0.70f);
        style.setColor(ImGuiCol.HeaderActive,       lr, lg, lb, 0.80f);

        // Separator
        style.setColor(ImGuiCol.Separator,          dr, dg, db, 0.50f);
        style.setColor(ImGuiCol.SeparatorHovered,   cr, cg, cb, 0.70f);
        style.setColor(ImGuiCol.SeparatorActive,    lr, lg, lb, 1.0f);

        // Resize grip
        style.setColor(ImGuiCol.ResizeGrip,         dr, dg, db, 0.40f);
        style.setColor(ImGuiCol.ResizeGripHovered,  cr, cg, cb, 0.70f);
        style.setColor(ImGuiCol.ResizeGripActive,   lr, lg, lb, 1.0f);

        // Tabs
        style.setColor(ImGuiCol.Tab,                0.12f, 0.03f, 0.03f, 0.80f);
        style.setColor(ImGuiCol.TabHovered,         cr, cg, cb, 0.80f);

        // Scrollbar
        style.setColor(ImGuiCol.ScrollbarBg,        0.04f, 0.01f, 0.01f, 0.50f);
        style.setColor(ImGuiCol.ScrollbarGrab,      dr, dg, db, 0.60f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, cr, cg, cb, 0.70f);
        style.setColor(ImGuiCol.ScrollbarGrabActive,  lr, lg, lb, 1.0f);

        // Text
        style.setColor(ImGuiCol.Text,               0.92f, 0.88f, 0.88f, 1.0f);
        style.setColor(ImGuiCol.TextDisabled,       0.50f, 0.35f, 0.35f, 1.0f);

        // Tooltip
        style.setColor(ImGuiCol.TextSelectedBg,     cr, cg, cb, 0.40f);

        // Nav highlight
        style.setColor(ImGuiCol.NavHighlight,       0.0f, 0.0f, 0.0f, 0.0f);

        // Plot lines & histogram (for ImGui.plotLines used in graphs)
        style.setColor(ImGuiCol.PlotLines,          lr, lg, lb, 1.0f);
        style.setColor(ImGuiCol.PlotLinesHovered,   1.0f, 0.30f, 0.30f, 1.0f);
        style.setColor(ImGuiCol.PlotHistogram,      cr, cg, cb, 1.0f);
        style.setColor(ImGuiCol.PlotHistogramHovered, lr, lg, lb, 1.0f);
    }

    public void draw(final List<Menu> menu) {
        try {
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
            SageFang.LOGGER.error("Error drawing ImGui for " + menu.getClass().getSimpleName(), e);
        }
    }

    public void shutdown() {
        imGuiGl3.shutdown();
        ImGui.destroyContext();
        ImPlot.destroyContext();
    }

}
