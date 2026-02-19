package com.tricrotism.features.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tricrotism.utils.ScheduledTaskRunner;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ForEachPlayerCommand implements SFCommand {

    private static final UUID ZERO_UUID = new UUID(0, 0);
    private static final long DELAY_MS = 100;

    @Override
    public void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("foreachplayer")
                .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            var conn = mc.getConnection();
                            if (conn == null) {
                                ctx.getSource().sendFeedback(Component.literal("§cNot connected to a server."));
                                return 0;
                            }

                            String command = StringArgumentType.getString(ctx, "command");
                            boolean singleplayer = mc.isLocalServer();
                            List<PlayerInfo> players = new ArrayList<>();

                            for (PlayerInfo info : conn.getListedOnlinePlayers()) {
                                if (info.getProfile() == null) continue;
                                String name = info.getProfile().name();
                                if (name == null || name.isBlank()) continue;
                                if (name.contains("§")) continue;
                                if (ZERO_UUID.equals(info.getProfile().id())) continue;
                                if (!singleplayer && info.getLatency() == 0) continue;
                                players.add(info);
                            }

                            if (players.isEmpty()) {
                                ctx.getSource().sendFeedback(Component.literal("§cNo valid players found."));
                                return 0;
                            }

                            for (int i = 0; i < players.size(); i++) {
                                final int index = i;
                                PlayerInfo info = players.get(i);
                                long delay = (long) i * DELAY_MS;

                                ScheduledTaskRunner.schedule(() ->
                                        Minecraft.getInstance().execute(() -> {
                                            String resolved = command
                                                    .replaceAll("(?i)%PLAYER%", info.getProfile().name())
                                                    .replaceAll("(?i)%PLAYER_UUID%", info.getProfile().id().toString())
                                                    .replaceAll("(?i)%INDEX%", String.valueOf(index));
                                            RepeatCommand.sendPlayerMsg(resolved);
                                        }), delay);
                            }

                            ctx.getSource().sendFeedback(Component.literal(
                                    "Running command for " + players.size() + " player(s)."));
                            return 1;
                        })
                )
        );
    }
}
