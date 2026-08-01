package com.tricrotism.mixin.impl.screen;

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
 * When {@link LaggySign} is active, immediately fills the opened sign with lag
 * characters, sends the update, and closes the screen.
 */
@Mixin(AbstractSignEditScreen.class)
public class AbstractSignEditScreenMixin {

    @Shadow protected SignBlockEntity sign;

    @Inject(method = "init", at = @At("TAIL"))
    private void sagefang$laggySign(CallbackInfo ci) {
        if (!LaggySign.instance.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || sign == null) return;

        String[] lines = LaggySign.instance.randomLines();
        mc.getConnection().send(new ServerboundSignUpdatePacket(
            sign.getBlockPos(), true, lines[0], lines[1], lines[2], lines[3]));
        mc.setScreen(null);
    }
}
