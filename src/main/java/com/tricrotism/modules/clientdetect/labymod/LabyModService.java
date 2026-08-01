package com.tricrotism.modules.clientdetect.labymod;

import com.tricrotism.modules.clientdetect.labymod.protocol.packets.UserTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the LabyConnect lifecycle: connects on game join, periodically
 * syncs the server player list, and tracks which players are LabyMod users
 * via {@code UserBadge} responses.
 */
public class LabyModService {
    private static final Logger LOGGER = LogManager.getLogger("LabyConnect");
    private static final int SYNC_INTERVAL_TICKS = 200;

    private final LabyConnectClient client = new LabyConnectClient();
    /**
     * Players already reported to the server; used to diff add/remove each sync.
     */
    private final Set<UUID> lastSyncedPlayers = new HashSet<>();
    private int tickCounter;
    private volatile boolean handshakeComplete;

    /**
     * Opens the LabyConnect connection and resets per-session sync bookkeeping.
     */
    public void connect() {
        tickCounter = 0;
        handshakeComplete = false;
        lastSyncedPlayers.clear();
        client.connect();
    }

    /**
     * Closes the connection, clears the sync diff and resets {@link LabySocial} state.
     */
    public void disconnect() {
        client.disconnect();
        lastSyncedPlayers.clear();
        LabySocial.instance.reset();
    }

    /**
     * Per-game-tick driver. Latches {@link #handshakeComplete} once the session
     * reaches PLAY or DISCONNECTED, then — only while connected and in PLAY —
     * resyncs the online-player list on the first tick and every
     * {@value #SYNC_INTERVAL_TICKS} ticks thereafter. Called from the game thread.
     */
    public void tick() {
        // Track handshake completion for any session state (even after disconnect)
        if (!handshakeComplete) {
            var session = client.getSession();
            if (session != null) {
                var state = session.getState();
                if (state == LabyConnectSession.State.PLAY
                    || state == LabyConnectSession.State.DISCONNECTED) {
                    handshakeComplete = true;
                }
            }
        }

        // Only sync players when connected and in PLAY state
        if (!client.isConnected()) return;
        if (client.getSession() == null) return;
        if (client.getSession().getState() != LabyConnectSession.State.PLAY) return;

        tickCounter++;
        if (tickCounter == 1 || tickCounter % SYNC_INTERVAL_TICKS == 0) {
            syncPlayerList();
        }
    }

    /**
     * Diffs the current server tab list against {@link #lastSyncedPlayers} and
     * sends {@link UserTracker} add/remove batches for the delta so LabyConnect
     * can report which newly-seen players are LabyMod users. Only valid v4 UUIDs
     * are tracked (NPCs/bots with synthetic ids are skipped). No-op if there is
     * no active server connection.
     */
    private void syncPlayerList() {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        Set<UUID> currentPlayers = new HashSet<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            UUID uuid = info.getProfile().id();
            if (isValidV4UUID(uuid)) {
                currentPlayers.add(uuid);
            }
        }

        Set<UUID> added = new HashSet<>(currentPlayers);
        added.removeAll(lastSyncedPlayers);

        Set<UUID> removed = new HashSet<>(lastSyncedPlayers);
        removed.removeAll(currentPlayers);

        if (!added.isEmpty()) {
            client.sendPacket(new UserTracker(
                UserTracker.CHANNEL_LIST, UserTracker.ACTION_ADD,
                added.toArray(new UUID[0])
            ));
        }

        if (!removed.isEmpty()) {
            client.sendPacket(new UserTracker(
                UserTracker.CHANNEL_LIST, UserTracker.ACTION_REMOVE,
                removed.toArray(new UUID[0])
            ));
        }

        lastSyncedPlayers.clear();
        lastSyncedPlayers.addAll(currentPlayers);
    }

    /**
     * True for a real, non-zero version-4 (random) UUID — filters out bot/NPC ids.
     */
    private static boolean isValidV4UUID(UUID uuid) {
        return uuid.getMostSignificantBits() != 0
            && uuid.getLeastSignificantBits() != 0
            && uuid.version() == 4;
    }

    /**
     * Whether {@code uuid} is a confirmed LabyMod user; false if not connected.
     */
    public boolean isUser(UUID uuid) {
        return client.isConnected()
            && client.getSession() != null
            && client.getSession().isLabyModUser(uuid);
    }

    /**
     * All confirmed LabyMod-user UUIDs this session, or an empty set if not connected.
     */
    public Set<UUID> getUsers() {
        if (!client.isConnected() || client.getSession() == null) return Set.of();
        return client.getSession().getLabyModUsers();
    }

    /**
     * Count of confirmed LabyMod users; convenience over {@link #getUsers()}.
     */
    public int getUserCount() {
        return getUsers().size();
    }

    /**
     * Whether the underlying socket is currently connected and active.
     */
    public boolean isConnected() {
        return client.isConnected();
    }

    /**
     * Returns true once the LabyConnect Mojang auth phase has completed
     * (session reached PLAY or DISCONNECTED). Stays false during the async
     * TCP connect and login handshake.
     */
    public boolean isHandshakeDone() {
        return handshakeComplete;
    }
}
