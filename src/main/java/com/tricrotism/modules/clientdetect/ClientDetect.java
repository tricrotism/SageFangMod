package com.tricrotism.modules.clientdetect;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.modules.clientdetect.labymod.LabyGroup;
import com.tricrotism.modules.clientdetect.labymod.LabyModService;
import com.tricrotism.modules.clientdetect.labymod.LabySocial;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.ChatMsg;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.Friend;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.ServerInfo;
import com.tricrotism.modules.clientdetect.labymod.protocol.model.UserStatus;
import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/**
 * Detects LabyMod users on the current server (tab-list badges) and exposes the
 * LabyConnect social layer via this module's ImGui window: friends, requests,
 * direct messages and status. Always active; the window is shown by toggling
 * the module's visibility in Settings.
 */
public class ClientDetect extends Module {
    public static final ClientDetect instance = new ClientDetect();

    private final Settings.Key openKey = key("Open (free cursor)", "openkey", "Open the panel with a free cursor", GLFW.GLFW_KEY_HOME);

    private static final UserStatus[] STATUS_VALUES = {UserStatus.ONLINE, UserStatus.AWAY, UserStatus.BUSY, UserStatus.OFFLINE};
    private static final String[] STATUS_LABELS = {"Online", "Away", "Busy", "Invisible"};
    private static final int MAX_VISIBLE_FRIENDS = 8;

    private final LabyModService labyMod = new LabyModService();

    private final ImInt statusIndex = new ImInt(0);
    private final ImString addFriendBuffer = new ImString(32);
    private final ImString chatInputBuffer = new ImString(256);
    private UUID openChat;
    private boolean wasTyping;
    private boolean refocusChat;
    private boolean refocusAddFriend;
    private boolean openKeyWasDown;

    private ClientDetect() {
        super("clientdetect", "Client Detect",
            "LabyMod detection + friends, chat and status",
            "Network");
    }

    /**
     * LabyMod detection is always on. The LabyConnect session runs whenever
     * connected to a server regardless of the persisted enabled flag.
     */
    @Override
    public boolean isActive() {
        return true;
    }

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
        handleOpenKey();
    }

    /**
     * Opens a cursor-freeing {@link ClientDetectScreen} when the bound key is
     * pressed in-world, so the ImGui window becomes clickable (friends, chat,
     * status). Only fires while the window is visible and no screen is open;
     * closing is via Esc (vanilla screen dismissal).
     */
    private void handleOpenKey() {
        if (!isVisible()) {
            openKeyWasDown = false;
            return;
        }
        int key = openKey.get();
        if (key == GLFW.GLFW_KEY_UNKNOWN) return;
        boolean down = KeybindUtil.isKeyDown(key);
        if (down && !openKeyWasDown && mc.gui.screen() == null) {
            mc.gui.setScreen(new ClientDetectScreen());
        }
        openKeyWasDown = down;
    }

    public boolean isLabyModUser(UUID uuid) {
        return labyMod.isUser(uuid);
    }

    public int getLabyModUserCount() {
        return labyMod.getUserCount();
    }

    public boolean isLabyModConnected() {
        return labyMod.isConnected();
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;
        try {
            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            if (mc.gui.screen() == null) flags |= ImGuiWindowFlags.NoInputs;
            ImGui.setNextWindowBgAlpha(0.55f);
            ImGui.begin(title, flags);

            LabySocial social = LabySocial.instance;
            if (!social.isConnected()) {
                ImGui.textDisabled("Not connected to LabyConnect.");
                ImGui.end();
                return;
            }

            List<Friend> friends = social.getFriends();

            renderStatusSelector(social);
            ImGui.separator();
            renderAddFriend(social);
            renderRequests(social);
            ImGui.separatorText("Friends");
            renderFriendList(social, friends);

            if (openChat != null) {
                ImGui.separator();
                renderChat(social, friends);
            }

            ImGui.separator();
            renderOpenKeyButton();

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in ClientDetect menu", e);
        }
    }

    private void renderOpenKeyButton() {
        openKey.render();
    }

    private void renderStatusSelector(LabySocial social) {
        UserStatus current = social.getSelfStatus();
        if (STATUS_VALUES[statusIndex.get()] != current) {
            for (int i = 0; i < STATUS_VALUES.length; i++) {
                if (STATUS_VALUES[i] == current) statusIndex.set(i);
            }
        }
        ImGui.setNextItemWidth(140);
        if (ImGui.combo("Status##labyStatus", statusIndex, STATUS_LABELS)) {
            social.setStatus(STATUS_VALUES[statusIndex.get()]);
        }
    }

    private void renderAddFriend(LabySocial social) {
        if (refocusAddFriend) {
            ImGui.setKeyboardFocusHere();
            refocusAddFriend = false;
        }
        ImGui.setNextItemWidth(160);
        boolean entered = ImGui.inputText("##labyAddFriend", addFriendBuffer, ImGuiInputTextFlags.EnterReturnsTrue);
        ImGui.sameLine();
        if ((ImGui.button("Add Friend##labyAdd") || entered) && !addFriendBuffer.get().trim().isEmpty()) {
            social.addFriend(addFriendBuffer.get().trim());
            addFriendBuffer.set("");
            refocusAddFriend = true;
        }
    }

    private void renderRequests(LabySocial social) {
        List<Friend> requests = social.getIncomingRequests();
        if (requests.isEmpty()) return;
        ImGui.separatorText("Requests (" + requests.size() + ")");
        for (Friend req : requests) {
            ImGui.text(req.getName());
            renderBadge(social.getGroup(req.getUuid()));
            ImGui.sameLine();
            if (ImGui.button("Accept##req_" + req.getUuid())) social.acceptRequest(req);
            ImGui.sameLine();
            if (ImGui.button("Deny##req_" + req.getUuid())) social.denyRequest(req);
        }
    }

    private void renderFriendList(LabySocial social, List<Friend> friends) {
        if (friends.isEmpty()) {
            ImGui.textDisabled("No friends.");
            return;
        }
        float maxH = ImGui.getTextLineHeightWithSpacing() * MAX_VISIBLE_FRIENDS;
        ImGui.beginChild("##labyFriends", 320, maxH, true, ImGuiWindowFlags.None);
        for (Friend f : friends) {
            float[] c = statusColor(f.getStatus());
            ImGui.textColored(c[0], c[1], c[2], 1.0f, "●");
            ImGui.sameLine();
            boolean selected = f.getUuid().equals(openChat);
            if (ImGui.selectable(f.getName() + "##friend_" + f.getUuid(), selected)) {
                openChat = f.getUuid();
                wasTyping = false;
                chatInputBuffer.set("");
            }
            renderBadge(social.getGroup(f.getUuid()));
            ServerInfo server = f.getServer();
            if (server != null && server.isPresent()) {
                ImGui.sameLine();
                ImGui.textDisabled("— " + server.getAddress());
            }
        }
        ImGui.endChild();
    }

    private void renderChat(LabySocial social, List<Friend> friends) {
        Friend friend = friendByUuid(friends, openChat);
        if (friend == null) {
            openChat = null;
            return;
        }
        ImGui.text("Chat with " + friend.getName());
        ImGui.sameLine();
        if (ImGui.smallButton("Close##labyChatClose")) {
            openChat = null;
            return;
        }

        float histH = ImGui.getTextLineHeightWithSpacing() * 7;
        ImGui.beginChild("##labyChatHist", 320, histH, true, ImGuiWindowFlags.None);
        for (ChatMsg msg : social.getHistory(openChat)) {
            if (msg.outgoing()) {
                ImGui.textColored(0.55f, 0.75f, 1.0f, 1.0f, "You: ");
            } else {
                ImGui.textColored(0.6f, 0.85f, 0.45f, 1.0f, friend.getName() + ": ");
            }
            ImGui.sameLine();
            ImGui.textWrapped(msg.text());
        }
        if (ImGui.getScrollY() >= ImGui.getScrollMaxY() - 2f) {
            ImGui.setScrollHereY(1f);
        }
        ImGui.endChild();

        if (social.isTyping(openChat)) {
            ImGui.textDisabled(friend.getName() + " is typing…");
        }

        if (refocusChat) {
            ImGui.setKeyboardFocusHere();
            refocusChat = false;
        }
        ImGui.setNextItemWidth(260);
        boolean send = ImGui.inputText("##labyChatInput", chatInputBuffer, ImGuiInputTextFlags.EnterReturnsTrue);
        ImGui.sameLine();
        send |= ImGui.button("Send##labyChatSend");

        boolean hasText = !chatInputBuffer.get().trim().isEmpty();
        if (send && hasText) {
            social.sendMessage(friend, chatInputBuffer.get().trim());
            chatInputBuffer.set("");
            refocusChat = true;
            if (wasTyping) {
                social.sendTyping(friend, false);
                wasTyping = false;
            }
        } else if (hasText != wasTyping) {
            social.sendTyping(friend, hasText);
            wasTyping = hasText;
        }
    }

    private static Friend friendByUuid(List<Friend> friends, UUID uuid) {
        for (Friend f : friends) {
            if (f.getUuid().equals(uuid)) return f;
        }
        return null;
    }

    private static float[] statusColor(UserStatus status) {
        return switch (status) {
            case ONLINE -> new float[]{0.3f, 0.85f, 0.4f};
            case AWAY -> new float[]{0.95f, 0.75f, 0.2f};
            case BUSY -> new float[]{0.9f, 0.3f, 0.3f};
            case OFFLINE -> new float[]{0.6f, 0.6f, 0.6f};
        };
    }

    /**
     * Appends an inline, color-coded rank badge (e.g. {@code [Partner]}) on the current line; no-op if none.
     */
    private static void renderBadge(LabyGroup group) {
        if (group == null) return;
        float[] rgb = group.rgb();
        ImGui.sameLine();
        ImGui.textColored(rgb[0], rgb[1], rgb[2], 1.0f, "[" + group.name() + "]");
    }
}
