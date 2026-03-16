package com.tricrotism.mixin.impl.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tricrotism.SageFang;
import com.tricrotism.api.duck.IEditBox;
import com.tricrotism.events.game.SendMessageEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (this.input != null) {
            ((IEditBox) this.input).sageFang$setAsChatBox();
        }
    }

    @Inject(method = "onEdited", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;setAllowSuggestions(Z)V", shift = At.Shift.BEFORE))
    public void bypassChatLimit(String chatText, CallbackInfo ci) {
        this.input.setMaxLength(SageFang.MAX_LENGTH_MINUS_ONE);
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (!message.startsWith("/")) {
            SendMessageEvent event = SageFang.EVENT_BUS.post(SendMessageEvent.get(message));
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @WrapOperation(method = "moveInHistory", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;setValue(Ljava/lang/String;)V"))
    private void updateCommandHist(EditBox instance, String text, Operation<Void> original) {
        instance.setMaxLength(SageFang.MAX_LENGTH_MINUS_ONE);
        original.call(instance, text);
    }

}
