package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.zoom.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modifies the field-of-view returned by {@code Camera.getFov()} when
 * the zoom module is active. The multiplier is smoothly interpolated in
 * {@link Zoom#getInterpolatedFovMultiplier(float)}.
 * <p>
 * In MC 26.1.2 the FOV calculation moved from {@code GameRenderer.getFov(...)}
 * to {@code Camera.getFov()}, which takes no partial-tick argument, so the
 * render partial tick is sourced from the client's {@code DeltaTracker}.
 */
@Mixin(Camera.class)
public class CameraFovMixin {

    @Inject(method = "getFov", at = @At("TAIL"), cancellable = true)
    private void sagefang$modifyFov(CallbackInfoReturnable<Float> cir) {
        if (Zoom.instance.isZoomEngaged()) {
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            float original = cir.getReturnValueF();
            float modified = original * Zoom.instance.getInterpolatedFovMultiplier(partialTick);
            cir.setReturnValue(Mth.clamp(modified, 10.0f, 130.0f));
        }
    }
}
