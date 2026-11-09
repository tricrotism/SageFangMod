package com.tricrotism.mixin.impl.chat;

import com.tricrotism.modules.chat.SageChat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds every message the client accepts to {@link SageChat} and gives it a slot in the gui
 * extraction pass.
 * <p>
 * The three add methods are the only ways a message reaches the chat, so tapping them catches
 * server chat, client-side notices and signed player messages alike without touching the network
 * layer. Cancelling the extraction is what "replace vanilla chat" means: off, both draw and the two
 * can be compared side by side; on, these windows are the chat.
 */
@Mixin(ChatComponent.class)
public class ChatTapMixin {

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"))
    private void sagefang$clientSystemMessage(Component message, CallbackInfo ci) {
        SageChat.instance.onMessage(message);
    }

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void sagefang$serverSystemMessage(Component message, CallbackInfo ci) {
        SageChat.instance.onMessage(message);
    }

    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void sagefang$playerMessage(Component message, MessageSignature signature, GuiMessageTag tag,
                                        CallbackInfo ci) {
        SageChat.instance.onMessage(message);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void sagefang$extractRenderState(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
                                             int lineOffset, ChatComponent.DisplayMode mode, boolean focused,
                                             CallbackInfo ci) {
        // lineOffset is vanilla's own scroll position, still maintained by scrollChat while our
        // windows draw, so scrolling works without a second input hook or a second copy of the state.
        SageChat.instance.render(graphics, font, ((ChatComponent) (Object) this).isChatFocused(), lineOffset);

        if (SageChat.instance.replacesVanilla()) ci.cancel();
    }
}
