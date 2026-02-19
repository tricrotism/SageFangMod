package com.tricrotism.features.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tricrotism.utils.ScheduledTaskRunner;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class WaitCommand implements SFCommand {

    @Override
    public void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("wait")
                .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(1))
                        .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    int ms = IntegerArgumentType.getInteger(ctx, "ms");
                                    String command = StringArgumentType.getString(ctx, "command");

                                    ScheduledTaskRunner.schedule(() ->
                                            Minecraft.getInstance().execute(() ->
                                                    RepeatCommand.sendPlayerMsg(command)), ms);

                                    ctx.getSource().sendFeedback(Component.literal(
                                            "Scheduled command in " + ms + "ms."));
                                    return 1;
                                })
                        )
                )
        );
    }
}
