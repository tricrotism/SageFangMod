package com.tricrotism.modules.logger;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameQuitEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records every player name the server mentions in team packets, including
 * players who never appear in the tab list, building a session history of who is
 * (or was) on the server. Captured in {@code ConnectionMixin}. Ported from the
 * Meteor addon's team-detector.
 */
public final class TeamDetector extends Module {

    public static final TeamDetector instance = new TeamDetector();

    private static final Set<String> PLAYER_HISTORY = ConcurrentHashMap.newKeySet();

    private TeamDetector() {
        super("teamdetector", "Team Detector", "Record player names seen in team packets.", Category.LOGGING);
    }

    /**
     * Called from ConnectionMixin on the network thread.
     */
    public void onTeamPacket(ClientboundSetPlayerTeamPacket packet) {
        if (!isActive()) return;
        var players = packet.getPlayers();
        if (!players.isEmpty()) PLAYER_HISTORY.addAll(players);
    }

    public static Set<String> history() {
        return PLAYER_HISTORY;
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        PLAYER_HISTORY.clear();
    }

    @Override
    public void onDeactivate() {
        PLAYER_HISTORY.clear();
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Seen players: " + PLAYER_HISTORY.size());
        if (ImGui.button("Clear##teamDetectorClear")) PLAYER_HISTORY.clear();
        if (ImGui.treeNode("Names##teamDetectorList")) {
            for (String name : PLAYER_HISTORY) ImGui.text(name);
            ImGui.treePop();
        }
    }
}
