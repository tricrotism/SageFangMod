package com.tricrotism.mixin.impl.chat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tricrotism.modules.chat.ChatStacker;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * Lets {@link ChatStacker} swap each new message before vanilla logs, splits and stores it.
 * The hook sits on the constructor call in {@code addMessage}, not in
 * {@code addMessageToDisplayQueue}, so {@code refreshTrimmedMessages} re-adding lines on a rescale
 * never reaches the stacker.
 */
@Mixin(ChatComponent.class)
public class ChatStackerMixin {

    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private Predicate<GuiMessage> visibleMessageFilter;

    @ModifyExpressionValue(method = "addMessage", at = @At(value = "NEW",
        target = "(ILnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)Lnet/minecraft/client/multiplayer/chat/GuiMessage;"))
    private GuiMessage sagefang$stack(GuiMessage message) {
        if (!ChatStacker.instance.isActive()) return message;
        return ChatStacker.instance.stack(message, allMessages, trimmedMessages, visibleMessageFilter);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void sagefang$clearStacks(boolean history, CallbackInfo ci) {
        if (ChatStacker.instance.isActive()) ChatStacker.instance.reset();
    }
}
