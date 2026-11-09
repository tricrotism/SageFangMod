package com.tricrotism.mixin.accessors;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code Hud.overlayMessageString} and its remaining time, both private with no getter.
 * <p>
 * The action bar is the only place a server can put a line of text that is neither chat nor a
 * system message, so it is where plugins put readouts that refresh every tick. There is no receive
 * event for it either: {@code ClientboundSetActionBarTextPacket} goes straight to
 * {@code Hud.setOverlayMessage}, so reading the field is the only way a harness can see what a
 * server is showing. Used by {@code RemoteControl}'s {@code actionbar}.
 */
@Mixin(Hud.class)
public interface HudAccessor {

    @Accessor("overlayMessageString")
    Component sagefang$overlayMessage();

    @Accessor("overlayMessageTime")
    int sagefang$overlayMessageTime();
}
