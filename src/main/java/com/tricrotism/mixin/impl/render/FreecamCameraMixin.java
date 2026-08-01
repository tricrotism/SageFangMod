package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.freecam.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When Freecam is engaged, overrides the camera's final position and rotation at
 * the tail of {@code Camera.alignWithEntity()} — after vanilla (and FreeLook's
 * rotation {@code @ModifyArgs}) have run — so the render camera detaches from the
 * player and follows the freecam controller instead.
 */
@Mixin(Camera.class)
public abstract class FreecamCameraMixin {

    @Shadow protected abstract void setPosition(Vec3 position);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void sagefang$freecamOverride(float partialTicks, CallbackInfo ci) {
        Freecam freecam = Freecam.instance;
        if (!freecam.isEngaged()) return;
        setRotation(freecam.getCameraYaw(), freecam.getCameraPitch());
        setPosition(freecam.getCameraPos());
    }
}
