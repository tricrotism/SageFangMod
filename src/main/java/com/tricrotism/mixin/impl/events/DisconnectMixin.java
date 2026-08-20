package com.tricrotism.mixin.impl.events;

import com.tricrotism.SageFang;
import com.tricrotism.events.game.GameQuitEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A server-initiated disconnect reaches {@code onDisconnect} on the netty thread, but
 * {@code GameQuitEvent} handlers tear down modules, discarding entities, flushing packet
 * queues and restoring the camera perspective, so the event is hopped onto the client thread.
 * Queued at HEAD, it runs before the disconnect task that calls {@code dropAllTasks()}.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class DisconnectMixin {

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void onDisconnect(DisconnectionDetails details, CallbackInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            SageFang.EVENT_BUS.post(GameQuitEvent.get());
        } else {
            mc.execute(() -> SageFang.EVENT_BUS.post(GameQuitEvent.get()));
        }
    }
}
