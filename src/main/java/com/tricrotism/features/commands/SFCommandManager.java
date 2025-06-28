package com.tricrotism.features.commands;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SFCommandManager {

    private static final Path OUTPUT_PATH = Paths.get("profiles.json");
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new Gson();

    private SFCommandManager() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext access) {
        dispatcher.register(ClientCommandManager.literal("sagefang")
                .then(ClientCommandManager.literal("minimessage").then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                                    Component component = MiniMessage.miniMessage().deserialize(StringArgumentType.getString(ctx, "text"));
                                    net.minecraft.network.chat.Component nativeComponent = MinecraftClientAudiences.of().asNative(component);
                                    int componentWidth = Minecraft.getInstance().font.width(nativeComponent);

                                    ctx.getSource().sendFeedback(nativeComponent);
                                    ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("width: " + componentWidth));
                                    return 1;
                                }
                        )
                ))
                .then(ClientCommandManager.literal("saveskins")
                        .executes(ctx -> {
                            saveSkinsContext();
                            return 1;
                        }))
        );
    }

    private static void saveSkinsContext() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        ArrayList<JsonArray> existingEntries = new ArrayList<>();
        Set<String> existingUUIDs = new HashSet<>();

        if (Files.exists(OUTPUT_PATH)) {
            try (Reader reader = Files.newBufferedReader(OUTPUT_PATH)) {
                JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonArray entry = el.getAsJsonArray();
                    if (entry.size() >= 2) {
                        existingUUIDs.add(entry.get(1).getAsString());
                        existingEntries.add(entry);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (Player player : client.level.players()) {
            GameProfile profile = player.getGameProfile();
            String uuidStr = profile.getId().toString();
            if (existingUUIDs.contains(uuidStr)) continue;

            Collection<Property> textures = profile.getProperties().get("textures");
            JsonArray texturesArr = new JsonArray();
            for (Property prop : textures) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", prop.name());
                obj.addProperty("value", prop.value());
                obj.addProperty("signature", prop.signature());
                texturesArr.add(obj);
            }

            JsonArray playerData = new JsonArray();
            playerData.add(profile.getName());
            playerData.add(uuidStr);
            playerData.add(GSON_COMPACT.toJson(texturesArr));

            existingEntries.add(playerData);
        }

        // Write new data
        try (Writer writer = Files.newBufferedWriter(OUTPUT_PATH)) {
            JsonArray toWrite = new JsonArray();
            for (JsonArray entry : existingEntries) toWrite.add(entry);
            GSON_PRETTY.toJson(toWrite, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
