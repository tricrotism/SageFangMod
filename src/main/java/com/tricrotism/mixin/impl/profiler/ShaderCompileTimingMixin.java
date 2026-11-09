package com.tricrotism.mixin.impl.profiler;

import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.tricrotism.modules.profiler.ReloadProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Times each shader module compile for {@link ReloadProfiler}, named by its compilation key (id, stage
 * and defines). Compiles run on the render thread only, so one field holds the start. GlDevice is
 * package-private, so it is targeted by name.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public class ShaderCompileTimingMixin {

    @Unique
    private long sagefang$compileStart;

    @Inject(method = "compileShader", at = @At("HEAD"))
    private void sagefang$compileStart(@Coerce Object key, ShaderSource source, CallbackInfoReturnable<GlShaderModule> cir) {
        sagefang$compileStart = System.nanoTime();
    }

    @Inject(method = "compileShader", at = @At("RETURN"))
    private void sagefang$compileEnd(@Coerce Object key, ShaderSource source, CallbackInfoReturnable<GlShaderModule> cir) {
        ReloadProfiler.shaderCompiled(key.toString(), System.nanoTime() - sagefang$compileStart);
    }
}
