package com.tricrotism.features.commands;

import com.tricrotism.utils.MessageUtils;
import com.tricrotism.utils.ScheduledTaskRunner;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;

public class ForEachPlayerCommand implements SFCommand {

    private static final UUID ZERO_UUID = new UUID(0, 0);
    private static final long DELAY_MS = 100;

    @Override
    public void register(CommandManager<FabricClientCommandSource> manager,
                         Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("foreachplayer")
            .required("command", greedyStringParser())
            .handler(ctx -> {
                Minecraft mc = Minecraft.getInstance();
                var conn = mc.getConnection();
                if (conn == null) {
                    ctx.sender().sendError(Component.literal("Not connected to a server."));
                    return;
                }

                String command = ctx.get("command");
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
                    ctx.sender().sendError(Component.literal("No valid players found."));
                    return;
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
                            MessageUtils.sendAsPlayer(resolved);
                        }), delay);
                }

                ctx.sender().sendFeedback(Component.literal(
                    "Running command for " + players.size() + " player(s)."));
            })
        );
    }
}
