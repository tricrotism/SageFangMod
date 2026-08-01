package com.tricrotism.mixin.impl.misc;

import com.tricrotism.api.duck.IVec3;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Makes {@code Vec3}'s final coordinate fields writable through {@link IVec3}, so
 * movement/position vectors can be rewritten in place.
 */
@Mixin(Vec3.class)
public abstract class Vec3Mixin implements IVec3 {

    @Mutable @Shadow public double x;
    @Mutable @Shadow public double y;
    @Mutable @Shadow public double z;

    @Override
    public void sagefang$set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void sagefang$setX(double x) {
        this.x = x;
    }

    @Override
    public void sagefang$setY(double y) {
        this.y = y;
    }

    @Override
    public void sagefang$setZ(double z) {
        this.z = z;
    }
}
