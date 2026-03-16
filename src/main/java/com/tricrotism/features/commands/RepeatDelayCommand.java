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

public class RepeatDelayCommand implements SFCommand {

    @Override
    public void register(CommandManager<FabricClientCommandSource> manager,
                         Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("repeat-delay")
            .required("ms", integerParser(1))
            .required("times", integerParser(1))
            .required("command", greedyStringParser())
            .handler(ctx -> {
                int ms = ctx.<Integer>get("ms");
                int times = ctx.<Integer>get("times");
                String command = ctx.get("command");

                for (int i = 0; i < times; i++) {
                    final int index = i;
                    long delay = (long) i * ms;
                    ScheduledTaskRunner.schedule(() ->
                        Minecraft.getInstance().execute(() -> {
                            String resolved = command.replaceAll("(?i)%INDEX%", String.valueOf(index));
                            MessageUtils.sendAsPlayer(resolved);
                        }), delay);
                }

                ctx.sender().sendFeedback(Component.literal(
                    "Repeating command " + times + " time(s) with " + ms + "ms delay."));
            }));
    }
}
