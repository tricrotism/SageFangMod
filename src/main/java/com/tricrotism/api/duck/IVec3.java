package com.tricrotism.api.duck;

/**
 * Lets modules mutate an otherwise-immutable {@code Vec3} in place — used where
 * vanilla hands out a movement/position vector we need to rewrite without
 * reallocating (entity movement deltas, vehicle move packets).
 */
public interface IVec3 {

    void sagefang$set(double x, double y, double z);

    void sagefang$setX(double x);

    void sagefang$setY(double y);

    void sagefang$setZ(double z);
}
