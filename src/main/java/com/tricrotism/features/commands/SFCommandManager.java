package com.tricrotism.features.commands;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.modules.macros.ChatMacros;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
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

/**
 * Central command registration. All commands live under /sagefang.
 * Individual command classes implement {@link SFCommand} and are registered here.
 */
public class SFCommandManager {

    private static final Path OUTPUT_PATH = Paths.get("profiles.json");
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new Gson();

    /** All command classes to register under /sagefang. */
    private static final SFCommand[] COMMANDS = {
            new ClickSlotCommand(),
            new RepeatCommand(),
            new WaitCommand(),
            new RepeatDelayCommand(),
            new ForEachPlayerCommand(),
            ChatMacros.instance,
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext access) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("sagefang");

        // Legacy commands built inline
        root.then(ClientCommandManager.literal("minimessage").then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                .executes(ctx -> {
                    net.kyori.adventure.text.Component component = MiniMessage.format(StringArgumentType.getString(ctx, "text"));
                    net.minecraft.network.chat.Component nativeComponent = MinecraftClientAudiences.of().asNative(component);
                    int componentWidth = Minecraft.getInstance().font.width(nativeComponent);
                    ctx.getSource().sendFeedback(nativeComponent);
                    ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("width: " + componentWidth));
                    return 1;
                })
        ));

        root.then(ClientCommandManager.literal("saveskins")
                .executes(ctx -> {
                    saveSkinsContext();
                    return 1;
                })
        );

        root.then(ClientCommandManager.literal("spam").then(ClientCommandManager.argument("target", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String buildString = StringArgumentType.getString(ctx, "target") + " ";
                    buildString = buildString + "a".repeat(32000);
                    if (Minecraft.getInstance().getConnection() != null) {
                        Minecraft.getInstance().getConnection().send(new ServerboundChatCommandPacket(buildString));
                    }
                    return 1;
                })
        ));

        // Register all modular commands
        for (SFCommand cmd : COMMANDS) {
            cmd.register(root);
        }

        dispatcher.register(root);
    }

    @SuppressWarnings("D")
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
            String uuidStr = profile.id().toString();
            if (existingUUIDs.contains(uuidStr)) continue;

            Collection<Property> textures = profile.properties().get("textures");
            JsonArray texturesArr = new JsonArray();
            for (Property prop : textures) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", prop.name());
                obj.addProperty("value", prop.value());
                obj.addProperty("signature", prop.signature());
                texturesArr.add(obj);
            }

            JsonArray playerData = new JsonArray();
            playerData.add(profile.name());
            playerData.add(uuidStr);
            playerData.add(GSON_COMPACT.toJson(texturesArr));

            existingEntries.add(playerData);
        }

        try (Writer writer = Files.newBufferedWriter(OUTPUT_PATH)) {
            JsonArray toWrite = new JsonArray();
            for (JsonArray entry : existingEntries) toWrite.add(entry);
            GSON_PRETTY.toJson(toWrite, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
