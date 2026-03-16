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

public class LabyConnectClient {
    private static final String ADDRESS = "chat.labymod.net";
    private static final int PORT = 30336;
    static final Logger LOGGER = LogManager.getLogger("LabyConnect");

    private final PacketRegistry registry;
    private EventLoopGroup group;
    private Channel channel;
    @Getter private LabyConnectSession session;
    private volatile boolean connected;

    public LabyConnectClient() {
        this.registry = new PacketRegistry();
        registerPackets();
    }

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

        // Server-bound
        registry.register(0, HelloPing::new);
        registry.register(3, LoginData::new);
        registry.register(6, LoginOptions::new);
        registry.register(9, LoginVersion::new);
        registry.register(11, EncryptionResponse::new);
        registry.register(63, Pong::new);
        registry.register(68, ServerStatusUpdate::new);
        registry.register(69, UserTracker::new);
    }

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

    public void disconnect() {
        connected = false;
        if (channel != null) {
            channel.close();
            channel = null;
        }
        session = null;
        shutdownGroup();
    }

    private void shutdownGroup() {
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }
    }

    public void sendPacket(Packet packet) {
        sendPacket(packet, null);
    }

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

    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }

    PacketRegistry getRegistry() {
        return registry;
    }

    private class ClientHandler extends ChannelInboundHandlerAdapter {
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

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            LOGGER.info("Disconnected from LabyConnect");
            connected = false;
            if (session != null) {
                session.onDisconnected();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.error("LabyConnect connection error", cause);
            ctx.close();
        }
    }
}
