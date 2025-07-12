package com.tricrotism.mixin.events;

import com.tricrotism.Main;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.game.GameQuitEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class EventsClientPacketListenerMixin extends ClientCommonPacketListenerImpl {
    @Shadow
    private ClientLevel level;

//    @Shadow
//    public abstract void sendChatMessage(String content);

    @Unique
    private boolean ignoreChatMessage;

    @Unique
    private boolean worldNotNull;

    protected EventsClientPacketListenerMixin(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void onGameJoinHead(ClientboundLoginPacket packet, CallbackInfo info) {
        worldNotNull = level != null;
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onGameJoinTail(ClientboundLoginPacket packet, CallbackInfo info) {
        if (worldNotNull) {
            Main.EVENT_BUS.post(GameQuitEvent.get());
        }

        Main.EVENT_BUS.post(GameJoinedEvent.get());
    }

//    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
//    private void onSendChat(String message, CallbackInfo ci) {
//        if (ignoreChatMessage) return;

//        if (!message.startsWith(Config.get().prefix.get()) && !(BaritoneUtils.IS_AVAILABLE && message.startsWith(BaritoneUtils.getPrefix()))) {
//            SendMessageEvent event = MeteorClient.EVENT_BUS.post(SendMessageEvent.get(message));
//
//            if (!event.isCancelled()) {
//                ignoreChatMessage = true;
//                sendChatMessage(event.message);
//                ignoreChatMessage = false;
//            }
//            ci.cancel();
//            return;
//        }
//
//        if (message.startsWith(Config.get().prefix.get())) {
//            try {
//                Commands.dispatch(message.substring(Config.get().prefix.get().length()));
//            } catch (CommandSyntaxException e) {
//                ChatUtils.error(e.getMessage());
//            }
//
//            client.inGameHud.getChatHud().addToMessageHistory(message);
//            ci.cancel();
//        }
//    }
}
