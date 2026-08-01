package com.tricrotism.mixin.accessors;

import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mutable access to the {@code input} of a {@link ServerboundPlayerInputPacket}
 * so modules can rewrite the jump flag in-flight (used by {@code ElytraCast} to
 * force/suppress the jump bit around a fall-flying start).
 */
@Mixin(ServerboundPlayerInputPacket.class)
public interface ServerboundPlayerInputPacketAccessor {

    @Mutable
    @Accessor("input")
    void sagefang$setInput(Input input);
}
