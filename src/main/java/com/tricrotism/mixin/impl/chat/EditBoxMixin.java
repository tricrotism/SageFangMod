package com.tricrotism.mixin.impl.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tricrotism.SageFang;
import com.tricrotism.api.duck.IEditBox;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EditBox.class)
public class EditBoxMixin implements IEditBox {

    @Shadow private int cursorPos;
    @Shadow private int highlightPos;

    @Unique
    private boolean isEditBox = false;

    @WrapOperation(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;insertText(Ljava/lang/String;)V"))
    private void preventTruncation(EditBox instance, String text, Operation<Void> original) {
        instance.setMaxLength(SageFang.MAX_LENGTH_MINUS_ONE);
        original.call(instance, text);
    }

    @Override
    public void sageFang$setAsChatBox() {
        this.isEditBox = true;
    }
}
