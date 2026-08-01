package com.tricrotism.utils;

import com.tricrotism.mixin.accessors.ConnectionAccessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.VarInt;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Builds and sends 1.21.4-style {@code ServerboundContainerClickPacket} payloads that still carry
 * full {@code ItemStack}s (not modern {@code HashedStack}s), written past Via's encoder so it does
 * not re-parse them. Uses hardcoded 1.21.4 registry ids. Requires ViaFabricPlus targeting 1.21.4.
 */
public final class Legacy1214ClickSlotPackets {

    /**
     * Play packet id for {@code container_click} on 1.21.4 (protocol 769).
     */
    public static final int PACKET_ID_1_21_4 = 0x10;

    public static final int MODIFIED_SLOT_COUNT = 128;

    private static final int STONE_ID_1_21_4 = 1;
    private static final int TOOL_COMPONENT_ID_1_21_4 = 26;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LegacyClickSlot-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private Legacy1214ClickSlotPackets() {}

    public static boolean isBusy() {
        return BUSY.get();
    }

    public static void sendAsync(int packetCount, int toolEntryListSize, int packetId,
                                 Consumer<String> onDone, Consumer<String> onError) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.level == null) {
            if (onError != null) onError.accept("Not in a world.");
            return;
        }
        if (!BUSY.compareAndSet(false, true)) {
            if (onError != null) onError.accept("Already sending legacy click-slot packets.");
            return;
        }

        Connection connection = mc.getConnection().getConnection();
        Channel channel = ((ConnectionAccessor) connection).sagefang$getChannel();
        if (channel == null) {
            BUSY.set(false);
            if (onError != null) onError.accept("Connection has no channel yet.");
            return;
        }

        boolean viaPresent = ViaBypass.isViaPresent(channel);
        int packets = packetCount;
        int size = toolEntryListSize;
        int id = packetId;

        WORKER.execute(() -> {
            ByteBuf template = null;
            try {
                template = encodePacketTemplate(id, size);
                int bytesEach = template.readableBytes();
                for (int i = 0; i < packets; i++) {
                    if (!channel.isActive()) {
                        throw new IllegalStateException("Channel closed while sending.");
                    }
                    while (!channel.isWritable() && channel.isActive()) {
                        ViaBypass.flushSkippingVia(channel);
                        try {
                            Thread.sleep(1L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while waiting for channel writable.", ie);
                        }
                    }
                    ViaBypass.writeSkippingVia(channel, template.retainedDuplicate());
                    if ((i & 0xFF) == 0xFF) {
                        ViaBypass.flushSkippingVia(channel);
                    }
                }
                ViaBypass.flushSkippingVia(channel);
                if (onDone != null) {
                    String msg = "Sent " + packets + "x click-slot (each " + bytesEach + " bytes, tool size "
                        + size + ", id 0x" + Integer.toHexString(id)
                        + (viaPresent ? ", Via raw-to-server" : ", direct channel") + ").";
                    mc.execute(() -> onDone.accept(msg));
                }
            } catch (Throwable t) {
                if (onError != null) {
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    mc.execute(() -> onError.accept(msg));
                }
            } finally {
                if (template != null) {
                    template.release();
                }
                BUSY.set(false);
            }
        });
    }

    public static ByteBuf encodePacketTemplate(int packetId, int toolEntryListSize) {
        if (toolEntryListSize < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }

        ByteBuf itemBytes = Unpooled.buffer();
        try {
            writeLagStoneItemStack1214(itemBytes, toolEntryListSize);

            ByteBuf packet = Unpooled.buffer();
            VarInt.write(packet, packetId);
            VarInt.write(packet, 0);
            VarInt.write(packet, 0);
            packet.writeShort(0);
            packet.writeByte(0);
            VarInt.write(packet, 0);

            VarInt.write(packet, MODIFIED_SLOT_COUNT);
            for (int i = 0; i < MODIFIED_SLOT_COUNT; i++) {
                packet.writeShort(i);
                packet.writeBytes(itemBytes, itemBytes.readerIndex(), itemBytes.readableBytes());
            }
            packet.writeBytes(itemBytes, itemBytes.readerIndex(), itemBytes.readableBytes());
            return packet;
        } finally {
            itemBytes.release();
        }
    }

    public static void writeLagStoneItemStack1214(ByteBuf buf, int entryListSize) {
        VarInt.write(buf, 1);
        VarInt.write(buf, STONE_ID_1_21_4);

        VarInt.write(buf, 1);
        VarInt.write(buf, 0);
        VarInt.write(buf, TOOL_COMPONENT_ID_1_21_4);

        VarInt.write(buf, 1);
        writeRepeatedStoneHolderSet(buf, entryListSize);
        buf.writeBoolean(true);
        buf.writeFloat(6.0F);
        buf.writeBoolean(true);
        buf.writeBoolean(true);

        buf.writeFloat(1.0F);
        VarInt.write(buf, 1);
    }

    private static void writeRepeatedStoneHolderSet(ByteBuf buf, int entryListSize) {
        VarInt.write(buf, entryListSize + 1);
        for (int i = 0; i < entryListSize; i++) {
            VarInt.write(buf, STONE_ID_1_21_4);
        }
    }
}
