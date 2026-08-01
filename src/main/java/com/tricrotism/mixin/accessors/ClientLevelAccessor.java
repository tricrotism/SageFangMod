package com.tricrotism.mixin.accessors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code ClientLevel.getBlockStatePredictionHandler()} (package-private)
 * so modules can read/advance the vanilla block-action prediction sequence — used
 * by {@code SingleMine}'s Vanilla/Current sequence modes.
 */
@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {

    @Invoker("getBlockStatePredictionHandler")
    BlockStatePredictionHandler sagefang$getBlockStatePredictionHandler();
}
