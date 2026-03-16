package com.tricrotism.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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

    /**
     * Send a chat message or command to the server as the player.
     * Messages starting with '/' are dispatched as commands (without the slash).
     */
    public static void sendAsPlayer(String msg) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        if (msg.startsWith("/")) {
            conn.sendCommand(msg.substring(1));
        } else {
            conn.sendChat(msg);
        }
    }
}
