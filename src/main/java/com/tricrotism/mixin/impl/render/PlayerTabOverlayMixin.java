package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.clientdetect.ClientDetect;
import com.tricrotism.modules.clientdetect.labymod.LabyGroup;
import com.tricrotism.modules.clientdetect.labymod.LabySocial;
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
 * Appends client badge indicators to player names in the tab list for players
 * detected as LabyMod users. When the player's LabyMod rank group is known
 * (from {@code PacketUserBadge}, via {@link LabySocial#getGroup}), their colored
 * rank tag (e.g. {@code [Partner]}) is shown; otherwise a generic LabyMod marker.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @Unique private static final TextColor LABYMOD_COLOR = TextColor.fromRgb(0x00A2E8);

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void sagefang$appendClientBadge(PlayerInfo playerInfo,
                                            CallbackInfoReturnable<Component> cir) {
        var uuid = playerInfo.getProfile().id();
        if (!ClientDetect.instance.isLabyModUser(uuid)) return;

        MutableComponent result = Component.empty().append(cir.getReturnValue());
        LabyGroup group = LabySocial.instance.getGroup(uuid);
        if (group != null) {
            result.append(Component.literal(" [" + group.name() + "]").withColor(group.color()));
        } else {
            result.append(Component.literal(" ◆").withColor(LABYMOD_COLOR.getValue()));
        }
        cir.setReturnValue(result);
    }
}
