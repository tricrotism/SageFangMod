package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.freelook.FreeLook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Overrides the camera rotation angles in {@code Camera.alignWithEntity()} when
 * free look is engaged. All calls to {@code setRotation(float, float)}
 * within alignWithEntity() are intercepted so the camera uses the free look
 * controller's yaw/pitch instead of the entity's view angles.
 */
@Mixin(Camera.class)
public class FreeLookCameraMixin {

    @ModifyArgs(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V")
    )
    private void sagefang$overrideRotation(Args args) {
        if (FreeLook.instance.isFreeLookEngaged()) {
            args.set(0, FreeLook.instance.getCameraYaw());
            args.set(1, FreeLook.instance.getCameraPitch());
        }
    }
}
