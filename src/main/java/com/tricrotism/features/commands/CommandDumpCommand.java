package com.tricrotism.features.commands;

import com.mojang.brigadier.tree.CommandNode;
import com.tricrotism.SageFang;
import com.tricrotism.api.text.MiniMessage;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * {@code /sagefang commands [filename]}: writes the server's root command literals, one per line,
 * to {@code sagefang-testlog/commands/}.
 */
public class CommandDumpCommand implements SFCommand {

    private static final Path DIRECTORY = Path.of("sagefang-testlog", "commands");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    public void register(CommandManager<FabricClientCommandSource> manager,
                         Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("commands")
            .optional("filename", stringParser())
            .handler(this::execute));
    }

    private void execute(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            ctx.sender().sendError(MinecraftClientAudiences.of().asNative(
                MiniMessage.format("<error>Not connected to a server.")));
            return;
        }

        List<String> lines = new ArrayList<>();
        for (CommandNode<?> child : connection.getCommands().getRoot().getChildren()) {
            lines.add("/" + child.getName());
        }
        lines.sort(String.CASE_INSENSITIVE_ORDER);

        ServerData server = mc.getCurrentServer();
        String name = ctx.<String>optional("filename")
            .orElseGet(() -> "commands-" + (server == null ? "unknown" : server.ip) + "-" + LocalDateTime.now().format(STAMP));
        name = sanitize(name);
        if (!name.endsWith(".txt")) name += ".txt";
        Path file = DIRECTORY.resolve(name);

        FabricClientCommandSource sender = ctx.sender();
        Thread.ofPlatform().daemon().name("sagefang-command-dump").start(() -> {
            try {
                Files.createDirectories(DIRECTORY);
                Files.write(file, lines);
                String path = file.toAbsolutePath().toString();
                mc.execute(() -> sender.sendFeedback(MinecraftClientAudiences.of().asNative(MiniMessage.format(
                    "<off_white>Wrote <highlight><count></highlight> commands to <highlight><path></highlight>.",
                    Placeholder.unparsed("count", String.valueOf(lines.size())),
                    Placeholder.unparsed("path", path)))));
            } catch (IOException e) {
                SageFang.LOGGER.error("Failed to write command dump to {}", file, e);
                mc.execute(() -> sender.sendError(MinecraftClientAudiences.of().asNative(MiniMessage.format(
                    "<error>Could not write <highlight><path></highlight>: <reason>",
                    Placeholder.unparsed("path", file.toString()),
                    Placeholder.unparsed("reason", String.valueOf(e.getMessage()))))));
            }
        });
    }

    /**
     * Replaces path separators and drive colons and strips {@code ..}, so the file stays inside
     * {@link #DIRECTORY}.
     */
    private static String sanitize(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_").replace("..", "");
        return cleaned.isEmpty() ? "commands" : cleaned;
    }
}
