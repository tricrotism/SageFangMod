package com.tricrotism.mixin.misc;

import com.tricrotism.Main;
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
        if (!Main.getConfig().shouldShowSlotNumbers) return;

        int x = slot.x;
        int y = slot.y;
        int index = slot.index;
        context.pose().pushPose();
        context.pose().translate(0, 0, 399);
        context.pose().scale(SCALE, SCALE, SCALE);
        context.drawString(
                Minecraft.getInstance().font,
                String.valueOf(index),
                (int) ((x + 1) * (1 / SCALE)),
                (int) ((y + 1) * (1 / SCALE)),
                Color.WHITE.getRGB(),
                true
        );
        context.pose().popPose();
    }
}