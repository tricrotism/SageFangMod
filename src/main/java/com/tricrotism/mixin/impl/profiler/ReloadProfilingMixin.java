package com.tricrotism.mixin.impl.profiler;

import com.tricrotism.modules.profiler.ReloadProfiler;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Starts a {@link ReloadProfiler} window and makes every reload the profiled kind.
 * <p>
 * Vanilla only builds a {@code ProfiledReloadInstance}, which times each listener, when debug logging
 * is on. Forcing the flag costs two clock reads per listener stage, and reloads only happen when packs
 * change, so it never touches a frame.
 */
@Mixin(SimpleReloadInstance.class)
public class ReloadProfilingMixin {

    @Inject(method = "create", at = @At("HEAD"))
    private static void sagefang$begin(CallbackInfoReturnable<?> cir) {
        ReloadProfiler.begin();
    }

    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
    private static boolean sagefang$profiled(boolean profiled) {
        return true;
    }
}
