package com.tricrotism.features.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tricrotism.utils.ScheduledTaskRunner;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class RepeatDelayCommand implements SFCommand {

    @Override
    public void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("repeat-delay")
                .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(1))
                        .then(ClientCommandManager.argument("times", IntegerArgumentType.integer(1))
                                .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            int ms = IntegerArgumentType.getInteger(ctx, "ms");
                                            int times = IntegerArgumentType.getInteger(ctx, "times");
                                            String command = StringArgumentType.getString(ctx, "command");

                                            for (int i = 0; i < times; i++) {
                                                final int index = i;
                                                long delay = (long) i * ms;
                                                ScheduledTaskRunner.schedule(() ->
                                                        Minecraft.getInstance().execute(() -> {
                                                            String resolved = command.replaceAll("(?i)%INDEX%", String.valueOf(index));
                                                            RepeatCommand.sendPlayerMsg(resolved);
                                                        }), delay);
                                            }

                                            ctx.getSource().sendFeedback(Component.literal(
                                                    "Repeating command " + times + " time(s) with " + ms + "ms delay."));
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }
}
