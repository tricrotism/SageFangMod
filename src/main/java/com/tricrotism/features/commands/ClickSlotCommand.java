package com.tricrotism.features.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;

public class ClickSlotCommand implements SFCommand {

    @Override
    public void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("clickslot")
                .then(ClientCommandManager.argument("slot", IntegerArgumentType.integer())
                        .then(ClientCommandManager.argument("button", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("action", EnumArgumentType.enumArgument(ClickType.PICKUP))
                                        .executes(ctx -> execute(ctx, 1))
                                )
                                .then(ClientCommandManager.argument("times", IntegerArgumentType.integer(1))
                                        .then(ClientCommandManager.argument("action", EnumArgumentType.enumArgument(ClickType.PICKUP))
                                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "times")))
                                        )
                                )
                        )
                )
        );
    }

    private int execute(CommandContext<FabricClientCommandSource> ctx, int times) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) {
            ctx.getSource().sendFeedback(Component.literal("§cNot in a game."));
            return 0;
        }

        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        int button = IntegerArgumentType.getInteger(ctx, "button");
        ClickType action = ctx.getArgument("action", ClickType.class);
        int containerId = mc.player.containerMenu.containerId;

        for (int i = 0; i < times; i++) {
            mc.gameMode.handleInventoryMouseClick(containerId, slot, button, action, mc.player);
        }

        ctx.getSource().sendFeedback(Component.literal(
                "Clicked slot " + slot + " (button=" + button + ", action=" + action + ") x" + times));
        return 1;
    }
}
