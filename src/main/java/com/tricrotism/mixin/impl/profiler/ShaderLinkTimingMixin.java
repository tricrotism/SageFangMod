package com.tricrotism.mixin.impl.profiler;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tricrotism.modules.profiler.ReloadProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Times each program link for {@link ReloadProfiler}, named by the pipeline's debug label. Drivers that
 * defer compilation do the real work here, so this is where a heavy pack shader shows up on them.
 */
@Mixin(GlProgram.class)
public class ShaderLinkTimingMixin {

    @Unique
    private static long sagefang$linkStart;

    @Inject(method = "link", at = @At("HEAD"))
    private static void sagefang$linkStart(GlShaderModule vertex, GlShaderModule fragment, VertexFormat[] formats, String label,
                                           CallbackInfoReturnable<GlProgram> cir) {
        sagefang$linkStart = System.nanoTime();
    }

    @Inject(method = "link", at = @At("RETURN"))
    private static void sagefang$linkEnd(GlShaderModule vertex, GlShaderModule fragment, VertexFormat[] formats, String label,
                                         CallbackInfoReturnable<GlProgram> cir) {
        ReloadProfiler.programLinked(label, System.nanoTime() - sagefang$linkStart);
    }
}
