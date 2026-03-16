package com.tricrotism.features.commands;

import com.tricrotism.utils.MessageUtils;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;

public class RepeatCommand implements SFCommand {

    @Override
    public void register(CommandManager<FabricClientCommandSource> manager,
                         Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("repeat")
            .required("times", integerParser(1))
            .required("command", greedyStringParser())
            .handler(ctx -> {
                int times = ctx.<Integer>get("times");
                String command = ctx.get("command");

                for (int i = 0; i < times; i++) {
                    String resolved = command.replaceAll("(?i)%INDEX%", String.valueOf(i));
                    MessageUtils.sendAsPlayer(resolved);
                }

                ctx.sender().sendFeedback(Component.literal(
                    "Repeating command " + times + " time(s)."));
            })
        );
    }
}
