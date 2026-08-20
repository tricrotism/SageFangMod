package com.tricrotism.mixin.impl.screen;

import com.tricrotism.config.SageFangConfig;
import com.tricrotism.modules.misc.LaggySign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes the sign screen right away when sign editing is disabled. When
 * {@link LaggySign} is active, it first fills the sign with lag characters and
 * sends the update. {@code init} lives on the abstract base in 26.2 (neither
 * {@code SignEditScreen} nor {@code HangingSignEditScreen} overrides it).
 */
@Mixin(AbstractSignEditScreen.class)
public class AbstractSignEditScreenMixin {

    @Shadow protected SignBlockEntity sign;

    @Inject(method = "init", at = @At("TAIL"))
    private void sagefang$onInit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!SageFangConfig.isShouldEditSign()) {
            mc.gui.setScreen(null);
            return;
        }
        if (!LaggySign.instance.isActive()) return;
        if (mc.player == null || mc.getConnection() == null || sign == null) return;

        String[] lines = LaggySign.instance.randomLines();
        mc.getConnection().send(new ServerboundSignUpdatePacket(
            sign.getBlockPos(), true, lines[0], lines[1], lines[2], lines[3]));
        mc.gui.setScreen(null);
    }
}
