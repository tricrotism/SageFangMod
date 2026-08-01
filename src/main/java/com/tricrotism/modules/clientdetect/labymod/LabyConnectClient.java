package com.tricrotism.modules.clientdetect.labymod;

import com.tricrotism.modules.clientdetect.labymod.protocol.*;
import com.tricrotism.modules.clientdetect.labymod.protocol.packets.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Low-level Netty TCP client for LabyMod's LabyConnect service
 * ({@code chat.labymod.net:30336}). Owns the connection, the per-channel
 * pipeline (read-timeout, frame splitter/prepender, inbound handler) and the
 * {@link PacketRegistry}; the protocol state machine and packet handling live
 * in {@link LabyConnectSession}.
 *
 * <p>All network work runs on a single-thread {@link NioEventLoopGroup}.
 * Connection state fields are {@code volatile} because {@link #connect()} /
 * {@link #disconnect()} and the predicates ({@link #isConnected()}) may be
 * called from the game thread while the event loop mutates them.
 */
public class LabyConnectClient {
    private static final String ADDRESS = "chat.labymod.net";
    private static final int PORT = 30336;
    /**
     * Shared logger for the whole LabyConnect stack ({@code "LabyConnect"} channel).
     */
    static final Logger LOGGER = LogManager.getLogger("LabyConnect");

    private final PacketRegistry registry;
    private volatile EventLoopGroup group;
    private volatile Channel channel;
    /**
     * The active protocol session, or {@code null} before connect / after disconnect.
     */
    @Getter private volatile LabyConnectSession session;
    private volatile boolean connected;

    /**
     * Creates the client and populates the {@link PacketRegistry} with all known ids.
     */
    public LabyConnectClient() {
        this.registry = new PacketRegistry();
        registerPackets();
    }

    /**
     * Registers every client-bound (received) and server-bound (sent) packet
     * against its numeric LabyConnect id. The id constants here match LabyMod's
     * protocol exactly; an id present on both sides (e.g. login packets) shares
     * the same number for both directions.
     */
    private void registerPackets() {
        // Client-bound
        registry.register(1, HelloPong::new);
        registry.register(7, LoginComplete::new);
        registry.register(10, EncryptionRequest::new);
        registry.register(33, UserBadge::new);
        registry.register(62, Ping::new);
        registry.register(60, Disconnect::new);
        registry.register(61, Kick::new);
        registry.register(32, AddonMessage::new);
        registry.register(4, LoginFriend::new);
        registry.register(14, PlayPlayerOnline::new);
        registry.register(17, PlayRequestAddFriendResponse::new);
        registry.register(23, PlayFriendStatus::new);
        registry.register(24, PlayFriendPlayingOn::new);
        registry.register(25, PlayTyping::new);
        registry.register(64, ServerMessage::new);
        registry.register(65, Message::new);

        // Server-bound
        registry.register(0, HelloPing::new);
        registry.register(3, LoginData::new);
        registry.register(6, LoginOptions::new);
        registry.register(9, LoginVersion::new);
        registry.register(11, EncryptionResponse::new);
        registry.register(63, Pong::new);
        registry.register(68, ServerStatusUpdate::new);
        registry.register(69, UserTracker::new);
        registry.register(16, PlayRequestAddFriend::new);
        registry.register(18, PlayRequestRemove::new);
        registry.register(19, PlayDenyFriendRequest::new);
        registry.register(20, PlayFriendRemove::new);
        registry.register(21, PlayChangeOptions::new);
    }

    /**
     * Opens the LabyConnect connection asynchronously. No-op if already
     * connected. On success a fresh {@link LabyConnectSession} is created and
     * the handshake is kicked off by sending a {@link HelloPing}; on failure
     * the event-loop group is shut down. The connect itself is non-blocking —
     * the listener fires on the event loop.
     */
    public void connect() {
        if (isConnected()) {
            return;
        }

        group = new NioEventLoopGroup(1, new DefaultThreadFactory("labyconnect"));

        new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast("timeout", new ReadTimeoutHandler(30, TimeUnit.SECONDS));
                    p.addLast("splitter", new PacketFrameDecoder());
                    p.addLast("prepender", new PacketFrameEncoder());
                    p.addLast("handler", new ClientHandler());
                }
            })
            .connect(ADDRESS, PORT)
            .addListener((ChannelFuture future) -> {
                if (future.isSuccess()) {
                    channel = future.channel();
                    session = new LabyConnectSession(this);
                    connected = true;
                    LOGGER.info("Connected to LabyConnect");
                    sendPacket(new HelloPing(System.currentTimeMillis()));
                } else {
                    LOGGER.warn("Failed to connect to LabyConnect", future.cause());
                    shutdownGroup();
                }
            });
    }

    /**
     * Closes the channel, clears the session and shuts down the event-loop
     * group. Safe to call from any thread and idempotent.
     */
    public void disconnect() {
        connected = false;
        if (channel != null) {
            channel.close();
            channel = null;
        }
        session = null;
        shutdownGroup();
    }

    /**
     * Gracefully terminates the Netty event-loop group if one is running.
     */
    private void shutdownGroup() {
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }
    }

    /**
     * Sends a packet with no post-write callback. See {@link #sendPacket(Packet, Consumer)}.
     */
    public void sendPacket(Packet packet) {
        sendPacket(packet, null);
    }

    /**
     * Serializes and writes a packet on the channel's event loop. The frame is
     * built as {@code varint(id) + packet.write(buf)} and flushed; the
     * {@link PacketFrameEncoder} adds the outer length prefix. No-op (silently
     * dropped) if not connected. Exceptions during serialization/write are
     * logged, not propagated.
     *
     * @param packet     the packet to send; its class must be registered
     * @param afterWrite optional callback run on the event loop right after the
     *                   flush is issued — used by the encryption handshake to
     *                   splice cipher handlers into the pipeline once the
     *                   {@code EncryptionResponse} is on the wire. May be {@code null}.
     */
    public void sendPacket(Packet packet, Consumer<Channel> afterWrite) {
        if (!isConnected()) return;
        channel.eventLoop().execute(() -> {
            try {
                int id = registry.getId(packet);
                ByteBuf buf = channel.alloc().buffer();
                PacketBuffer packetBuf = new PacketBuffer(buf);
                packetBuf.writeVarInt(id);
                packet.write(packetBuf);
                channel.writeAndFlush(buf).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                if (afterWrite != null) afterWrite.accept(channel);
            } catch (Exception e) {
                LOGGER.error("Error sending packet {}", packet.getClass().getSimpleName(), e);
            }
        });
    }

    /**
     * True only when the connect succeeded and the channel is still active.
     */
    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }

    /**
     * Package-private accessor for the packet id/class registry.
     */
    PacketRegistry getRegistry() {
        return registry;
    }

    /**
     * Final inbound pipeline handler. Reads the leading varint id, instantiates
     * the matching packet via the registry, decodes its body and dispatches it
     * to the {@link LabyConnectSession}. Runs on the Netty event-loop thread;
     * unknown ids are logged at debug and skipped, and the inbound buffer is
     * always released.
     */
    private class ClientHandler extends ChannelInboundHandlerAdapter {
        /**
         * Decodes one framed packet and hands it to the session.
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf raw = (ByteBuf) msg;
            try {
                if (!raw.isReadable()) return;
                PacketBuffer buf = new PacketBuffer(raw);
                int packetId = buf.readVarInt();
                Packet packet;
                try {
                    packet = registry.create(packetId);
                } catch (IllegalArgumentException e) {
                    LOGGER.debug("[LabyConnect] Unknown packet ID: {}, bytes remaining: {}", packetId, raw.readableBytes());
                    return;
                }
                packet.read(buf);
                if (session != null) {
                    session.handlePacket(packet);
                }
            } catch (Exception e) {
                LOGGER.error("Error handling inbound packet", e);
            } finally {
                raw.release();
            }
        }

        /**
         * Fired when the socket closes; marks disconnected and notifies the session.
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            LOGGER.info("Disconnected from LabyConnect");
            connected = false;
            if (session != null) {
                session.onDisconnected();
            }
        }

        /**
         * Logs the error and closes the channel (which triggers {@link #channelInactive}).
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.error("LabyConnect connection error", cause);
            ctx.close();
        }
    }
}
