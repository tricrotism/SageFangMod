package com.tricrotism.features.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Interface for individual commands registered under /sagefang.
 */
public interface SFCommand {
    /**
     * Register this command's argument tree as a child of the /sagefang root.
     */
    void register(LiteralArgumentBuilder<FabricClientCommandSource> root);
}
