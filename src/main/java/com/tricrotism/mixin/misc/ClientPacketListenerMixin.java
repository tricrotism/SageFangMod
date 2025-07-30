package com.tricrotism.mixin.misc;

import com.tricrotism.utils.MessageUtils;
import com.tricrotism.utils.NumberUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    public void handleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        var change = packet.change();
        if (checkNaN("x","location", change.position().x(), "ClientboundPlayerPositionPacket", ci)) return;
        if (checkNaN("y","location", change.position().y(), "ClientboundPlayerPositionPacket", ci)) return;
        if (checkNaN("z","location", change.position().z(), "ClientboundPlayerPositionPacket", ci)) return;
        if (checkNaN("xRot", "rotation", change.xRot(), "ClientboundPlayerPositionPacket", ci)) return;
        if (checkNaN("yRot", "rotation", change.yRot(), "ClientboundPlayerPositionPacket", ci)) return;
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    public void handleParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (packet.getCount() > 100) {
            MessageUtils.sendMessage(Minecraft.getInstance(),
                    String.format("You have received %s particles cancelling!", NumberUtils.format(packet.getCount())));
            ci.cancel();
        }
    }

    @Unique
    private boolean checkNaN(String axis, String name, double value, String packet, CallbackInfo ci) {
        if (Double.isNaN(value)) {
            MessageUtils.sendMessage(Minecraft.getInstance(),
                    String.format("You have received an invalid %s %s \"%s\" from %s!", axis, name, value, packet));
            ci.cancel();
            return true;
        }
        return false;
    }

    @Unique
    private boolean checkNaN(String axis, String name, float value, String packet, CallbackInfo ci) {
        if (Float.isNaN(value)) {
            MessageUtils.sendMessage(Minecraft.getInstance(),
                    String.format("You have received an invalid %s %s \"%f\" from %s!", axis, name, value, packet));
            ci.cancel();
            return true;
        }
        return false;
    }


}
