package com.tricrotism.mixin.impl.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tricrotism.modules.misc.AllowInvalidChars;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StringUtil.class)
public class ChatTextLimitsMixin {

    @WrapOperation(method = "trimChatMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;truncateStringIfNecessary(Ljava/lang/String;IZ)Ljava/lang/String;"))
    private static String removeTruncationLimitIfCommand(String text, int maxLength, boolean addEllipsis, Operation<String> original) {
        return text;
    }

    @Inject(method = "isAllowedChatCharacter", at = @At("HEAD"), cancellable = true)
    private static void sagefang$allowInvalidChars(int ch, CallbackInfoReturnable<Boolean> cir) {
        if (AllowInvalidChars.instance.isActive()) cir.setReturnValue(true);
    }

}
