package com.tricrotism.modules.misc;

import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.mixin.accessors.ConnectionAccessor;
import com.tricrotism.utils.SFLog;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import io.netty.channel.Channel;

/**
 * Stops all packets from being sent or received, simulating a cut connection
 * (like pulling the ethernet cable). Blocking is enforced in {@code ConnectionMixin}
 * (outbound in {@code send}, inbound in {@code channelRead0}). With "Close Channel"
 * on, activation also closes the underlying netty channel, forcing a real
 * disconnect. Ported from the Meteor addon's connection-cut.
 */
public final class ConnectionCut extends Module implements Menu {

    public static final ConnectionCut instance = new ConnectionCut();

    private boolean closeChannel;

    private ConnectionCut() {
        super("connectioncut", "Connection Cut", "Block all inbound/outbound packets (simulate a dropped connection).", "Network");
        closeChannel = Config.getBool(baseConfig + ".closechannel", false);
    }

    @Override
    public void onActivate() {
        if (!closeChannel) return;
        var listener = mc.getConnection();
        if (listener == null) return;
        try {
            Channel channel = ((ConnectionAccessor) listener.getConnection()).sagefang$getChannel();
            if (channel != null && channel.isOpen()) {
                channel.close();
                SFLog.log(title, "Connection channel closed.");
            }
        } catch (Exception e) {
            SageFang.LOGGER.warn("[ConnectionCut] failed to close channel", e);
        }
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##connectionCutEnabled", isActive())) toggle();
        if (ImGui.checkbox("Close Channel##connectionCutClose", closeChannel)) {
            closeChannel = !closeChannel;
            Config.setProperty(baseConfig + ".closechannel", String.valueOf(closeChannel));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Actually close the TCP channel on activate (forces a disconnect).\nOff: only silently drop packets.");
        }

        ImGui.end();
    }
}
