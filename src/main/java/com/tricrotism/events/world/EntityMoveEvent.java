package com.tricrotism.events.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Fired at the head of {@code Entity.move(MoverType, Vec3)} for every moving
 * entity. Handlers may rewrite {@link #movement} in place (via
 * {@link com.tricrotism.api.duck.IVec3}) to change how the entity moves this tick.
 * That is how boat-flight style modules steer their vehicle.
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

    /**
     * Drops the pooled references once the post has returned.
     * <p>
     * Without this the singleton keeps the last entity that moved, and an entity keeps its level, so
     * disconnecting pins the whole dead {@code ClientLevel} and everything in it for as long as the
     * client sits on the menu. Handlers rewrite the delta in place rather than replacing it, so
     * clearing the field here loses nothing the caller still needs.
     */
    public static void release() {
        INSTANCE.entity = null;
        INSTANCE.movement = null;
    }
}
