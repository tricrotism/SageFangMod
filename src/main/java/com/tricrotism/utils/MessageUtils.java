package com.tricrotism.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.Component;

public class MessageUtils {

    public static void sendMessage(Minecraft mc, String message) {
        sendMessage(mc, Component.literal(message));
    }

    public static void sendMessage(Minecraft mc, MutableComponent message) {
        MutableComponent component = Component.empty()
                .append(Component.literal("SFM").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
                .append(message);

        mc.gui.getChat().addMessage(component);
    }

}
