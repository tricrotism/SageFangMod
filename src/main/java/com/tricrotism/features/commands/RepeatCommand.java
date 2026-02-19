package com.tricrotism.features.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class RepeatCommand implements SFCommand {

    @Override
    public void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("repeat")
                .then(ClientCommandManager.argument("times", IntegerArgumentType.integer(1))
                        .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    int times = IntegerArgumentType.getInteger(ctx, "times");
                                    String command = StringArgumentType.getString(ctx, "command");

                                    for (int i = 0; i < times; i++) {
                                        String resolved = command.replaceAll("(?i)%INDEX%", String.valueOf(i));
                                        sendPlayerMsg(resolved);
                                    }

                                    ctx.getSource().sendFeedback(Component.literal(
                                            "Repeating command " + times + " time(s)."));
                                    return 1;
                                })
                        )
                )
        );
    }

    static void sendPlayerMsg(String msg) {
        Minecraft mc = Minecraft.getInstance();
        var conn = mc.getConnection();
        if (conn == null) return;
        if (msg.startsWith("/")) {
            conn.sendCommand(msg.substring(1));
        } else {
            conn.sendChat(msg);
        }
    }
}
