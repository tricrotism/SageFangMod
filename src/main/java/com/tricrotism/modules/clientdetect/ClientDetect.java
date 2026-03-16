package com.tricrotism.modules.clientdetect;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.modules.clientdetect.labymod.LabyModService;

import java.util.UUID;

/**
 * Detects LabyMod users on the current server and displays badges in the tab
 * list. Connects to LabyConnect TCP on game join and disconnects on quit.
 */
public class ClientDetect extends Module {
    public static final ClientDetect instance = new ClientDetect();

    private final LabyModService labyMod = new LabyModService();

    private ClientDetect() {
        super("clientdetect", "Client Detect",
            "Detects LabyMod users on the server",
            "Network");
    }

    // ── lifecycle ───────────────────────────────────────────────

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        labyMod.connect();
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        labyMod.disconnect();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        labyMod.tick();
    }

    // ── public API (used by mixin + menus) ──────────────────────

    public boolean isLabyModUser(UUID uuid) {
        return labyMod.isUser(uuid);
    }

    public int getLabyModUserCount() {
        return labyMod.getUserCount();
    }

    public boolean isLabyModConnected() {
        return labyMod.isConnected();
    }

}
