package com.tricrotism.modules.misc;

import com.tricrotism.SageFang;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.mixin.accessors.ConnectionAccessor;
import com.tricrotism.utils.SFLog;
import io.netty.channel.Channel;

/**
 * Stops all packets from being sent or received, simulating a cut connection
 * (like pulling the ethernet cable). Blocking is enforced in {@code ConnectionMixin}
 * (outbound in {@code send}, inbound in {@code channelRead0}). With "Close Channel"
 * on, activation also closes the underlying netty channel, forcing a real
 * disconnect. Ported from the Meteor addon's connection-cut.
 */
public final class ConnectionCut extends Module {

    public static final ConnectionCut instance = new ConnectionCut();

    private final Settings.Bool closeChannel =
        bool("Close Channel", "closechannel",
            "Close the TCP channel on activate, forcing a real disconnect; off means packets are only dropped", false);

    private ConnectionCut() {
        super("connectioncut", "Connection Cut", "Block all inbound/outbound packets (simulate a dropped connection).", Category.NETWORK);
    }

    @Override
    public void onActivate() {
        if (!closeChannel.get()) return;
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

}
