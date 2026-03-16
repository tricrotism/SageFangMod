package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.clientdetect.ClientDetect;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Appends client badge indicators to player names in the tab list
 * for players detected as LabyMod users.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @Unique private static final TextColor LABYMOD_COLOR = TextColor.fromRgb(0x00A2E8);

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void sagefang$appendClientBadge(PlayerInfo playerInfo,
                                            CallbackInfoReturnable<Component> cir) {
        var uuid = playerInfo.getProfile().id();
        var detect = ClientDetect.instance;
        boolean isLaby = detect.isLabyModUser(uuid);

        if (isLaby) {
            MutableComponent result = Component.empty().append(cir.getReturnValue());
            result.append(Component.literal(" ◆").withColor(LABYMOD_COLOR.getValue()));
            cir.setReturnValue(result);
        }
    }
}
