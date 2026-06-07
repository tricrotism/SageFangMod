package com.tricrotism.features.commands;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.DefaultValue;
import org.incendo.cloud.context.CommandContext;

import static org.incendo.cloud.parser.standard.EnumParser.enumParser;
import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;

public class ClickSlotCommand implements SFCommand {

    @Override
    public void register(CommandManager<FabricClientCommandSource> manager,
                         Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("clickslot")
            .required("slot", integerParser())
            .required("button", integerParser())
            .required("action", enumParser(ContainerInput.class))
            .optional("times", integerParser(1), DefaultValue.constant(1))
            .handler(this::execute)
        );
    }

    private void execute(CommandContext<FabricClientCommandSource> ctx) {
        int times = ctx.<Integer>get("times");
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) {
            ctx.sender().sendError(Component.literal("Not in a game."));
            return;
        }

        int slot = ctx.<Integer>get("slot");
        int button = ctx.<Integer>get("button");
        ContainerInput action = ctx.get("action");
        int containerId = mc.player.containerMenu.containerId;

        for (int i = 0; i < times; i++) {
            mc.gameMode.handleContainerInput(containerId, slot, button, action, mc.player);
        }

        ctx.sender().sendFeedback(Component.literal(
            "Clicked slot " + slot + " (button=" + button + ", action=" + action + ") x" + times));
    }
}
