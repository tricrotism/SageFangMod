package com.tricrotism.modules.misc;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket.Type;

/**
 * Cancels selected inbound {@code ClientboundGameEventPacket}s (weather, game-mode
 * change, win-game credits, immediate respawn, limited crafting) so the server
 * can't force those client states. The cancel happens in {@code ConnectionMixin}'s
 * inbound hook, which calls {@link #shouldCancel(ClientboundGameEventPacket)}.
 * Ported (simplified) from the Meteor addon's Game State Spoof.
 */
public final class GameStateBypass extends Module implements Menu {

    public static final GameStateBypass instance = new GameStateBypass();

    private boolean weather;
    private boolean thunder;
    private boolean gamemode;
    private boolean winGame;
    private boolean immediateRespawn;
    private boolean limitedCrafting;

    private GameStateBypass() {
        super("gamestatebypass", "Game State Bypass", "Cancel selected server game-event packets.", "Network");
        weather = Config.getBool(baseConfig + ".weather", false);
        thunder = Config.getBool(baseConfig + ".thunder", false);
        gamemode = Config.getBool(baseConfig + ".gamemode", false);
        winGame = Config.getBool(baseConfig + ".winGame", false);
        immediateRespawn = Config.getBool(baseConfig + ".immediateRespawn", false);
        limitedCrafting = Config.getBool(baseConfig + ".limitedCrafting", false);
    }

    /**
     * True if this inbound game-event packet should be dropped. Called on the network thread.
     */
    public boolean shouldCancel(ClientboundGameEventPacket packet) {
        if (!isActive()) return false;
        Type t = packet.getEvent();
        if (weather && (t == ClientboundGameEventPacket.START_RAINING
            || t == ClientboundGameEventPacket.STOP_RAINING
            || t == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE)) return true;
        if (thunder && t == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE) return true;
        if (gamemode && t == ClientboundGameEventPacket.CHANGE_GAME_MODE) return true;
        if (winGame && t == ClientboundGameEventPacket.WIN_GAME) return true;
        if (immediateRespawn && t == ClientboundGameEventPacket.IMMEDIATE_RESPAWN) return true;
        return limitedCrafting && t == ClientboundGameEventPacket.LIMITED_CRAFTING;
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##gsbEnabled", isActive())) toggle();
        ImGui.separatorText("Cancel");

        weather = boolRow("Weather (rain)##gsbWeather", weather, ".weather");
        thunder = boolRow("Thunder##gsbThunder", thunder, ".thunder");
        gamemode = boolRow("Game Mode Change##gsbGamemode", gamemode, ".gamemode");
        winGame = boolRow("Win Game (credits)##gsbWin", winGame, ".winGame");
        immediateRespawn = boolRow("Immediate Respawn##gsbRespawn", immediateRespawn, ".immediateRespawn");
        limitedCrafting = boolRow("Limited Crafting##gsbCrafting", limitedCrafting, ".limitedCrafting");

        ImGui.end();
    }

    private boolean boolRow(String id, boolean value, String key) {
        if (ImGui.checkbox(id, value)) {
            value = !value;
            Config.setProperty(baseConfig + key, String.valueOf(value));
        }
        return value;
    }
}
