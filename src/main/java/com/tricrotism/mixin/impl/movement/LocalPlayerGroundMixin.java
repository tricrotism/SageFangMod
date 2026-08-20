package com.tricrotism.mixin.impl.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tricrotism.modules.movement.NoFall;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Substitutes the transmitted ground flag inside {@code sendPosition()} without touching the
 * entity's real state, so the client keeps falling and rendering normally.
 * <p>
 * Wrapped across the whole method for the same reason as
 * {@link com.tricrotism.mixin.impl.rotation.LocalPlayerRotationMixin}: the method also decides
 * whether to send a status-only packet by comparing against {@code lastOnGround}, and updates that
 * field afterwards. Spoofing only the packet constructor would leave the comparison tracking the
 * real flag, so every tick would look like a ground transition.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerGroundMixin {

    @WrapOperation(
        method = "sendPosition",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z")
    )
    private boolean sagefang$claimGround(LocalPlayer self, Operation<Boolean> original) {
        return NoFall.isClaiming() || original.call(self);
    }
}
