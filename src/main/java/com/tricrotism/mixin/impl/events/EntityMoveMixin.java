package com.tricrotism.mixin.impl.events;

import com.tricrotism.SageFang;
import com.tricrotism.events.world.EntityMoveEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Posts {@link EntityMoveEvent} at the head of {@code Entity.move} so modules can
 * rewrite an entity's movement delta in place before vanilla applies it.
 */
@Mixin(Entity.class)
public class EntityMoveMixin {

    @Inject(method = "move", at = @At("HEAD"))
    private void sagefang$onMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
        // The integrated server moves its own entities on its own thread, and the event is a shared singleton.
        if (!((Entity) (Object) this).level().isClientSide()) return;
        try {
            SageFang.EVENT_BUS.post(EntityMoveEvent.get((Entity) (Object) this, delta));
        } finally {
            EntityMoveEvent.release();
        }
    }
}
