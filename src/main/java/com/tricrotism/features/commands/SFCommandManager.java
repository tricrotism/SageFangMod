package com.tricrotism.features.commands;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.tricrotism.api.text.MiniMessage;
import com.tricrotism.modules.macros.ChatMacros;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.world.entity.player.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.fabric.FabricClientCommandManager;

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

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;

/**
 * Central command registration. All commands live under /sagefang.
 * Uses Cloud command framework via {@link FabricClientCommandManager}.
 */
public class SFCommandManager {

    private static final Path OUTPUT_PATH = Paths.get("profiles.json");
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new Gson();

    /**
     * All command classes to register under /sagefang.
     */
    private static final SFCommand[] COMMANDS = {
        new ClickSlotCommand(),
        new RepeatCommand(),
        new WaitCommand(),
        new RepeatDelayCommand(),
        new ForEachPlayerCommand(),
        new PluginsCommand(),
        new LabyFriendCommand(),
        ChatMacros.instance,
    };

    /**
     * Create the Cloud command manager and register all commands.
     * Called once during mod initialization.
     */
    public static void init() {
        CommandManager<FabricClientCommandSource> manager =
            FabricClientCommandManager.createNative(ExecutionCoordinator.simpleCoordinator());

        Command.Builder<FabricClientCommandSource> root = manager.commandBuilder("sagefang");

        manager.command(root.literal("minimessage")
            .required("text", greedyStringParser())
            .handler(ctx -> {
                net.kyori.adventure.text.Component component = MiniMessage.format(ctx.get("text"));
                Component nativeComponent = MinecraftClientAudiences.of().asNative(component);
                int componentWidth = Minecraft.getInstance().font.width(nativeComponent);
                ctx.sender().sendFeedback(nativeComponent);
                ctx.sender().sendFeedback(Component.literal("width: " + componentWidth));
            }));

        manager.command(root.literal("saveskins")
            .handler(ctx -> saveSkinsContext()));

        manager.command(root.literal("spam")
            .required("target", greedyStringParser())
            .handler(ctx -> {
                String buildString = ctx.<String>get("target") + " " + "a".repeat(32000);
                if (Minecraft.getInstance().getConnection() != null) {
                    Minecraft.getInstance().getConnection().send(new ServerboundChatCommandPacket(buildString));
                }
            }));

        for (SFCommand cmd : COMMANDS) {
            cmd.register(manager, root);
        }
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
