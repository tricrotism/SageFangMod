package com.tricrotism.mixin.impl.inventory;

import com.tricrotism.config.SageFangConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(AbstractContainerScreen.class)
public class HandledScreenMixin {

    @Unique
    private static final float SCALE = 0.75f;
    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void onRenderSlot(GuiGraphics context, Slot slot, CallbackInfo ci) {
        if (!SageFangConfig.isShouldShowSlotNumbers()) return;

        int x = slot.x;
        int y = slot.y;
        int index = slot.index;
        context.pose().pushMatrix();
        context.pose().translate(0, 0);
        context.pose().scale(SCALE, SCALE);
        context.drawString(
                Minecraft.getInstance().font,
                String.valueOf(index),
                (int) ((x + 1) * (1 / SCALE)),
                (int) ((y + 1) * (1 / SCALE)),
                Color.WHITE.getRGB(),
                true
        );
        context.pose().popMatrix();
    }
}
