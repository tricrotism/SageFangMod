package com.tricrotism.modules.inventory;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.config.SageFangConfig;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.modules.packets.UIPacketDelay;
import com.tricrotism.utils.ImGuiUtil;
import com.tricrotism.utils.KeybindUtil;
import com.tricrotism.utils.MessageUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Pokes at how a server tracks open containers, for hunting duplication bugs in plugins.
 * <p>
 * Most container dupes come down to the client and server disagreeing about which GUI is open.
 * This window manufactures that disagreement. It can keep a GUI on screen after the server closed
 * it, close one without telling the server, or tell the server it closed while it stays open. It can
 * also hold UI clicks back so they land in one burst or after a disconnect.
 * <p>
 * Recorded clicks replay into whatever container is open, so a sequence that duped once runs again
 * exactly. The saved screen holds its menu, and the menu holds world references, so quitting drops it.
 */
public final class ContainerTools extends Module {

    public static final ContainerTools instance = new ContainerTools();

    private final Settings.Key restoreKey =
        key("Restore Key", "restoreKey", "Reopen the saved GUI", GLFW.GLFW_KEY_UNKNOWN);
    private final Settings.Int replayDelayMs = integer("Replay Delay (ms)", "replayDelayMs",
        "Gap between replayed clicks. 0 keeps the recorded timing, at tick resolution", 0, 0, 2000);

    private record RecordedClick(int slot, int button, ContainerInput input, long gapNanos) {}

    private Screen savedScreen;
    private LocalPlayer savedPlayer;
    private AbstractContainerMenu savedMenu;
    private String savedTitle = "";

    // Read on the network thread to notice the server closing the saved GUI.
    private volatile int savedContainerId = -1;
    private volatile boolean savedClosed;

    private boolean disconnectNextTick;
    private boolean keyWasDown;

    private final List<RecordedClick> recorded = new ArrayList<>();
    private boolean recording;
    private long lastClickNanos;
    private int replayIndex = -1;
    private long nextReplayNanos;

    private final ImString commandInput = new ImString(256);
    private final ImInt buttonId = new ImInt();
    private int labelContainerId = -1;
    private int labelStateId = -1;
    private String containerLabel = "";

    private ContainerTools() {
        super("containertools", "Container Tools",
            "Save, desync and replay container GUIs to probe plugin dupes.", Category.EXPLOIT);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (disconnectNextTick) {
            disconnectNextTick = false;
            var connection = mc.getConnection();
            if (connection != null) {
                connection.getConnection().disconnect(Component.literal("Disconnected by Container Tools"));
            }
        }

        if (replayIndex >= 0) stepReplay();

        int key = restoreKey.get();
        if (isActive() && key != GLFW.GLFW_KEY_UNKNOWN) {
            boolean down = KeybindUtil.isKeyDown(key);
            if (down && !keyWasDown) restoreGui();
            keyWasDown = down;
        }
    }

    @Override
    public void onDeactivate() {
        stopAll();
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        stopAll();
    }

    private void stopAll() {
        clearSaved();
        disconnectNextTick = false;
        recording = false;
        replayIndex = -1;
    }

    /**
     * Called from ConnectionMixin for serverbound packets.
     */
    public void onOutbound(Packet<?> packet) {
        if (packet instanceof ServerboundContainerClosePacket close && close.getContainerId() == savedContainerId) {
            savedClosed = true;
        }
    }

    /**
     * Called from ConnectionMixin on the network thread. An open-screen packet closes the saved
     * container server-side, same as a close packet.
     */
    public void onInbound(Packet<?> packet) {
        int saved = savedContainerId;
        if (saved < 0) return;
        if (packet instanceof ClientboundOpenScreenPacket
            || packet instanceof ClientboundContainerClosePacket close && close.getContainerId() == saved) {
            savedClosed = true;
        }
    }

    /**
     * Called from ContainerScreenMixin for clicks the player makes on a container screen.
     */
    public void onSlotClicked(int slotId, int button, ContainerInput input) {
        if (!recording) return;
        long now = System.nanoTime();
        long gap = recorded.isEmpty() ? 0 : now - lastClickNanos;
        lastClickNanos = now;
        recorded.add(new RecordedClick(slotId, button, input, gap));
    }

    private void saveGui(boolean close) {
        Screen screen = mc.gui.screen();
        if (mc.player == null || screen == null) {
            say("<error>No GUI open to save.");
            return;
        }
        savedScreen = screen;
        savedPlayer = mc.player;
        savedMenu = mc.player.containerMenu;
        savedTitle = screen.getTitle().getString();
        savedClosed = false;
        savedContainerId = savedMenu.containerId;
        if (close) mc.gui.setScreen(null);
        say(close ? "<off_white>Closed <highlight><title></highlight> without telling the server."
                : "<off_white>Saved <highlight><title></highlight>.",
            Placeholder.unparsed("title", savedTitle));
    }

    private void restoreGui() {
        if (savedScreen == null || mc.player == null) {
            say("<error>No saved GUI.");
            return;
        }
        // A respawn or dimension change replaces the player, and the saved menu still points at the old one.
        if (savedPlayer != mc.player) {
            clearSaved();
            say("<error>Dropped the saved GUI. It was opened before a respawn or dimension change.");
            return;
        }
        boolean closed = savedClosed;
        String title = savedTitle;
        mc.gui.setScreen(savedScreen);
        mc.player.containerMenu = savedMenu;
        clearSaved();
        say(closed ? "<warning>Restored <highlight><title></highlight>, but the server already closed it."
                : "<off_white>Restored <highlight><title></highlight>.",
            Placeholder.unparsed("title", title));
    }

    private void clearSaved() {
        savedScreen = null;
        savedPlayer = null;
        savedMenu = null;
        savedTitle = "";
        savedContainerId = -1;
        savedClosed = false;
    }

    private void desync() {
        var connection = mc.getConnection();
        if (mc.player == null || connection == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == mc.player.inventoryMenu) {
            say("<error>No container open to desync.");
            return;
        }
        connection.send(new ServerboundContainerClosePacket(menu.containerId));
        say("<off_white>Told the server container <highlight><id></highlight> closed.",
            Placeholder.unparsed("id", String.valueOf(menu.containerId)));
    }

    private void flushAndDisconnect() {
        if (mc.getConnection() == null) return;
        UIPacketDelay.release();
        // Wait a tick so the released packets go out before the channel closes.
        disconnectNextTick = true;
    }

    private void moveAll(boolean fromContainer) {
        if (mc.player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == mc.player.inventoryMenu) return;
        var inventory = mc.player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.hasItem() && (slot.container != inventory) == fromContainer) {
                mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
            }
        }
    }

    private void copyAsJson(AbstractContainerScreen<?> screen) {
        if (mc.level == null) return;
        var ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        JsonObject root = new JsonObject();
        root.addProperty("title", screen.getTitle().getString());
        root.addProperty("containerId", screen.getMenu().containerId);
        root.addProperty("stateId", screen.getMenu().getStateId());
        JsonArray slots = new JsonArray();
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", slot.index);
            entry.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            entry.addProperty("count", stack.getCount());
            ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(tag -> entry.addProperty("nbt", tag.toString()));
            slots.add(entry);
        }
        root.add("slots", slots);
        mc.keyboardHandler.setClipboard(new GsonBuilder().setPrettyPrinting().create().toJson(root));
        say("<off_white>Copied <highlight><count></highlight> slots as JSON.",
            Placeholder.unparsed("count", String.valueOf(slots.size())));
    }

    private void startReplay() {
        if (recorded.isEmpty() || mc.player == null) return;
        recording = false;
        replayIndex = 0;
        nextReplayNanos = System.nanoTime();
    }

    private void stepReplay() {
        if (mc.player == null || mc.gameMode == null) {
            replayIndex = -1;
            return;
        }
        long now = System.nanoTime();
        long fixedGap = replayDelayMs.get() * 1_000_000L;
        while (replayIndex < recorded.size() && now >= nextReplayNanos) {
            RecordedClick click = recorded.get(replayIndex++);
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,
                click.slot(), click.button(), click.input(), mc.player);
            if (replayIndex < recorded.size()) {
                nextReplayNanos += fixedGap > 0 ? fixedGap : recorded.get(replayIndex).gapNanos();
            }
        }
        if (replayIndex >= recorded.size()) replayIndex = -1;
    }

    private void say(String text, TagResolver... resolvers) {
        var component = resolvers.length == 0 ? MiniMessage.format(text) : MiniMessage.format(text, resolvers);
        MessageUtils.sendMessage(mc, Component.empty().append(MinecraftClientAudiences.of().asNative(component)));
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        if (mc.player == null) {
            ImGuiUtil.textDisabled("Not in a world.");
            return;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        Screen screen = mc.gui.screen();

        ImGui.separator();
        if (menu.containerId != labelContainerId || menu.getStateId() != labelStateId) {
            labelContainerId = menu.containerId;
            labelStateId = menu.getStateId();
            containerLabel = "Container " + labelContainerId + ", state " + labelStateId;
        }
        ImGui.textUnformatted(containerLabel);

        if (ImGui.button("Save & Close##ctSaveClose")) saveGui(true);
        if (ImGui.isItemHovered()) ImGuiUtil.setTooltip("Close the GUI client-side only, so the server keeps it open");
        ImGui.sameLine();
        if (ImGui.button("Save##ctSave")) saveGui(false);
        ImGui.sameLine();
        if (ImGui.button("Restore##ctRestore")) restoreGui();
        ImGui.sameLine();
        if (ImGui.button("Clear##ctClear")) clearSaved();
        if (savedScreen != null) {
            ImGuiUtil.textDisabled("Saved: " + savedTitle + (savedClosed ? " (closed server-side)" : ""));
        }

        if (ImGui.button("Desync##ctDesync")) desync();
        if (ImGui.isItemHovered()) ImGuiUtil.setTooltip("Send a close packet but keep the GUI on screen");
        ImGui.sameLine();
        if (ImGui.button("Flush & Disconnect##ctFlushDc")) flushAndDisconnect();
        if (ImGui.isItemHovered()) ImGuiUtil.setTooltip("Send every held UI packet, then disconnect");

        if (ImGui.checkbox("Hold UI Packets##ctHold", SageFangConfig.isDelayUIPackets())) {
            SageFangConfig.setDelayUIPackets(!SageFangConfig.isDelayUIPackets());
        }
        int queued = UIPacketDelay.size();
        if (queued > 0) {
            ImGui.sameLine();
            if (ImGui.button("Send " + queued + "##ctHoldSend")) UIPacketDelay.release();
            ImGui.sameLine();
            if (ImGui.button("Drop##ctHoldDrop")) UIPacketDelay.clear();
        }

        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            if (ImGui.button("Steal All##ctSteal")) moveAll(true);
            ImGui.sameLine();
            if (ImGui.button("Dump All##ctDump")) moveAll(false);
            ImGui.sameLine();
            if (ImGui.button("Copy JSON##ctJson")) copyAsJson(containerScreen);
        }

        ImGui.setNextItemWidth(80);
        ImGui.inputInt("##ctButtonId", buttonId);
        ImGui.sameLine();
        if (ImGui.button("Button Click##ctButton") && mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, buttonId.get());
        }
        if (ImGui.isItemHovered())
            ImGuiUtil.setTooltip("Send a container button click, as enchanting tables and stonecutters do");

        ImGui.setNextItemWidth(220);
        if (ImGui.inputText("Chat##ctChat", commandInput, ImGuiInputTextFlags.EnterReturnsTrue)) {
            String text = commandInput.get().trim();
            if (!text.isEmpty()) MessageUtils.sendAsPlayer(text);
            commandInput.set("");
        }

        ImGui.separator();
        if (ImGui.button((recording ? "Stop" : "Record") + "##ctRecord")) {
            if (!recording) {
                recorded.clear();
                replayIndex = -1;
            }
            recording = !recording;
        }
        ImGui.sameLine();
        if (ImGui.button("Replay##ctReplay")) startReplay();
        ImGui.sameLine();
        ImGui.textUnformatted(recorded.size() + " clicks" + (replayIndex >= 0 ? ", replaying" : ""));
    }
}
