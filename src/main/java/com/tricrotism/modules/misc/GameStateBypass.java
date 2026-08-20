package com.tricrotism.modules.misc;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket.Type;

/**
 * Cancels selected inbound {@code ClientboundGameEventPacket}s (weather.get(), game-mode
 * change, win-game credits, immediate respawn, limited crafting) so the server
 * can't force those client states. The cancel happens in {@code ConnectionMixin}'s
 * inbound hook, which calls {@link #shouldCancel(ClientboundGameEventPacket)}.
 * Ported (simplified) from the Meteor addon's Game State Spoof.
 */
public final class GameStateBypass extends Module {

    public static final GameStateBypass instance = new GameStateBypass();

    private final Settings.Bool weather =
        bool("Weather (rain)", "weather", "Cancel rain start/stop events", false);
    private final Settings.Bool thunder =
        bool("Thunder", "thunder", "Cancel thunder-level events", false);
    private final Settings.Bool gamemode =
        bool("Game Mode Change", "gamemode", "Cancel game-mode changes", false);
    private final Settings.Bool winGame =
        bool("Win Game (credits)", "winGame", "Cancel the win-game/credits event", false);
    private final Settings.Bool immediateRespawn =
        bool("Immediate Respawn", "immediateRespawn", "Cancel immediate-respawn toggles", false);
    private final Settings.Bool limitedCrafting =
        bool("Limited Crafting", "limitedCrafting", "Cancel limited-crafting toggles", false);


    private GameStateBypass() {
        super("gamestatebypass", "Game State Bypass", "Cancel selected server game-event packets.", Category.NETWORK);
    }

    /**
     * True if this inbound game-event packet should be dropped. Called on the network thread.
     */
    public boolean shouldCancel(ClientboundGameEventPacket packet) {
        if (!isActive()) return false;
        Type t = packet.getEvent();
        if (weather.get() && (t == ClientboundGameEventPacket.START_RAINING
            || t == ClientboundGameEventPacket.STOP_RAINING
            || t == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE)) return true;
        if (thunder.get() && t == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE) return true;
        if (gamemode.get() && t == ClientboundGameEventPacket.CHANGE_GAME_MODE) return true;
        if (winGame.get() && t == ClientboundGameEventPacket.WIN_GAME) return true;
        if (immediateRespawn.get() && t == ClientboundGameEventPacket.IMMEDIATE_RESPAWN) return true;
        return limitedCrafting.get() && t == ClientboundGameEventPacket.LIMITED_CRAFTING;
    }

}
