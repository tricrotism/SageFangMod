package com.tricrotism.mixin.impl.network;

import com.tricrotism.modules.logger.ChannelLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the raw body of unknown plugin-message payloads for {@link ChannelLogger}.
 * <p>
 * In 26.1 an unrecognised custom payload decodes to a {@link DiscardedPayload}, which keeps only
 * the channel id. The decoder {@code skipBytes} the body, so by the time the packet reaches
 * {@code Connection.channelRead0} the content is gone. This decoder lambda is the last point the
 * bytes are readable.
 * <p>
 * Injected with {@code require = 0} on purpose: the target is a compiler-generated lambda, so if
 * its name ever shifts the injector simply no-ops (channel names still log, contents don't) rather
 * than hard-failing mod init under {@code defaultRequire: 1}.
 */
@Mixin(DiscardedPayload.class)
public abstract class DiscardedPayloadMixin {

    @Inject(
        method = "lambda$codec$1(ILnet/minecraft/resources/Identifier;Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/DiscardedPayload;",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private static void sagefang$capturePayload(int maxSize, Identifier id, FriendlyByteBuf buf,
                                                CallbackInfoReturnable<DiscardedPayload> cir) {
        if (!ChannelLogger.instance.isActive()) return;

        int length = buf.readableBytes();
        if (length <= 0 || length > maxSize) return;

        byte[] bytes = new byte[length];
        buf.getBytes(buf.readerIndex(), bytes);
        ChannelLogger.instance.onRawPayload(id.toString(), bytes);
    }
}
