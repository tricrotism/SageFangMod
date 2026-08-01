package com.tricrotism.mixin.accessors;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mutable access to {@code ServerboundMoveVehiclePacket}'s final fields so modules
 * can spoof the reported vehicle rotation and on-ground flag before send.
 * (Position is rewritten in place via {@link com.tricrotism.api.duck.IVec3}.)
 */
@Mixin(ServerboundMoveVehiclePacket.class)
public interface ServerboundMoveVehiclePacketAccessor {

    @Mutable
    @Accessor("yRot")
    void sagefang$setYRot(float yRot);

    @Mutable
    @Accessor("xRot")
    void sagefang$setXRot(float xRot);

    @Mutable
    @Accessor("onGround")
    void sagefang$setOnGround(boolean onGround);
}
