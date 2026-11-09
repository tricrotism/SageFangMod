package com.tricrotism.mixin.impl.profiler;

import com.tricrotism.modules.profiler.ReloadProfiler;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts and times every folder listing in a zip pack for {@link ReloadProfiler}.
 * <p>
 * A zip pack answers each listing by walking every entry in the archive, once per overlay and per
 * namespace, so the total here grows with entries times overlays times namespaces. Listings run on
 * the worker pool, so the start time is per thread.
 */
@Mixin(FilePackResources.class)
public class PackListingTimingMixin {

    @Inject(method = "listResources", at = @At("HEAD"))
    private void sagefang$listStart(PackType type, String namespace, String path, PackResources.ResourceOutput output, CallbackInfo ci) {
        ReloadProfiler.listingStart();
    }

    @Inject(method = "listResources", at = @At("RETURN"))
    private void sagefang$listEnd(PackType type, String namespace, String path, PackResources.ResourceOutput output, CallbackInfo ci) {
        ReloadProfiler.listingEnd();
    }
}
