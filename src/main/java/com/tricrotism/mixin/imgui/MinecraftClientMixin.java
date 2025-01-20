package com.tricrotism.mixin.imgui;

import com.tricrotism.Main;
import com.tricrotism.Menu;
import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Shadow
    @Final
    private Window window;
    @Unique
    private static int gFontTexture = -1;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V"))
    void onInitRenders(RunArgs args, CallbackInfo ci) {
        Main.imGuiGlfw.init(window.getHandle(), true);
        Main.imGuiGl3.init(Main.glslVersion);

        ImGuiIO io = ImGui.getIO();
        ImFontAtlas fonts = io.getFonts();
        fonts.clear();
        fonts.clearTexData();
        updateFontsTexture(io.getFonts());
    }

    @Inject(method = "render", at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(I)V",
            ordinal = 0
    ))
    public void renderImGui(boolean tick, CallbackInfo ci) {
        Main.imGuiGlfw.newFrame();
        ImGui.newFrame();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V"))
    void onRender(boolean bl, CallbackInfo ci) {
        Main.MENUS.forEach(Menu::frame);
        ImGui.render();
        Main.imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    @Unique
    private static void updateFontsTexture(ImFontAtlas atlas) {
        if (gFontTexture != -1) {
            GL11.glDeleteTextures(gFontTexture);
        }

        final ImInt width = new ImInt();
        final ImInt height = new ImInt();
        final ByteBuffer buffer = atlas.getTexDataAsRGBA32(width, height);

        gFontTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, gFontTexture);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width.get(), height.get(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        atlas.setTexID(gFontTexture);
    }
}