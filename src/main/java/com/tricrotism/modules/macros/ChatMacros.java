package com.tricrotism.modules.macros;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.tricrotism.SageFang;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.features.commands.SFCommand;
import com.tricrotism.utils.MessageUtils;
import com.tricrotism.utils.ScheduledTaskRunner;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import io.avaje.config.Config;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatMacros extends Module implements Menu, SFCommand {

    public static final ChatMacros instance = new ChatMacros();

    private static final String CONFIG_KEY = "module.macros.list";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();
    private static final Pattern DELAY_PATTERN = Pattern.compile("^%DELAY:(\\d+)%(.*)$", Pattern.DOTALL);

    private final ImString nameBuffer = new ImString(64);
    private final ImString valueBuffer = new ImString(256);

    public ChatMacros() {
        super("macros", "Chat Macros", "Save and run chat macros.");
    }

    // ── Macro storage ───────────────────────────────────────────────

    /**
     * Loads macros from config, handling backward compatibility with old
     * single-string format ({@code {"name": "value"}}) by wrapping into lists.
     */
    public Map<String, List<String>> getMacros() {
        try {
            String json = Config.get(CONFIG_KEY, "{}");
            // Try to detect and migrate old format
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) return new HashMap<>();

            Map<String, List<String>> result = new HashMap<>();
            boolean needsMigration = false;

            for (var entry : obj.entrySet()) {
                JsonElement el = entry.getValue();
                if (el.isJsonArray()) {
                    List<String> list = new ArrayList<>();
                    for (JsonElement item : el.getAsJsonArray()) {
                        list.add(item.getAsString());
                    }
                    result.put(entry.getKey(), list);
                } else if (el.isJsonPrimitive()) {
                    // Old format: single string value — migrate to list
                    List<String> list = new ArrayList<>();
                    list.add(el.getAsString());
                    result.put(entry.getKey(), list);
                    needsMigration = true;
                }
            }

            if (needsMigration) {
                saveMacros(result);
            }

            return result;
        } catch (Exception e) {
            SageFang.LOGGER.error("Failed to parse macros from config", e);
            return new HashMap<>();
        }
    }

    private void saveMacros(Map<String, List<String>> macros) {
        Config.setProperty(CONFIG_KEY, GSON.toJson(macros));
    }

    public void addMacro(String name, String value) {
        Map<String, List<String>> macros = getMacros();
        macros.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        saveMacros(macros);
    }

    public void removeMacro(String name) {
        Map<String, List<String>> macros = getMacros();
        macros.remove(name);
        saveMacros(macros);
    }

    public void removeValue(String name, int index) {
        Map<String, List<String>> macros = getMacros();
        List<String> values = macros.get(name);
        if (values == null) return;
        if (index < 0 || index >= values.size()) return;
        values.remove(index);
        if (values.isEmpty()) {
            macros.remove(name);
        }
        saveMacros(macros);
    }

    public void runMacro(String name) {
        if (!isActive()) {
            MessageUtils.sendMessage(mc, Component.literal("Chat Macros is not enabled.").withStyle(ChatFormatting.RED));
            return;
        }

        Map<String, List<String>> macros = getMacros();
        List<String> values = macros.get(name);
        if (values == null || values.isEmpty()) {
            MessageUtils.sendMessage(mc, Component.literal("Macro not found: " + name).withStyle(ChatFormatting.RED));
            return;
        }

        for (String msg : values) {
            runSingleValue(msg);
        }
    }

    public void runSingleValue(String msg) {
        Matcher matcher = DELAY_PATTERN.matcher(msg);
        if (matcher.matches()) {
            long delayMs = Long.parseLong(matcher.group(1));
            String actual = matcher.group(2);
            ScheduledTaskRunner.schedule(() -> mc.execute(() -> sendChatOrCommand(actual)), delayMs);
        } else {
            mc.execute(() -> sendChatOrCommand(msg));
        }
    }

    private void sendChatOrCommand(String msg) {
        var conn = mc.getConnection();
        if (conn == null) return;

        if (msg.startsWith("/")) {
            conn.sendCommand(msg.substring(1));
        } else {
            conn.sendChat(msg);
        }
    }

    // ── ImGui menu ──────────────────────────────────────────────────

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        try {
            ImGui.setNextWindowBgAlpha(0.55f);
            ImGui.begin("Chat Macros", ImGuiWindowFlags.AlwaysAutoResize);

            if (ImGui.checkbox("Enabled##macrosEnabled", isActive())) { toggle(); }
            ImGui.separator();

            // Add Macro section
            ImGui.separatorText("Add Macro");
            ImGui.textDisabled("Tip: Use the same name to add multiple values to one macro.");
            ImGui.inputText("Name##macroName", nameBuffer);
            ImGui.inputText("Value##macroValue", valueBuffer);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Prefix with %%DELAY:ms%% for delayed send.\nAdd multiple values to the same macro name to chain messages.");
            }
            if (ImGui.button("Add##macroAdd")) {
                String name = nameBuffer.get().trim();
                String value = valueBuffer.get().trim();
                if (!name.isEmpty() && !value.isEmpty()) {
                    addMacro(name, value);
                    valueBuffer.set("");
                    MessageUtils.sendMessage(mc, Component.literal("Added value to macro: " + name).withStyle(ChatFormatting.GREEN));
                }
            }

            // Macros list section
            ImGui.separatorText("Macros");
            Map<String, List<String>> macros = getMacros();
            if (macros.isEmpty()) {
                ImGui.text("No macros saved.");
            } else {
                String macroToDelete = null;
                String valueToDeleteMacro = null;
                int valueToDeleteIndex = -1;

                for (var entry : macros.entrySet()) {
                    String name = entry.getKey();
                    List<String> values = entry.getValue();

                    if (ImGui.treeNode(name + " (" + values.size() + " value" + (values.size() != 1 ? "s" : "") + ")##macro_" + name)) {
                        // Run All button
                        if (isActive()) {
                            if (ImGui.button("Run All##runAll_" + name)) {
                                runMacro(name);
                            }
                        } else {
                            ImGui.textDisabled("Run All");
                            if (ImGui.isItemHovered()) {
                                ImGui.setTooltip("Enable module to run");
                            }
                        }
                        ImGui.sameLine();
                        if (ImGui.button("Delete Macro##delMacro_" + name)) {
                            macroToDelete = name;
                        }

                        // Individual values
                        for (int i = 0; i < values.size(); i++) {
                            String val = values.get(i);
                            String display = val.length() > 50 ? val.substring(0, 47) + "..." : val;
                            ImGui.text("[" + i + "] " + display);
                            ImGui.sameLine();
                            if (isActive()) {
                                if (ImGui.button("Run##runVal_" + name + "_" + i)) {
                                    runSingleValue(val);
                                }
                            } else {
                                ImGui.textDisabled("Run");
                                if (ImGui.isItemHovered()) {
                                    ImGui.setTooltip("Enable module to run");
                                }
                            }
                            ImGui.sameLine();
                            if (ImGui.button("Delete##delVal_" + name + "_" + i)) {
                                valueToDeleteMacro = name;
                                valueToDeleteIndex = i;
                            }
                        }

                        ImGui.treePop();
                    }
                }

                // Delete outside iteration to avoid ConcurrentModificationException
                if (macroToDelete != null) {
                    removeMacro(macroToDelete);
                }
                if (valueToDeleteMacro != null && valueToDeleteIndex >= 0) {
                    removeValue(valueToDeleteMacro, valueToDeleteIndex);
                }
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in Chat Macros menu", e);
        }
    }

    // ── Command registration ────────────────────────────────────────

    @Override
    public void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("macro")
                .then(ClientCommandManager.literal("run")
                        .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    runMacro(name);
                                    return 1;
                                })
                        )
                )
                .then(ClientCommandManager.literal("list")
                        .executes(ctx -> {
                            Map<String, List<String>> macros = getMacros();
                            if (macros.isEmpty()) {
                                ctx.getSource().sendFeedback(Component.literal("No macros saved.").withStyle(ChatFormatting.YELLOW));
                            } else {
                                ctx.getSource().sendFeedback(Component.literal("Macros:").withStyle(ChatFormatting.GREEN));
                                for (var entry : macros.entrySet()) {
                                    String name = entry.getKey();
                                    List<String> values = entry.getValue();
                                    ctx.getSource().sendFeedback(Component.literal("  " + name + " (" + values.size() + " values):").withStyle(ChatFormatting.AQUA));
                                    for (int i = 0; i < values.size(); i++) {
                                        ctx.getSource().sendFeedback(Component.literal("    [" + i + "] " + values.get(i)).withStyle(ChatFormatting.GRAY));
                                    }
                                }
                            }
                            return 1;
                        })
                )
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String value = StringArgumentType.getString(ctx, "value");
                                            addMacro(name, value);
                                            ctx.getSource().sendFeedback(Component.literal("Added value to macro: " + name).withStyle(ChatFormatting.GREEN));
                                            return 1;
                                        })
                                )
                        )
                )
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    removeMacro(name);
                                    ctx.getSource().sendFeedback(Component.literal("Macro removed: " + name).withStyle(ChatFormatting.GREEN));
                                    return 1;
                                })
                        )
                )
        );
    }
}
