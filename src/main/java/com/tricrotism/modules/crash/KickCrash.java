package com.tricrotism.modules.crash;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.mixin.accessors.ConnectionAccessor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.netty.channel.Channel;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.ContainerInput;

/**
 * Self-kick / disconnect exploit ported from YungLightUI's DisconnectAction.
 * Forces the server to kick you on demand (panic disconnect / combat log) by
 * surrounding a malformed "kick" packet with a burst of lag packets.
 * <p>
 * Sequence per execution: lag burst → kick packet → lag burst, all written
 * straight to the Netty channel so SageFang's own UI-packet suppression
 * ({@code ConnectionMixin}) doesn't drop the container-click spam.
 * <p>
 * Kick methods:
 * <ul>
 *   <li><b>Invalid Slot</b>: {@link ServerboundSetCarriedItemPacket} with slot -1</li>
 *   <li><b>Invalid View Distance</b>: {@link ServerboundClientInformationPacket} with view distance -2</li>
 * </ul>
 * The interact-based vectors (attack-self, boat/entity NBT) and the macro-bound
 * modes (kick-dupe, auto-disconnect) from the original are intentionally omitted.
 */
public class KickCrash extends Module {

    private static final String[] KICK_LABELS = {"Invalid Slot", "Invalid View Distance"};
    public static final KickCrash instance = new KickCrash();

    private final Settings.Mode kickMethod =
        mode("Kick Method", "kickMethod", "Which kick technique", 0, KICK_LABELS);
    private final Settings.Int packetCount =
        integer("Lag Packets", "packetCount", "Packets in the burst", 200, 1, 1000);

    private static final int KICK_HELD_SLOT = 0;
    private static final int KICK_STATUS = 1;

    public KickCrash() {
        super("kickcrash", "Kick Crash", "Force the server to kick you on demand (panic disconnect / combat log).", Category.COMBAT);
    }

    private boolean canSend() {
        return mc.player != null && mc.getConnection() != null;
    }

    private Channel channel() {
        if (mc.getConnection() == null) return null;
        Connection connection = mc.getConnection().getConnection();
        Channel channel = ((ConnectionAccessor) connection).sagefang$getChannel();
        return channel != null && channel.isOpen() ? channel : null;
    }

    private Packet<?> buildKickPacket() {
        return switch (kickMethod.get()) {
            case KICK_STATUS -> new ServerboundClientInformationPacket(new ClientInformation(
                "en_us", -2, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, true, ParticleStatus.ALL));
            default -> new ServerboundSetCarriedItemPacket(-1);
        };
    }

    private void doKick() {
        if (!canSend()) return;
        Channel channel = channel();
        if (channel == null) return;

        ServerboundContainerClickPacket lag = new ServerboundContainerClickPacket(
            mc.player.containerMenu.containerId, 0, (short) 0, (byte) 0,
            ContainerInput.PICKUP, new Int2ObjectArrayMap<>(), HashedStack.EMPTY);

        int count = packetCount.get();
        for (int i = 0; i < count; i++) {
            channel.write(lag);
        }
        channel.write(buildKickPacket());
        for (int i = 0; i < count; i++) {
            channel.write(lag);
        }
        channel.flush();
    }

    private void doDisconnect() {
        if (!canSend()) return;
        mc.getConnection().getConnection().disconnect(Component.literal("Disconnected by SageFang"));
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

            if (ImGui.checkbox("Enabled##kcEnabled", isActive())) {
                toggle();
            }
            ImGui.separator();

            kickMethod.render();

            int[] countArr = {packetCount.get()};
            if (ImGui.sliderInt("Lag Packets##kcCount", countArr, 1, 1000)) {
                packetCount.set(countArr[0]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Container-click packets sent before and after the kick packet");
            }

            ImGui.separator();

            if (ImGui.button("Kick##kcKick")) {
                doKick();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Lag burst + malformed kick packet to force a server disconnect");
            }

            if (ImGui.button("Disconnect##kcDisconnect")) {
                doDisconnect();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Cleanly close the connection");
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in KickCrash menu", e);
        }
    }
}
