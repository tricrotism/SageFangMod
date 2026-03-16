package com.tricrotism.features.commands;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Detects server plugins via two methods:
 * <ol>
 *   <li>Parsing the command tree for namespaced commands (e.g. {@code essentials:home} → "Essentials")</li>
 *   <li>Tab-completing {@code /version} to list all Bukkit plugins</li>
 * </ol>
 */
public class PluginsCommand implements SFCommand {

    /**
     * Namespaces that are not plugins.
     */
    private static final Set<String> EXCLUDED = Set.of("minecraft", "bukkit", "brigadier");

    private static final AtomicReference<Consumer<ClientboundCommandSuggestionsPacket>> pendingCallback =
        new AtomicReference<>();
    private static volatile int pendingTxId = -1;

    @Override
    public void register(CommandManager<FabricClientCommandSource> manager,
                         Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("plugins")
            .handler(ctx -> detectPlugins(ctx.sender())));
    }

    private void detectPlugins(FabricClientCommandSource source) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            source.sendError(Component.literal("Not connected to a server."));
            return;
        }

        // ── Method 1: Parse command tree for namespaced commands ──────────
        Set<String> fromTree = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (CommandNode<?> child : connection.getCommands().getRoot().getChildren()) {
            String name = child.getName();
            int colon = name.indexOf(':');
            if (colon > 0) {
                String ns = name.substring(0, colon).toLowerCase();
                if (!EXCLUDED.contains(ns)) {
                    fromTree.add(capitalize(ns));
                }
            }
        }
        displaySection(source, "Command Tree", fromTree);

        // ── Method 2: Tab-complete "version " to discover all plugins ────
        int txId = ThreadLocalRandom.current().nextInt(0x1000, 0x7FFF);
        pendingTxId = txId;
        pendingCallback.set(packet -> {
            Set<String> fromVersion = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (var entry : packet.suggestions()) {
                String text = entry.text();
                if (!text.isBlank()) fromVersion.add(text);
            }
            mc.execute(() -> displaySection(source, "/version", fromVersion));
        });
        connection.send(new ServerboundCommandSuggestionPacket(txId, "version "));
        source.sendFeedback(Component.literal("Querying /version tab-complete...")
            .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Called from {@link com.tricrotism.mixin.impl.network.ConnectionMixin} on inbound
     * suggestion packets. Dispatches to the pending callback if the transaction ID matches.
     */
    public static void onSuggestionsPacket(ClientboundCommandSuggestionsPacket packet) {
        if (packet.id() != pendingTxId) return;
        Consumer<ClientboundCommandSuggestionsPacket> cb = pendingCallback.getAndSet(null);
        pendingTxId = -1;
        if (cb != null) cb.accept(packet);
    }

    // ── Formatting ───────────────────────────────────────────────────────

    private void displaySection(FabricClientCommandSource source, String method, Set<String> plugins) {
        if (plugins.isEmpty()) {
            source.sendFeedback(Component.empty()
                .append(Component.literal("[" + method + "] ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("No plugins detected.").withStyle(ChatFormatting.RED)));
            return;
        }

        MutableComponent header = Component.empty()
            .append(Component.literal("[" + method + "] ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(plugins.size() + " plugin(s): ").withStyle(ChatFormatting.GREEN));

        MutableComponent list = Component.empty();
        boolean first = true;
        for (String plugin : plugins) {
            if (!first) list.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            list.append(Component.literal(plugin).withStyle(ChatFormatting.WHITE));
            first = false;
        }

        source.sendFeedback(header.append(list));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
