package com.tricrotism.features.commands;

import com.tricrotism.utils.MessageUtils;
import com.tricrotism.utils.ScheduledTaskRunner;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;

public class WaitCommand implements SFCommand {

    @Override
    public void register(CommandManager<FabricClientCommandSource> manager,
                         Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("wait")
            .required("ms", integerParser(1))
            .required("command", greedyStringParser())
            .handler(ctx -> {
                int ms = ctx.get("ms");
                String command = ctx.get("command");

                ScheduledTaskRunner.schedule(() ->
                    Minecraft.getInstance().execute(() ->
                        MessageUtils.sendAsPlayer(command)), ms);

                ctx.sender().sendFeedback(Component.literal(
                    "Scheduled command in " + ms + "ms."));
            })
        );
    }
}
