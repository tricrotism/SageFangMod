package com.tricrotism.modules.login;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;

/**
 * Spoofs client-side fields of the {@code ClientboundLoginPacket} (join game) —
 * hardcore hearts, reduced-debug HUD, death screen, limited crafting, secure-chat
 * enforcement, and the reported max-players / view / simulation distances. Applied
 * by {@code ClientboundLoginPacketMixin}, which reads these getters. Ints of -1
 * mean "leave as sent". Ported from the Meteor addon's game-join-spoof.
 */
public final class GameJoinSpoof extends Module implements Menu {

    public static final GameJoinSpoof instance = new GameJoinSpoof();

    private boolean hardcore;
    private boolean reducedDebugInfo;
    private boolean showDeathScreen;
    private boolean limitedCrafting;
    private boolean secureChat;
    private int maxPlayers;
    private int viewDistance;
    private int simDistance;

    private GameJoinSpoof() {
        super("gamejoinspoof", "Game Join Spoof", "Spoof client-side join-game flags.", "Network");
        hardcore = Config.getBool(baseConfig + ".hardcore", false);
        reducedDebugInfo = Config.getBool(baseConfig + ".reducedDebug", false);
        showDeathScreen = Config.getBool(baseConfig + ".deathScreen", true);
        limitedCrafting = Config.getBool(baseConfig + ".limitedCrafting", false);
        secureChat = Config.getBool(baseConfig + ".secureChat", true);
        maxPlayers = Config.getInt(baseConfig + ".maxPlayers", -1);
        viewDistance = Config.getInt(baseConfig + ".viewDistance", -1);
        simDistance = Config.getInt(baseConfig + ".simDistance", -1);
    }

    public boolean getHardcore() {
        return hardcore;
    }

    public boolean getReducedDebugInfo() {
        return reducedDebugInfo;
    }

    public boolean getShowDeathScreen() {
        return showDeathScreen;
    }

    public boolean getLimitedCrafting() {
        return limitedCrafting;
    }

    public boolean getSecureChat() {
        return secureChat;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public int getSimDistance() {
        return simDistance;
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##gameJoinEnabled", isActive())) toggle();
        ImGui.separator();

        hardcore = boolRow("Hardcore##gjHardcore", hardcore, ".hardcore");
        reducedDebugInfo = boolRow("Reduced Debug Info##gjReducedDebug", reducedDebugInfo, ".reducedDebug");
        showDeathScreen = boolRow("Show Death Screen##gjDeathScreen", showDeathScreen, ".deathScreen");
        limitedCrafting = boolRow("Limited Crafting##gjLimitedCrafting", limitedCrafting, ".limitedCrafting");
        secureChat = boolRow("Enforce Secure Chat##gjSecureChat", secureChat, ".secureChat");

        maxPlayers = intRow("Max Players##gjMaxPlayers", maxPlayers, -1, 2000, ".maxPlayers");
        viewDistance = intRow("View Distance##gjViewDistance", viewDistance, -1, 128, ".viewDistance");
        simDistance = intRow("Simulation Distance##gjSimDistance", simDistance, -1, 128, ".simDistance");
        ImGui.textDisabled("(-1 = leave as sent by server)");

        ImGui.end();
    }

    private boolean boolRow(String id, boolean value, String key) {
        if (ImGui.checkbox(id, value)) {
            value = !value;
            Config.setProperty(baseConfig + key, String.valueOf(value));
        }
        return value;
    }

    private int intRow(String id, int value, int min, int max, String key) {
        int[] v = {value};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt(id, v, min, max)) {
            value = v[0];
            Config.setProperty(baseConfig + key, String.valueOf(value));
        }
        return value;
    }
}
