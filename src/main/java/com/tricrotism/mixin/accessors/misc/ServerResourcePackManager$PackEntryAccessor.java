package com.tricrotism.mixin.accessors.misc;

import net.minecraft.client.resources.server.ServerPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;
import java.util.UUID;

@SuppressWarnings("java:S114") // This is irrelevant here
@Mixin(ServerPackManager.ServerPackData.class)
public interface ServerResourcePackManager$PackEntryAccessor {
    @Accessor("path")
    Path getPath();

    @Accessor("id")
    UUID getId();
}