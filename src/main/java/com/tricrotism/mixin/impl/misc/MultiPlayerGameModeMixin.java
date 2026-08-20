package com.tricrotism.mixin.impl.misc;

import com.tricrotism.modules.world.FastMine;
import com.tricrotism.modules.world.SingleMine;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While {@link SingleMine} runs in manual-mine mode it is the miner, so vanilla's
 * own block breaking must stand down. Otherwise, holding left-click would fire
 * both vanilla's dig packets and the module's, defeating its packet settings.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
        at = @At("HEAD"), cancellable = true)
    private void sagefang$blockVanillaStart(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (sagefang$manualSuppress()) cir.setReturnValue(false);
    }

    @Inject(method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
        at = @At("HEAD"), cancellable = true)
    private void sagefang$blockVanillaContinue(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (sagefang$manualSuppress()) cir.setReturnValue(false);
    }

    private static boolean sagefang$manualSuppress() {
        return (SingleMine.instance.isActive() && SingleMine.instance.isManualMode())
            || (FastMine.instance.isActive() && FastMine.instance.isManualMode());
    }
}
