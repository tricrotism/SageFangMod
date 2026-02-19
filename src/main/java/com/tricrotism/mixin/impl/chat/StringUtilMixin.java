package com.tricrotism.mixin.impl.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StringUtil.class)
public class StringUtilMixin {

    @WrapOperation(method = "trimChatMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;truncateStringIfNecessary(Ljava/lang/String;IZ)Ljava/lang/String;"))
    private static String removeTruncationLimitIfCommand(String text, int maxLength, boolean addEllipsis, Operation<String> original) {
        return text;
    }

}
