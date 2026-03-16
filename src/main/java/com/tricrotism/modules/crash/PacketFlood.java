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
import imgui.type.ImInt;
import io.avaje.config.Config;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Generic packet flooding module — sends configurable packet types at high
 * throughput to trigger or test LPX FLOOD_B rate-limit detections.
 */
public class PacketFlood extends Module implements Menu {

    public static final PacketFlood instance = new PacketFlood();

    // ── Flood type labels & LPX limit descriptions ─────────────────

    private static final String[] TYPE_LABELS = {
        "Animation", "Use Item", "Block Place", "Interact",
        "Position", "Digging", "Tab Complete", "Sign"
    };

    private static final String[] LPX_LIMITS = {
        "LPX limit: 50 / 500ms",
        "LPX limit: 60 / 1000ms",
        "LPX limit: 14 / 100ms",
        "LPX limit: (entity interact proxy)",
        "LPX limit: 40 / 100ms",
        "LPX limit: 40 / 500ms",
        "LPX limit: 40 / 1000ms",
        "LPX limit: 2 / 300ms"
    };

    // ── Cached static packets ──────────────────────────────────────

    private static final ServerboundSwingPacket SWING =
        new ServerboundSwingPacket(InteractionHand.MAIN_HAND);

    private static final ServerboundUseItemPacket USE_ITEM =
        new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, 0f, 0f);

    private static final ServerboundUseItemOnPacket BLOCK_PLACE =
        new ServerboundUseItemOnPacket(
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false),
            0
        );

    private static final ServerboundPlayerActionPacket DIG =
        new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            BlockPos.ZERO,
            Direction.UP
        );

    private static final ServerboundChatCommandPacket TAB_COMPLETE =
        new ServerboundChatCommandPacket("/");

    private static final ServerboundSignUpdatePacket SIGN =
        new ServerboundSignUpdatePacket(BlockPos.ZERO, true, "", "", "", "");

    // ── Constructor ────────────────────────────────────────────────

    public PacketFlood() {
        super("packetflood", "Packet Flood", "Flood configurable packet types to trigger LPX FLOOD_B rate limits.", "Combat");
    }

    // ── Config keys ────────────────────────────────────────────────

    private int type() {
        return Config.getInt(baseConfig + ".type", 0);
    }

    private void setType(int v) {
        Config.setProperty(baseConfig + ".type", String.valueOf(v));
    }

    private int speed() {
        return Config.getInt(baseConfig + ".speed", 200);
    }

    private void setSpeed(int v) {
        Config.setProperty(baseConfig + ".speed", String.valueOf(v));
    }

    private boolean useRawChannel() {
        return Config.getBool(baseConfig + ".useRawChannel", true);
    }

    private void setUseRawChannel(boolean v) {
        Config.setProperty(baseConfig + ".useRawChannel", String.valueOf(v));
    }

    // ── Packet resolution ──────────────────────────────────────────

    /**
     * Returns the packet to send for the current flood type.
     * Most types use a cached static instance; Position is rebuilt
     * each tick to reflect the player's current coordinates.
     */
    private Packet<?> resolvePacket(Minecraft mc) {
        return switch (type()) {
            case 0 -> SWING;             // Animation
            case 1 -> USE_ITEM;          // Use Item
            case 2 -> BLOCK_PLACE;       // Block Place
            case 3 -> SWING;             // Interact (swing as proxy)
            case 4 -> new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYRot(), mc.player.getXRot(),
                true, false
            );
            case 5 -> DIG;              // Digging
            case 6 -> TAB_COMPLETE;     // Tab Complete
            case 7 -> SIGN;             // Sign
            default -> SWING;
        };
    }

    // ── Events ─────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        Packet<?> packet = resolvePacket(mc);
        int count = speed();

        if (useRawChannel()) {
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

    // ── ImGui ──────────────────────────────────────────────────────

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!isVisible()) return;

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            ImGui.setNextWindowBgAlpha(0.45f);
            ImGui.begin(title, flags);

            if (ImGui.checkbox("Enabled##pfEnabled", isActive())) {
                toggle();
            }
            ImGui.separator();

            // Type combo
            ImInt typeInt = new ImInt(type());
            if (ImGui.combo("Flood Type##pfType", typeInt, TYPE_LABELS)) {
                setType(typeInt.get());
            }

            // Speed slider
            int[] speedArr = {speed()};
            if (ImGui.sliderInt("Packets/tick##pfSpeed", speedArr, 1, 5000)) {
                setSpeed(speedArr[0]);
            }

            // Raw channel toggle
            boolean raw = useRawChannel();
            if (ImGui.checkbox("Use Raw Channel##pfRaw", raw)) {
                setUseRawChannel(!raw);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Bypass normal send pipeline via Netty channel");
            }

            ImGui.separator();

            // LPX limit info for selected type
            int currentType = type();
            if (currentType >= 0 && currentType < LPX_LIMITS.length) {
                ImGui.textDisabled(LPX_LIMITS[currentType]);
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in PacketFlood menu", e);
        }
    }
}
