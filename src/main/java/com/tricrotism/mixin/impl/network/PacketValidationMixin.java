package com.tricrotism.mixin.impl.network;

import com.tricrotism.utils.MessageUtils;
import com.tricrotism.utils.NumberUtils;
import com.tricrotism.utils.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class PacketValidationMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    public void handleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        var change = packet.change();
        if (PacketUtils.checkVec3("location", change.position(), "PlayerPositionPacket", ci)) return;
        if (PacketUtils.checkBad("xRot", "rotation", change.xRot(), "PlayerPositionPacket", ci)) return;
        if (PacketUtils.checkBad("yRot", "rotation", change.yRot(), "PlayerPositionPacket", ci)) return;
        if (PacketUtils.checkVec3("deltaMovement", change.deltaMovement(), "PlayerPositionPacket", ci)) {
        }
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    public void handleParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (packet.getCount() > 500) {
            MessageUtils.sendMessage(Minecraft.getInstance(),
                String.format("You have received %s particles cancelling!", NumberUtils.format(packet.getCount())));
            ci.cancel();
            return;
        }
        if (PacketUtils.checkBad("x", "position", packet.getX(), "LevelParticlesPacket", ci)) return;
        if (PacketUtils.checkBad("y", "position", packet.getY(), "LevelParticlesPacket", ci)) return;
        if (PacketUtils.checkBad("z", "position", packet.getZ(), "LevelParticlesPacket", ci)) return;
        if (PacketUtils.checkBad("xDist", "distribution", packet.getXDist(), "LevelParticlesPacket", ci)) return;
        if (PacketUtils.checkBad("yDist", "distribution", packet.getYDist(), "LevelParticlesPacket", ci)) return;
        if (PacketUtils.checkBad("zDist", "distribution", packet.getZDist(), "LevelParticlesPacket", ci)) return;
        if (PacketUtils.checkBad("maxSpeed", "speed", packet.getMaxSpeed(), "LevelParticlesPacket", ci)) {
        }
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    public void handleExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        if (PacketUtils.checkVec3("center", packet.center(), "ExplodePacket", ci)) return;
        if (PacketUtils.checkBad("radius", "radius", packet.radius(), "ExplodePacket", ci)) return;
        if (packet.playerKnockback().isPresent()) {
            if (PacketUtils.checkVec3("playerKnockback", packet.playerKnockback().get(), "ExplodePacket", ci)) {
            }
        }
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true)
    public void handleAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (PacketUtils.checkBad("x", "position", packet.getX(), "AddEntityPacket", ci)) return;
        if (PacketUtils.checkBad("y", "position", packet.getY(), "AddEntityPacket", ci)) return;
        if (PacketUtils.checkBad("z", "position", packet.getZ(), "AddEntityPacket", ci)) return;
        if (PacketUtils.checkVec3("movement", packet.getMovement(), "AddEntityPacket", ci)) {
        }
    }

    @Inject(method = "handleEntityPositionSync", at = @At("HEAD"), cancellable = true)
    public void handleEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        var values = packet.values();
        if (PacketUtils.checkVec3("position", values.position(), "EntityPositionSyncPacket", ci)) return;
        if (PacketUtils.checkVec3("deltaMovement", values.deltaMovement(), "EntityPositionSyncPacket", ci)) return;
        if (PacketUtils.checkBad("xRot", "rotation", values.xRot(), "EntityPositionSyncPacket", ci)) return;
        if (PacketUtils.checkBad("yRot", "rotation", values.yRot(), "EntityPositionSyncPacket", ci)) {
        }
    }

    @Inject(method = "handleTeleportEntity", at = @At("HEAD"), cancellable = true)
    public void handleTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        var change = packet.change();
        if (PacketUtils.checkVec3("position", change.position(), "TeleportEntityPacket", ci)) return;
        if (PacketUtils.checkVec3("deltaMovement", change.deltaMovement(), "TeleportEntityPacket", ci)) return;
        if (PacketUtils.checkBad("xRot", "rotation", change.xRot(), "TeleportEntityPacket", ci)) return;
        if (PacketUtils.checkBad("yRot", "rotation", change.yRot(), "TeleportEntityPacket", ci)) {
        }
    }

    @Inject(method = "handleSetHealth", at = @At("HEAD"), cancellable = true)
    public void handleSetHealth(ClientboundSetHealthPacket packet, CallbackInfo ci) {
        if (PacketUtils.checkBad("health", "value", packet.getHealth(), "SetHealthPacket", ci)) return;
        if (PacketUtils.checkBad("saturation", "value", packet.getSaturation(), "SetHealthPacket", ci)) {
        }
    }

    @Inject(method = "handleSetExperience", at = @At("HEAD"), cancellable = true)
    public void handleSetExperience(ClientboundSetExperiencePacket packet, CallbackInfo ci) {
        if (PacketUtils.checkBad("experienceProgress", "value", packet.getExperienceProgress(), "SetExperiencePacket", ci)) {
        }
    }

    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    public void handleSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (PacketUtils.checkBad("volume", "value", packet.getVolume(), "SoundPacket", ci)) return;
        if (PacketUtils.checkBad("pitch", "value", packet.getPitch(), "SoundPacket", ci)) {
        }
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("HEAD"), cancellable = true)
    public void handleSoundEntityEvent(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (PacketUtils.checkBad("volume", "value", packet.getVolume(), "SoundEntityPacket", ci)) return;
        if (PacketUtils.checkBad("pitch", "value", packet.getPitch(), "SoundEntityPacket", ci)) {
        }
    }

    @Inject(method = "handleInitializeBorder", at = @At("HEAD"), cancellable = true)
    public void handleInitializeBorder(ClientboundInitializeBorderPacket packet, CallbackInfo ci) {
        if (PacketUtils.checkBad("newCenterX", "center", packet.getNewCenterX(), "InitializeBorderPacket", ci)) return;
        if (PacketUtils.checkBad("newCenterZ", "center", packet.getNewCenterZ(), "InitializeBorderPacket", ci)) return;
        if (PacketUtils.checkBad("oldSize", "size", packet.getOldSize(), "InitializeBorderPacket", ci)) return;
        if (PacketUtils.checkBad("newSize", "size", packet.getNewSize(), "InitializeBorderPacket", ci)) {
        }
    }

    @Inject(method = "handleSetBorderCenter", at = @At("HEAD"), cancellable = true)
    public void handleSetBorderCenter(ClientboundSetBorderCenterPacket packet, CallbackInfo ci) {
        if (PacketUtils.checkBad("newCenterX", "center", packet.getNewCenterX(), "SetBorderCenterPacket", ci)) return;
        if (PacketUtils.checkBad("newCenterZ", "center", packet.getNewCenterZ(), "SetBorderCenterPacket", ci)) {
        }
    }
}
