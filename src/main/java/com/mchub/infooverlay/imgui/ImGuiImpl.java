/*
 * This file is part of fabric-imgui-example-mod - https://github.com/FlorianMichael/fabric-imgui-example-mod
 * by FlorianMichael/EnZaXD and contributors
 */
package com.mchub.infooverlay.imgui;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.GLFW;

public class ImGuiImpl {
    public static final ImGuiImpl INSTANCE = new ImGuiImpl();
    private final ImGuiImplGlfw imGuiImplGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiImplGl3 = new ImGuiImplGl3();

    public void create(final long handle) {
        ImGui.createContext();
        ImPlot.createContext();

        final ImGuiIO data = ImGui.getIO();
        data.setIniFilename("modid.ini"); // TODO; Change this to your modid
        data.setFontGlobalScale(1F);

        // If you want to have custom fonts, you can use the following code here

//        {
//            final ImFontAtlas fonts = data.getFonts();
//            final ImFontGlyphRangesBuilder rangesBuilder = new ImFontGlyphRangesBuilder();
//
//            rangesBuilder.addRanges(data.getFonts().getGlyphRangesDefault());
//            rangesBuilder.addRanges(data.getFonts().getGlyphRangesCyrillic());
//            rangesBuilder.addRanges(data.getFonts().getGlyphRangesJapanese());
//
//            final short[] glyphRanges = rangesBuilder.buildRanges();
//
//            final ImFontConfig basicConfig = new ImFontConfig();
//            basicConfig.setGlyphRanges(data.getFonts().getGlyphRangesCyrillic());
//
//            final List<ImFont> generatedFonts = new ArrayList<>();
//            for (int i = 5 /* MINIMUM_FONT_SIZE */; i < 50 /* MAXIMUM_FONT_SIZE */; i++) {
//                basicConfig.setName("<Font Name> " + i + "px");
//                generatedFonts.add(fonts.addFontFromMemoryTTF(IOUtils.toByteArray(Objects.requireNonNull(ImGuiImpl.class.getResourceAsStream("<File Path>"))), i, basicConfig, glyphRanges));
//            }
//            fonts.build();
//            basicConfig.destroy();
//        }

        // The "generatedFonts" list now contains an ImFont for each scale from 5 to 50, you should save the font scales you want as global fields here to use them later:
        // For example:
        // defaultFont = generatedFonts.get(30); // Font scale is 30
        // How you can apply the font then, you can see in ExampleMixin

        data.setConfigFlags(ImGuiConfigFlags.DockingEnable);

        // In case you want to enable Viewports on Windows, you have to do this instead of the above line:
        data.setConfigFlags(ImGuiConfigFlags.DockingEnable | ImGuiConfigFlags.ViewportsEnable);

        imGuiImplGlfw.init(handle, true);
        imGuiImplGl3.init();
    }

    public void draw(final RenderInterface runnable) {
        // start frame
        imGuiImplGl3.newFrame();
        imGuiImplGlfw.newFrame(); // Handle keyboard and mouse interactions
        ImGui.newFrame();

        // do rendering logic
        runnable.render(ImGui.getIO());

        // end frame
        ImGui.render();
        imGuiImplGl3.renderDrawData(ImGui.getDrawData());

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            final long pointer = GLFW.glfwGetCurrentContext();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();

            GLFW.glfwMakeContextCurrent(pointer);
        }
    }

    public void dispose() {
        imGuiImplGl3.shutdown();

        ImGui.destroyContext();
        ImPlot.destroyContext();
    }
}
