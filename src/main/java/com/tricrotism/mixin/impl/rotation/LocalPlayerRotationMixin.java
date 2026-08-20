package com.tricrotism.mixin.impl.rotation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tricrotism.api.rotation.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Substitutes the transmitted rotation inside {@code sendPosition()} without touching the entity's
 * real rotation, so the camera is unaffected.
 * <p>
 * Both getters are wrapped across the whole method rather than just the packet constructor: the same
 * method also computes the "did rotation change" delta and updates {@code yRotLast}/{@code xRotLast}
 * afterwards. Spoofing only the packet would leave that bookkeeping tracking the real rotation, which
 * makes every subsequent tick believe the rotation changed and spams rotation packets.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerRotationMixin {

    @WrapOperation(
        method = "sendPosition",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F")
    )
    private float sagefang$spoofYaw(LocalPlayer self, Operation<Float> original) {
        return RotationManager.isTransmitting() ? RotationManager.yaw() : original.call(self);
    }

    @WrapOperation(
        method = "sendPosition",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F")
    )
    private float sagefang$spoofPitch(LocalPlayer self, Operation<Float> original) {
        return RotationManager.isTransmitting() ? RotationManager.pitch() : original.call(self);
    }
}
