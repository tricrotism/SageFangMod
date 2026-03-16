package com.tricrotism.features.commands;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;

/**
 * Interface for individual commands registered under /sagefang.
 */
public interface SFCommand {
    /**
     * Register this command's argument tree using the Cloud command manager.
     *
     * @param manager the Cloud command manager
     * @param root    a pre-built {@code /sagefang} base builder to chain from
     */
    void register(CommandManager<FabricClientCommandSource> manager,
                  Command.Builder<FabricClientCommandSource> root);
}
