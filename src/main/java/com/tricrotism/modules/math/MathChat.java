package com.tricrotism.modules.math;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.SendMessageEvent;
import com.tricrotism.utils.MessageUtils;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import io.avaje.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts outgoing chat messages that are math expressions, evaluates
 * them client-side, and displays the result instead of sending the raw
 * expression to the server.
 * <p>
 * Supports {@code + - * / % ^}, parentheses, and the constants
 * {@code pi} and {@code e}. An explicit {@code =} prefix always
 * triggers evaluation; plain expressions like {@code 69 + 420} are
 * auto-detected when {@link #autoDetect} is enabled.
 */
public class MathChat extends Module implements Menu {

    public static final MathChat instance = new MathChat();

    private static final int MAX_HISTORY = 50;
    private static final int COLOR_EXPR = 0xFFBBBBBB;
    private static final int COLOR_RESULT = 0xFF55FF55;
    private static final int COLOR_ERROR = 0xFFFF5555;

    private boolean autoDetect;
    private final List<String> history = new ArrayList<>();

    public MathChat() {
        super("mathchat", "Math Chat", "Evaluate math expressions in chat.", "Utility");
        autoDetect = Config.getBool(baseConfig + ".autoDetect", true);
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!isActive()) return;

        String msg = event.message.trim();
        if (msg.isEmpty()) return;

        boolean explicit = msg.startsWith("=");
        String expr = explicit ? msg.substring(1).trim() : msg;

        if (expr.isEmpty()) return;

        if (!explicit) {
            if (!autoDetect) return;
            if (!MathExprParser.looksLikeMath(expr)) return;
        }

        try {
            double result = MathExprParser.evaluate(expr);
            String formatted = formatResult(result);

            history.addFirst(expr + " = " + formatted);
            if (history.size() > MAX_HISTORY) history.removeLast();

            MessageUtils.sendMessage(mc, Component.empty()
                .append(Component.literal(expr + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("= ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(formatted).withStyle(ChatFormatting.GREEN)));

            event.setCancelled(true);
        } catch (IllegalArgumentException e) {
            if (explicit) {
                MessageUtils.sendMessage(mc, Component.literal("Invalid expression: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
                event.setCancelled(true);
            }
            // Auto-detected but failed to parse → let it through as normal chat
        }
    }

    private String formatResult(double value) {
        if (Double.isInfinite(value)) return "Infinity";
        if (Double.isNaN(value)) return "NaN";
        if (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        // Trim trailing zeros from decimal representation
        String s = String.format("%.10f", value);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        try {
            ImGui.setNextWindowBgAlpha(0.55f);
            ImGui.begin("Math Chat", ImGuiWindowFlags.AlwaysAutoResize);

            if (ImGui.checkbox("Enabled##mathEnabled", isActive())) toggle();

            ImBoolean autoBox = new ImBoolean(autoDetect);
            if (ImGui.checkbox("Auto-detect expressions", autoBox)) {
                autoDetect = autoBox.get();
                Config.setProperty(baseConfig + ".autoDetect", String.valueOf(autoDetect));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("When enabled, expressions like '69 + 420' are evaluated automatically.\n"
                    + "When disabled, prefix with = to evaluate (e.g. '=69 + 420').");
            }

            ImGui.separator();
            ImGui.text("Operators: + - * / %% ^ ( )");
            ImGui.text("Constants: pi, e");
            ImGui.textDisabled("Prefix with = to force evaluation.");

            if (!history.isEmpty()) {
                ImGui.separatorText("History");
                float lineH = ImGui.getTextLineHeightWithSpacing();
                float maxH = lineH * Math.min(history.size(), 10);
                ImGui.beginChild("##mathHistory", 0, maxH, true, ImGuiWindowFlags.None);
                for (String entry : history) {
                    int eqIdx = entry.lastIndexOf(" = ");
                    if (eqIdx > 0) {
                        ImGui.textColored(COLOR_EXPR, entry.substring(0, eqIdx));
                        ImGui.sameLine(0, 0);
                        ImGui.textColored(COLOR_RESULT, " = " + entry.substring(eqIdx + 3));
                    } else {
                        ImGui.text(entry);
                    }
                }
                ImGui.endChild();

                if (ImGui.button("Clear History")) history.clear();
            }

            ImGui.end();
        } catch (Exception e) {
            ImGui.end();
        }
    }
}
