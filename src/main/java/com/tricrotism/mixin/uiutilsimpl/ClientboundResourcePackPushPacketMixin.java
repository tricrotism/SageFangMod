package com.tricrotism.mixin.uiutilsimpl;

import com.tricrotism.Main;
import com.tricrotism.utils.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientboundResourcePackPushPacketMixin {

    @Unique
    private static boolean sentMessage = false;

    @Shadow
    public abstract void send(Packet<?> packet);

    @Inject(at = @At("HEAD"), method = "handleResourcePackPush", cancellable = true)
    public void handleResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        Optional<Component> prompt = (packet.prompt().isEmpty() ? Optional.empty() : packet.prompt());
        String promptMessage = prompt.map(Component::getString).orElse("No message");

        if (!sentMessage) {
            MutableComponent msg = Component.empty()
                    .append(Component.literal("Resource Pack Details | Message: \"").withStyle(ChatFormatting.WHITE))
                    .append(promptMessage)
                    .append(Component.literal("\", URL: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(packet.url().isEmpty() ? "<no url>" : packet.url())
                            .withStyle(ChatFormatting.AQUA).withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, packet.url())))
                            .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open link")))))
                    .append(Component.literal(", Hash: " + packet.hash()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(", Required?: " + packet.required()).withStyle(ChatFormatting.WHITE)
                    );
            sentMessage = true;

            MessageUtils.sendMessage(Minecraft.getInstance(), msg);
        }

        if (Main.getConfig().bypassResourcePack && (!packet.isSkippable() && Main.getConfig().resourcePackForceDeny)) {
            this.send(new ServerboundResourcePackPacket(Minecraft.getInstance().getUser().getProfileId(), ServerboundResourcePackPacket.Action.ACCEPTED));
            this.send(new ServerboundResourcePackPacket(Minecraft.getInstance().getUser().getProfileId(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            ci.cancel();
        }

        sentMessage = false;
    }
}
