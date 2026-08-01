package com.tricrotism.mixin.accessors;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Setter for {@code AbstractClientPlayer.playerInfo} so a client-only fake player
 * (e.g. Blink's ghost) can be given a real {@link PlayerInfo}, and thus the real
 * skin/cape/hat, without exposing the private field to the compiler.
 */
@Mixin(AbstractClientPlayer.class)
public interface AbstractClientPlayerAccessor {

    @Accessor("playerInfo")
    void sagefang$setPlayerInfo(PlayerInfo playerInfo);
}
