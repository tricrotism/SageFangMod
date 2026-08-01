package com.tricrotism.events.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Fired at the head of {@code Entity.move(MoverType, Vec3)} for every moving
 * entity. Handlers may rewrite {@link #movement} in place (via
 * {@link com.tricrotism.api.duck.IVec3}) to change how the entity moves this tick
 * — that is how boat-flight style modules steer their vehicle.
 *
 * <p>Singleton-pooled like the other SageFang events: never hold a reference
 * across ticks.
 */
public class EntityMoveEvent {

    private static final EntityMoveEvent INSTANCE = new EntityMoveEvent();

    public Entity entity;
    public Vec3 movement;

    public static EntityMoveEvent get(Entity entity, Vec3 movement) {
        INSTANCE.entity = entity;
        INSTANCE.movement = movement;
        return INSTANCE;
    }
}
