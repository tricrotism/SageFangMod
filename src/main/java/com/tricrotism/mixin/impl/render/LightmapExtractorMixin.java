package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.render.FullBright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TAIL only fires after a full update. The early returns keep last frame's state, which was already
 * forced. {@code tick()} schedules a full update every tick, so turning the module off takes effect
 * within a tick.
 */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapExtractorMixin {

    @Inject(method = "extract", at = @At("TAIL"))
    private void sagefang$fullBright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (!FullBright.instance.isActive() || !FullBright.instance.forcesLightmap()) return;

        // White ambient light maxes out every lightmap texel. Darkness and boss fog would dim it again.
        state.ambientColor = LightmapRenderStateExtractor.WHITE;
        state.darknessEffectScale = 0.0f;
        state.bossOverlayWorldDarkening = 0.0f;
    }
}
