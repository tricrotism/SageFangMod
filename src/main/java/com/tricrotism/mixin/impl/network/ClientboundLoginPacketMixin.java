package com.tricrotism.mixin.impl.network;

import com.tricrotism.modules.login.GameJoinSpoof;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides client-side accessors of the {@link ClientboundLoginPacket} (join
 * game) when {@link GameJoinSpoof} is active, so the client behaves as if the
 * server sent different join flags. Int fields use -1 to mean "leave as sent".
 */
@Mixin(ClientboundLoginPacket.class)
public class ClientboundLoginPacketMixin {

    @Inject(method = "hardcore", at = @At("RETURN"), cancellable = true)
    private void sagefang$hardcore(CallbackInfoReturnable<Boolean> cir) {
        if (GameJoinSpoof.instance.isActive()) cir.setReturnValue(GameJoinSpoof.instance.getHardcore());
    }

    @Inject(method = "reducedDebugInfo", at = @At("RETURN"), cancellable = true)
    private void sagefang$reducedDebugInfo(CallbackInfoReturnable<Boolean> cir) {
        if (GameJoinSpoof.instance.isActive()) cir.setReturnValue(GameJoinSpoof.instance.getReducedDebugInfo());
    }

    @Inject(method = "showDeathScreen", at = @At("RETURN"), cancellable = true)
    private void sagefang$showDeathScreen(CallbackInfoReturnable<Boolean> cir) {
        if (GameJoinSpoof.instance.isActive()) cir.setReturnValue(GameJoinSpoof.instance.getShowDeathScreen());
    }

    @Inject(method = "doLimitedCrafting", at = @At("RETURN"), cancellable = true)
    private void sagefang$doLimitedCrafting(CallbackInfoReturnable<Boolean> cir) {
        if (GameJoinSpoof.instance.isActive()) cir.setReturnValue(GameJoinSpoof.instance.getLimitedCrafting());
    }

    @Inject(method = "enforcesSecureChat", at = @At("RETURN"), cancellable = true)
    private void sagefang$enforcesSecureChat(CallbackInfoReturnable<Boolean> cir) {
        if (GameJoinSpoof.instance.isActive()) cir.setReturnValue(GameJoinSpoof.instance.getSecureChat());
    }

    @Inject(method = "maxPlayers", at = @At("RETURN"), cancellable = true)
    private void sagefang$maxPlayers(CallbackInfoReturnable<Integer> cir) {
        if (GameJoinSpoof.instance.isActive()) {
            int v = GameJoinSpoof.instance.getMaxPlayers();
            if (v != -1) cir.setReturnValue(v);
        }
    }

    @Inject(method = "chunkRadius", at = @At("RETURN"), cancellable = true)
    private void sagefang$chunkRadius(CallbackInfoReturnable<Integer> cir) {
        if (GameJoinSpoof.instance.isActive()) {
            int v = GameJoinSpoof.instance.getViewDistance();
            if (v != -1) cir.setReturnValue(v);
        }
    }

    @Inject(method = "simulationDistance", at = @At("RETURN"), cancellable = true)
    private void sagefang$simulationDistance(CallbackInfoReturnable<Integer> cir) {
        if (GameJoinSpoof.instance.isActive()) {
            int v = GameJoinSpoof.instance.getSimDistance();
            if (v != -1) cir.setReturnValue(v);
        }
    }
}
