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
import imgui.type.ImInt;
import imgui.type.ImString;
import io.avaje.config.Config;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

public class ChatMacros extends Module implements Menu, SFCommand {

    public static final ChatMacros instance = new ChatMacros();

    private static final String CONFIG_KEY = "module.macros.list";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();
    private static final Pattern DELAY_PATTERN = Pattern.compile("^%DELAY:(\\d+)%(.*)$", Pattern.DOTALL);

    private final ImString nameBuffer = new ImString(64);
    private final ImString valueBuffer = new ImString(256);
    private final ImInt delayBuffer = new ImInt(0);
    private static final int MAX_VISIBLE_MACROS = 12;
    private int commandInterval;

    // Cached macro map — loaded once from config, written back only on mutation.
    // Eliminates per-frame JSON parsing that caused lag with many entries.
    private Map<String, List<String>> cachedMacros;

    public ChatMacros() {
        super("macros", "Chat Macros", "Save and run chat macros.", "Chat");
        commandInterval = Config.getInt(baseConfig + ".interval", 100);
    }


    /**
     * Returns the cached macro map. Loads from config on first access.
     */
    public Map<String, List<String>> getMacros() {
        if (cachedMacros == null) {
            cachedMacros = loadMacrosFromConfig();
        }
        return cachedMacros;
    }

    /**
     * Parses macros from config, handling backward compatibility with old
     * single-string format ({@code {"name": "value"}}) by wrapping into lists.
     */
    private Map<String, List<String>> loadMacrosFromConfig() {
        try {
            String json = Config.get(CONFIG_KEY, "{}");
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
                    List<String> list = new ArrayList<>();
                    list.add(el.getAsString());
                    result.put(entry.getKey(), list);
                    needsMigration = true;
                }
            }

            if (needsMigration) {
                persistMacros(result);
            }

            return result;
        } catch (Exception e) {
            SageFang.LOGGER.error("Failed to parse macros from config", e);
            return new HashMap<>();
        }
    }

    private void persistMacros(Map<String, List<String>> macros) {
        Config.setProperty(CONFIG_KEY, GSON.toJson(macros));
    }

    /** Mutates the cached map and writes through to config. */
    private void saveMacros() {
        persistMacros(getMacros());
    }

    public void addMacro(String name, String value) {
        getMacros().computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        saveMacros();
    }

    public void removeMacro(String name) {
        getMacros().remove(name);
        saveMacros();
    }

    public void removeValue(String name, int index) {
        List<String> values = getMacros().get(name);
        if (values == null) return;
        if (index < 0 || index >= values.size()) return;
        values.remove(index);
        if (values.isEmpty()) {
            getMacros().remove(name);
        }
        saveMacros();
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

        for (int i = 0; i < values.size(); i++) {
            String msg = values.get(i);
            long stagger = (long) i * commandInterval;
            Matcher matcher = DELAY_PATTERN.matcher(msg);
            if (matcher.matches()) {
                long cmdDelay = Long.parseLong(matcher.group(1));
                String actual = matcher.group(2);
                ScheduledTaskRunner.schedule(() -> mc.execute(() -> sendChatOrCommand(actual)), stagger + cmdDelay);
            } else if (stagger > 0) {
                ScheduledTaskRunner.schedule(() -> mc.execute(() -> sendChatOrCommand(msg)), stagger);
            } else {
                mc.execute(() -> sendChatOrCommand(msg));
            }
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

    private String formatMacroValue(String val) {
        Matcher m = DELAY_PATTERN.matcher(val);
        String display;
        if (m.matches()) {
            display = m.group(2) + " [" + m.group(1) + "ms]";
        } else {
            display = val;
        }
        return display.length() > 50 ? display.substring(0, 47) + "..." : display;
    }


    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        try {
            ImGui.setNextWindowBgAlpha(0.55f);
            ImGui.begin("Chat Macros", ImGuiWindowFlags.AlwaysAutoResize);

            if (ImGui.checkbox("Enabled##macrosEnabled", isActive())) {
                toggle();
            }
            ImGui.separator();

            int[] intervalArr = {commandInterval};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderInt("Command Interval (ms)##macroInterval", intervalArr, 0, 2000)) {
                commandInterval = Math.max(0, intervalArr[0]);
                Config.setProperty(baseConfig + ".interval", String.valueOf(commandInterval));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Delay between each command in a macro chain.\n0 = all instant. 100 = 100ms between each.\nFor 28 commands at 100ms: ~2.7s total.");
            }


            ImGui.separatorText("Add Macro");
            ImGui.textDisabled("Tip: Use the same name to chain multiple messages.");
            ImGui.inputText("Name##macroName", nameBuffer);
            ImGui.inputText("Value##macroValue", valueBuffer);
            ImGui.setNextItemWidth(120);
            ImGui.inputInt("Delay (ms)##macroDelay", delayBuffer, 100, 500);
            if (delayBuffer.get() < 0) delayBuffer.set(0);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Delay before sending this message.\n0 = send immediately.");
            }
            if (ImGui.button("Add##macroAdd")) {
                String name = nameBuffer.get().trim();
                String value = valueBuffer.get().trim();
                if (!name.isEmpty() && !value.isEmpty()) {
                    String stored = delayBuffer.get() > 0
                        ? "%DELAY:" + delayBuffer.get() + "%" + value
                        : value;
                    addMacro(name, stored);
                    valueBuffer.set("");
                    delayBuffer.set(0);
                    MessageUtils.sendMessage(mc, Component.literal("Added value to macro: " + name).withStyle(ChatFormatting.GREEN));
                }
            }

            ImGui.separatorText("Macros");
            Map<String, List<String>> macros = getMacros();
            if (macros.isEmpty()) {
                ImGui.text("No macros saved.");
            } else {
                float lineH = ImGui.getTextLineHeightWithSpacing();
                float maxH = lineH * MAX_VISIBLE_MACROS;
                ImGui.beginChild("##macroList", 0, maxH, true, ImGuiWindowFlags.None);

                String macroToDelete = null;
                String valueToDeleteMacro = null;
                int valueToDeleteIndex = -1;

                for (var entry : macros.entrySet()) {
                    String name = entry.getKey();
                    List<String> values = entry.getValue();

                    if (ImGui.treeNode(name + " (" + values.size() + " value" + (values.size() != 1 ? "s" : "") + ")##macro_" + name)) {
                        if (isActive()) {
                            if (ImGui.button("Run All##runAll_" + name)) {
                                runMacro(name);
                            }
                        } else {
                            ImGui.textDisabled("Run All");
                            if (ImGui.isItemHovered()) ImGui.setTooltip("Enable module to run");
                        }
                        ImGui.sameLine();
                        if (ImGui.button("Delete Macro##delMacro_" + name)) {
                            macroToDelete = name;
                        }

                        for (int i = 0; i < values.size(); i++) {
                            String val = values.get(i);
                            String display = formatMacroValue(val);
                            ImGui.text("[" + i + "] " + display);
                            ImGui.sameLine();
                            if (isActive()) {
                                if (ImGui.button("Run##runVal_" + name + "_" + i)) {
                                    runSingleValue(val);
                                }
                            } else {
                                ImGui.textDisabled("Run");
                                if (ImGui.isItemHovered()) ImGui.setTooltip("Enable module to run");
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

                ImGui.endChild();

                if (macroToDelete != null) removeMacro(macroToDelete);
                if (valueToDeleteMacro != null && valueToDeleteIndex >= 0)
                    removeValue(valueToDeleteMacro, valueToDeleteIndex);
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in Chat Macros menu", e);
        }
    }


    @Override
    public void register(CommandManager<FabricClientCommandSource> manager, Command.Builder<FabricClientCommandSource> root) {
        manager.command(root.literal("macro").literal("run")
            .required("name", stringParser())
            .handler(ctx -> runMacro(ctx.get("name"))));

        manager.command(root.literal("macro").literal("list")
            .handler(ctx -> {
                Map<String, List<String>> macros = getMacros();
                if (macros.isEmpty()) {
                    ctx.sender().sendFeedback(Component.literal("No macros saved.").withStyle(ChatFormatting.YELLOW));
                } else {
                    ctx.sender().sendFeedback(Component.literal("Macros:").withStyle(ChatFormatting.GREEN));
                    for (var entry : macros.entrySet()) {
                        String name = entry.getKey();
                        List<String> values = entry.getValue();
                        ctx.sender().sendFeedback(Component.literal("  " + name + " (" + values.size() + " values):").withStyle(ChatFormatting.AQUA));
                        for (int i = 0; i < values.size(); i++) {
                            ctx.sender().sendFeedback(Component.literal("    [" + i + "] " + values.get(i)).withStyle(ChatFormatting.GRAY));
                        }
                    }
                }
            }));

        manager.command(root.literal("macro").literal("add")
            .required("name", stringParser())
            .required("value", greedyStringParser())
            .handler(ctx -> {
                String name = ctx.get("name");
                String value = ctx.get("value");
                addMacro(name, value);
                ctx.sender().sendFeedback(Component.literal("Added value to macro: " + name).withStyle(ChatFormatting.GREEN));
            }));

        manager.command(root.literal("macro").literal("remove")
            .required("name", stringParser())
            .handler(ctx -> {
                String name = ctx.get("name");
                removeMacro(name);
                ctx.sender().sendFeedback(Component.literal("Macro removed: " + name).withStyle(ChatFormatting.GREEN));
            }));
    }
}
