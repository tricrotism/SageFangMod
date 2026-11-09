package com.tricrotism.mixin.impl.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tricrotism.modules.render.NoTextureRotations;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Only model variant selection reads this seed: section compiling, the crack overlay and blocks
 * moved by pistons. A constant changes rendering and nothing else.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateSeedMixin {

    @ModifyReturnValue(method = "getSeed", at = @At("RETURN"))
    private long sagefang$uniformSeed(long original) {
        return NoTextureRotations.isUniform() ? 0L : original;
    }
}
