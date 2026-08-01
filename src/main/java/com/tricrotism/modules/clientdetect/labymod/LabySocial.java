package com.tricrotism.modules.clientdetect.labymod;

import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.ChatMsg;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.ServerInfo;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.UserStatus;
import com.tricrotism.modules.clientdetect.labymod.protocol.packets.*;
import com.tricrotism.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central LabyConnect social state: friends, incoming requests, direct-message
 * history, typing indicators and the local online status. Receive handlers run
 * on the Netty thread (state is concurrent); user-facing alerts hop to the
 * render thread via {@link Minecraft#execute(Runnable)}. Send helpers write
 * through the active {@link LabyConnectClient}.
 */
public final class LabySocial {

    /**
     * Process-wide singleton; receive handlers are invoked through this from the session.
     */
    public static final LabySocial instance = new LabySocial();

    private static final long TYPING_TIMEOUT_MS = 5_000L;

    private final Minecraft mc = Minecraft.getInstance();

    private final Map<UUID, Friend> friends = new ConcurrentHashMap<>();
    private final List<Friend> incomingRequests = new CopyOnWriteArrayList<>();
    private final Map<UUID, List<ChatMsg>> history = new ConcurrentHashMap<>();
    private final Map<UUID, Long> typingUntil = new ConcurrentHashMap<>();
    /**
     * Per-user rank group id from {@code PacketUserBadge}; resolved to a label via {@link LabyGroup}.
     */
    private final Map<UUID, Integer> groups = new ConcurrentHashMap<>();

    /**
     * Binds the active
     * once login completes, enabling
     * the send helpers. A
     * client (set by
     * ) disables sending.
     */
    @Setter private volatile LabyConnectClient client;
    /**
     * The local player's current broadcast status.
     */
    @Getter private volatile UserStatus selfStatus = UserStatus.ONLINE;

    private LabySocial() {}

    /**
     * Clears all social state and unbinds the client (on disconnect).
     */
    public void reset() {
        client = null;
        friends.clear();
        incomingRequests.clear();
        history.clear();
        typingUntil.clear();
        groups.clear();
    }

    /**
     * Replaces the full friend/request snapshot from a {@code LoginFriend}
     * packet: entries flagged {@link Friend#isFriendRequest()} go to the
     * incoming-requests list, the rest into the friends map.
     */
    public void onFriendList(List<Friend> list) {
        friends.clear();
        incomingRequests.clear();
        for (Friend f : list) {
            if (f.isFriendRequest()) {
                incomingRequests.add(f);
            } else {
                friends.put(f.getUuid(), f);
            }
        }
    }

    /**
     * A friend came online: updates the map and shows a chat alert (with sound).
     */
    public void onPlayerOnline(Friend friend) {
        friends.put(friend.getUuid(), friend);
        alert(MiniMessage.format("<off_white><name> <emerald>is now online.",
            Placeholder.unparsed("name", safe(friend.getName()))), true);
    }

    /**
     * A friend's presence/server changed: attaches the {@link ServerInfo} and stores them.
     */
    public void onFriendStatus(Friend friend, ServerInfo info) {
        friend.setServer(info);
        friends.put(friend.getUuid(), friend);
    }

    /**
     * A friend started playing somewhere: alerts with the server address if
     * known, else the supplied game-mode name (or a generic "a server").
     */
    public void onPlayingOn(Friend friend, String gameMode) {
        friends.put(friend.getUuid(), friend);
        ServerInfo server = friend.getServer();
        String where = server != null && server.isPresent() ? server.getAddress()
            : (gameMode == null || gameMode.isEmpty() ? "a server" : gameMode);
        alert(MiniMessage.format("<off_white><name> <highlight>is now playing on <off_white><server><highlight>.",
            Placeholder.unparsed("name", safe(friend.getName())),
            Placeholder.unparsed("server", safe(where))), true);
    }

    /**
     * Incoming direct message: appends to that peer's local history, clears any
     * typing indicator for them, and shows a chat alert (no sound). Ignored if
     * the packet has no sender.
     */
    public void onMessage(Message packet) {
        Friend sender = packet.getSender();
        if (sender == null) return;

        history.computeIfAbsent(sender.getUuid(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new ChatMsg(sender.getUuid(), packet.getMessage(), packet.getSentTime(), false));
        typingUntil.remove(sender.getUuid());

        alert(MiniMessage.format("<highlight><name> <dark_gray>» <off_white><msg>",
            Placeholder.unparsed("name", safe(sender.getName())),
            Placeholder.unparsed("msg", safe(packet.getMessage()))), false);
    }

    /**
     * Records the rank group ids carried by a {@code PacketUserBadge} batch
     * (uuids parallel to ranks). The byte is an unsigned group id; {@code 0} is
     * the default group and stores no badge. Surfaced via {@link #getGroup(UUID)}.
     */
    public void onUserBadges(UUID[] uuids, byte[] ranks) {
        if (uuids == null || ranks == null) return;
        int n = Math.min(uuids.length, ranks.length);
        for (int i = 0; i < n; i++) {
            int id = ranks[i] & 0xFF;
            if (id != 0) groups.put(uuids[i], id);
        }
    }

    /**
     * A broadcast/system message from the LabyConnect server: shown as an info alert.
     */
    public void onServerMessage(String message) {
        alert(MiniMessage.format("<info><msg>", Placeholder.unparsed("msg", safe(message))), false);
    }

    /**
     * Typing indicator from a peer: arms a {@value #TYPING_TIMEOUT_MS}ms timeout
     * when typing, or clears it when not. Drives {@link #isTyping(UUID)}.
     */
    public void onTyping(PlayTyping packet) {
        if (packet.getPlayer() == null) return;
        UUID uuid = packet.getPlayer().getUuid();
        if (packet.isTyping()) {
            typingUntil.put(uuid, System.currentTimeMillis() + TYPING_TIMEOUT_MS);
        } else {
            typingUntil.remove(uuid);
        }
    }

    /**
     * Result of an outgoing add-friend request: alerts success or the failure reason.
     */
    public void onAddFriendResponse(PlayRequestAddFriendResponse packet) {
        if (packet.isRequestSent()) {
            alert(MiniMessage.format("<success>Friend request sent to <name>.",
                Placeholder.unparsed("name", safe(packet.getSearched()))), false);
        } else {
            alert(MiniMessage.format("<error>Could not add <name>: <reason>",
                Placeholder.unparsed("name", safe(packet.getSearched())),
                Placeholder.unparsed("reason", safe(packet.getReason()))), false);
        }
    }

    /**
     * Sends an add-friend request by player name. The outcome arrives
     * asynchronously via {@link #onAddFriendResponse}.
     *
     * @return false if not connected (nothing sent)
     */
    public boolean addFriend(String name) {
        return send(new PlayRequestAddFriend(name));
    }

    /**
     * Removes a friend locally and notifies the server. Returns false if not connected.
     */
    public boolean removeFriend(Friend friend) {
        friends.remove(friend.getUuid());
        return send(new PlayFriendRemove(friend));
    }

    /**
     * Accept an incoming request by sending the reciprocal add.
     */
    public boolean acceptRequest(Friend request) {
        incomingRequests.removeIf(f -> f.getUuid().equals(request.getUuid()));
        return send(new PlayRequestAddFriend(request.getName()));
    }

    /**
     * Rejects an incoming request: drops it locally and sends the deny packet.
     */
    public boolean denyRequest(Friend request) {
        incomingRequests.removeIf(f -> f.getUuid().equals(request.getUuid()));
        return send(new PlayDenyFriendRequest(request));
    }

    /**
     * Sends a direct message to a friend and, on success, appends it to local
     * history as an outgoing entry. Returns false if the local player is
     * unavailable or the client isn't connected.
     */
    public boolean sendMessage(Friend to, String text) {
        Friend self = selfFriend();
        if (self == null) return false;
        long now = System.currentTimeMillis();
        if (!send(new Message(self, to, text, now))) return false;
        history.computeIfAbsent(to.getUuid(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new ChatMsg(to.getUuid(), text, now, true));
        return true;
    }

    /**
     * Notifies a friend that the local player is (or stopped) typing to them.
     */
    public boolean sendTyping(Friend to, boolean typing) {
        Friend self = selfFriend();
        if (self == null) return false;
        return send(new PlayTyping(self, to, typing));
    }

    /**
     * Sets and broadcasts the local online status. Updates {@link #selfStatus}
     * even if not connected; only the network send is conditional.
     */
    public boolean setStatus(UserStatus status) {
        this.selfStatus = status;
        return send(new PlayChangeOptions(true, status));
    }

    /**
     * A snapshot copy of the current friends (safe to iterate off any thread).
     */
    public List<Friend> getFriends() {
        return new ArrayList<>(friends.values());
    }

    /**
     * A snapshot copy of the pending incoming friend requests.
     */
    public List<Friend> getIncomingRequests() {
        return new ArrayList<>(incomingRequests);
    }

    /**
     * Case-insensitive name lookup across friends then incoming requests.
     *
     * @return the matching {@link Friend}, or {@code null} if none
     */
    public Friend findFriendByName(String name) {
        for (Friend f : friends.values()) {
            if (f.getName() != null && f.getName().equalsIgnoreCase(name)) return f;
        }
        for (Friend f : incomingRequests) {
            if (f.getName() != null && f.getName().equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    /**
     * A snapshot of the locally-kept DM history with {@code peer}, or empty if none.
     */
    public List<ChatMsg> getHistory(UUID peer) {
        List<ChatMsg> list = history.get(peer);
        if (list == null) return List.of();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    /**
     * The LabyMod rank group for a user (partner/staff/Laby+/…), or {@code null} if none/unknown.
     */
    public LabyGroup getGroup(UUID uuid) {
        Integer id = groups.get(uuid);
        return id == null ? null : LabyGroup.byId(id);
    }

    /**
     * True if {@code uuid}'s typing indicator is still within its timeout window.
     */
    public boolean isTyping(UUID uuid) {
        Long until = typingUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * True once a client is bound (login complete); gates the send helpers.
     */
    public boolean isConnected() {
        return client != null;
    }

    /**
     * Builds a minimal Friend representing the local player, or null if unavailable.
     */
    private Friend selfFriend() {
        var user = mc.getUser();
        if (user == null) return null;
        Friend self = new Friend(user.getProfileId(), user.getName());
        self.setStatus(selfStatus);
        return self;
    }

    /**
     * Sends a packet through the bound client; returns false (no-op) if none is bound.
     */
    private boolean send(com.tricrotism.modules.clientdetect.labymod.protocol.Packet packet) {
        LabyConnectClient c = this.client;
        if (c == null) return false;
        c.sendPacket(packet);
        return true;
    }

    /**
     * Posts a prefixed chat message (and optional notification sound) on the
     * render thread via {@link Minecraft#execute(Runnable)}, since receive
     * handlers run on the Netty thread. Skipped if no player is in-world. The
     * Adventure component is converted to native before display.
     */
    private void alert(net.kyori.adventure.text.Component component, boolean sound) {
        mc.execute(() -> {
            if (mc.player == null) return;
            MessageUtils.sendMessage(mc, Component.empty().append(MinecraftClientAudiences.of().asNative(component)));
            if (sound) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
            }
        });
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
