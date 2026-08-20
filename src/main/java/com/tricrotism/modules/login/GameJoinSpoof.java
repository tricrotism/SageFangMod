package com.tricrotism.modules.login;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import imgui.ImGui;
import imgui.ImGuiIO;

/**
 * Spoofs client-side fields of the {@code ClientboundLoginPacket} (join game):
 * hardcore hearts, reduced-debug HUD, death screen, limited crafting, secure-chat
 * enforcement, and the reported max-players / view / simulation distances. Applied
 * by {@code ClientboundLoginPacketMixin}, which reads the getters below. Ints of -1
 * mean "leave as sent". Ported from the Meteor addon's game-join-spoof.
 */
public final class GameJoinSpoof extends Module {

    public static final GameJoinSpoof instance = new GameJoinSpoof();

    private final Settings.Bool hardcore =
        bool("Hardcore", "hardcore", "Report hardcore hearts", false);
    private final Settings.Bool reducedDebugInfo =
        bool("Reduced Debug Info", "reducedDebug", "Report the reduced-debug HUD flag", false);
    private final Settings.Bool showDeathScreen =
        bool("Show Death Screen", "deathScreen", "Report the death-screen flag", true);
    private final Settings.Bool limitedCrafting =
        bool("Limited Crafting", "limitedCrafting", "Report limited crafting", false);
    private final Settings.Bool secureChat =
        bool("Enforce Secure Chat", "secureChat", "Report secure-chat enforcement", true);
    private final Settings.Int maxPlayers =
        integer("Max Players", "maxPlayers", "Reported max players; -1 leaves it as sent", -1, -1, 2000);
    private final Settings.Int viewDistance =
        integer("View Distance", "viewDistance", "Reported view distance; -1 leaves it as sent", -1, -1, 128);
    private final Settings.Int simDistance =
        integer("Simulation Distance", "simDistance", "Reported sim distance; -1 leaves it as sent", -1, -1, 128);

    private GameJoinSpoof() {
        super("gamejoinspoof", "Game Join Spoof", "Spoof client-side join-game flags.", Category.NETWORK);
    }

    public boolean getHardcore() {return hardcore.get();}

    public boolean getReducedDebugInfo() {return reducedDebugInfo.get();}

    public boolean getShowDeathScreen() {return showDeathScreen.get();}

    public boolean getLimitedCrafting() {return limitedCrafting.get();}

    public boolean getSecureChat() {return secureChat.get();}

    public int getMaxPlayers() {return maxPlayers.get();}

    public int getViewDistance() {return viewDistance.get();}

    public int getSimDistance() {return simDistance.get();}

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textDisabled("(-1 = leave as sent by server)");
    }
}
