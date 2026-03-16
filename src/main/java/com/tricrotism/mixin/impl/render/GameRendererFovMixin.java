package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.zoom.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modifies the field-of-view returned by {@code GameRenderer.getFov()} when
 * the zoom module is active. The multiplier is smoothly interpolated in
 * {@link Zoom#getInterpolatedFovMultiplier(float)}.
 */
@Mixin(GameRenderer.class)
public class GameRendererFovMixin {

    @Inject(method = "getFov", at = @At("TAIL"), cancellable = true)
    private void sagefang$modifyFov(Camera camera, float partialTick, boolean useFovSetting,
                                    CallbackInfoReturnable<Float> cir) {
        if (Zoom.instance.isZoomEngaged()) {
            float original = cir.getReturnValueF();
            float modified = original * Zoom.instance.getInterpolatedFovMultiplier(partialTick);
            cir.setReturnValue(Mth.clamp(modified, 10.0f, 130.0f));
        }
    }
}
