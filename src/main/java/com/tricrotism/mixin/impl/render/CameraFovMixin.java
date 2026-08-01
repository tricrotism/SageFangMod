package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.zoom.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modifies the rendered field-of-view when the zoom module is active. The
 * multiplier is smoothly interpolated in {@link Zoom#getInterpolatedFovMultiplier(float)}.
 * <p>
 * In MC 26.1.2 the world FOV is the {@code Camera.fov} field, which
 * {@code Camera.update(...)} fills each frame via {@code calculateFov(partialTick)}.
 * The public {@code getFov()} getter only feeds horizon projection, so we inject
 * into {@code calculateFov} to affect the actual projection matrix.
 */
@Mixin(Camera.class)
public class CameraFovMixin {

    @Inject(method = "calculateFov", at = @At("TAIL"), cancellable = true)
    private void sagefang$modifyFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (Zoom.instance.isZoomEngaged()) {
            float original = cir.getReturnValueF();
            float modified = original * Zoom.instance.getInterpolatedFovMultiplier(partialTicks);
            // Floor kept at 1° (not 0) because a zero/negative FOV breaks the projection matrix;
            // otherwise uncapped so zoom no longer hits a visible ceiling.
            cir.setReturnValue(Mth.clamp(modified, 1.0f, 130.0f));
        }
    }
}
