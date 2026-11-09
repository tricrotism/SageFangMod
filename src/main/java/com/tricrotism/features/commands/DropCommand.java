package com.tricrotism.features.commands;

import com.tricrotism.api.text.MiniMessage;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;

/**
 * {@code /sagefang drop [slot]}: throws the stack in an inventory slot (0-8 hotbar, 9-35 main),
 * or the held stack when no slot is given.
 */
public class DropCommand implements SFCommand {

    private static final int THROW_STACK = 1;

    @Override
    public void register(CommandManager<FabricClientCommandSource> manager,
                         Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("drop")
            .optional("slot", integerParser(0, Inventory.INVENTORY_SIZE - 1))
            .handler(this::execute));
    }

    private void execute(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            ctx.sender().sendError(MinecraftClientAudiences.of().asNative(MiniMessage.format("<error>Not in a game.")));
            return;
        }

        int slot = ctx.<Integer>optional("slot").orElse(mc.player.getInventory().getSelectedSlot());
        int menuSlot = slot < Inventory.SELECTION_SIZE ? slot + InventoryMenu.USE_ROW_SLOT_START : slot;
        InventoryMenu menu = mc.player.inventoryMenu;

        if (menu.getSlot(menuSlot).getItem().isEmpty()) {
            ctx.sender().sendError(MinecraftClientAudiences.of().asNative(MiniMessage.format(
                "<error>Slot <highlight><slot></highlight> is empty.",
                Placeholder.unparsed("slot", String.valueOf(slot)))));
            return;
        }

        mc.gameMode.handleContainerInput(menu.containerId, menuSlot, THROW_STACK, ContainerInput.THROW, mc.player);
        ctx.sender().sendFeedback(MinecraftClientAudiences.of().asNative(MiniMessage.format(
            "<off_white>Dropped slot <highlight><slot></highlight>.",
            Placeholder.unparsed("slot", String.valueOf(slot)))));
    }
}
