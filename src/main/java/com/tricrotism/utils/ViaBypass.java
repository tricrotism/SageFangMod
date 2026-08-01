package com.tricrotism.utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Sends already-encoded <em>server</em>-protocol play packets (e.g. 1.21.4 ItemStack clicks)
 * without letting Via re-parse them as modern hashed client packets. Entirely reflective — no
 * compile-time dependency on ViaFabricPlus/ViaVersion; every probe fails safe when Via is absent.
 */
public final class ViaBypass {
    private ViaBypass() {}

    public static void writeSkippingVia(Channel channel, ByteBuf buf) {
        if (tryScheduleViaRawToServer(buf)) {
            return;
        }
        ChannelHandlerContext encoder = findViaEncoderContext(channel);
        if (encoder != null) {
            if (channel.eventLoop().inEventLoop()) {
                encoder.writeAndFlush(buf);
            } else {
                channel.eventLoop().execute(() -> encoder.writeAndFlush(buf));
            }
            return;
        }
        if (channel.eventLoop().inEventLoop()) {
            channel.writeAndFlush(buf);
        } else {
            channel.eventLoop().execute(() -> channel.writeAndFlush(buf));
        }
    }

    public static void flushSkippingVia(Channel channel) {
        ChannelHandlerContext encoder = findViaEncoderContext(channel);
        if (encoder != null) {
            if (channel.eventLoop().inEventLoop()) {
                encoder.flush();
            } else {
                channel.eventLoop().execute(encoder::flush);
            }
        } else {
            channel.flush();
        }
    }

    public static boolean isViaPresent(Channel channel) {
        return findViaEncoderContext(channel) != null || tryGetPlayUserConnection() != null;
    }

    /**
     * ViaFabricPlus target protocol name, or {@code null} if VFP/Via is missing.
     */
    public static String viaFabricPlusTargetVersionName() {
        try {
            Class<?> pt = Class.forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator");
            Object version = pt.getMethod("getTargetVersion").invoke(null);
            if (version == null) {
                return null;
            }
            try {
                return String.valueOf(version.getClass().getMethod("getName").invoke(version));
            } catch (NoSuchMethodException ignored) {
                return String.valueOf(version);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * True when ViaFabricPlus is targeting 1.21.4 (protocol 769).
     */
    public static boolean isViaFabricPlusTarget1214() {
        try {
            Class<?> pt = Class.forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator");
            Object version = pt.getMethod("getTargetVersion").invoke(null);
            if (version == null) {
                return false;
            }
            try {
                Object ver = version.getClass().getMethod("getVersion").invoke(version);
                if (ver instanceof Number && ((Number) ver).intValue() == 769) {
                    return true;
                }
            } catch (NoSuchMethodException ignored) {
            }
            String name = String.valueOf(version).toLowerCase(Locale.ROOT);
            return name.contains("1.21.4") || name.contains("1_21_4");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isViaFabricPlusPresent() {
        try {
            Class.forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean tryScheduleViaRawToServer(ByteBuf buf) {
        Object user = tryGetPlayUserConnection();
        if (user == null) {
            return false;
        }
        try {
            Method schedule = user.getClass().getMethod("scheduleSendRawPacketToServer", ByteBuf.class);
            schedule.invoke(user, buf);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object tryGetPlayUserConnection() {
        try {
            Class<?> pt = Class.forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator");
            Object user = pt.getMethod("getPlayNetworkUserConnection").invoke(null);
            if (user != null) {
                return user;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            Object manager = via.getMethod("getManager").invoke(null);
            Object connectionManager = manager.getClass().getMethod("getConnectionManager").invoke(manager);
            Object connections = connectionManager.getClass().getMethod("getConnections").invoke(connectionManager);
            if (connections instanceof Iterable<?> it) {
                for (Object c : it) {
                    if (c != null) {
                        return c;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ChannelHandlerContext findViaEncoderContext(Channel channel) {
        if (channel == null) {
            return null;
        }
        ChannelPipeline pipeline = channel.pipeline();

        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            Object manager = via.getMethod("getManager").invoke(null);
            Object injector = manager.getClass().getMethod("getInjector").invoke(manager);
            Object name = injector.getClass().getMethod("getEncoderName").invoke(injector);
            if (name instanceof String s) {
                ChannelHandlerContext ctx = pipeline.context(s);
                if (ctx != null) {
                    return ctx;
                }
            }
        } catch (Throwable ignored) {
        }

        for (String name : pipeline.names()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("decode")) {
                continue;
            }
            if (lower.contains("via") && (lower.contains("encode") || lower.equals("via-codec") || lower.equals("via_codec"))) {
                ChannelHandlerContext ctx = pipeline.context(name);
                if (ctx != null) {
                    return ctx;
                }
            }
        }
        return null;
    }
}
