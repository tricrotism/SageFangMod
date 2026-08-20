package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.misc.RealPlayerNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When {@link RealPlayerNames} is active, replaces a player's display name with
 * their raw account name, so nicked / team-decorated names resolve to the real
 * one, most visibly on the name tag above the head.
 */
@Mixin(Player.class)
public class PlayerDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void sagefang$realName(CallbackInfoReturnable<Component> cir) {
        if (!RealPlayerNames.instance.isActive()) return;
        Player self = (Player) (Object) this;
        cir.setReturnValue(Component.literal(self.getGameProfile().name()));
    }
}
