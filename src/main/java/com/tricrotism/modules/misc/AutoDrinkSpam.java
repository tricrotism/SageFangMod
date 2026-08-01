package com.tricrotism.modules.misc;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Repeatedly attempts to use a drinkable/edible item in hand (potions, food),
 * optionally interrupting the current use each cycle so drinking restarts every
 * tick for maximum spam. Ported from the Meteor addon's auto-drink-spam (the
 * Grim raw-packet path is dropped to keep this self-contained).
 */
public final class AutoDrinkSpam extends Module implements Menu {

    public static final AutoDrinkSpam instance = new AutoDrinkSpam();

    private static final String[] HAND_LABELS = {"Main hand", "Off hand", "Both"};
    private static final String[] FILTER_LABELS = {"Drink", "Eat", "Drink or eat"};

    private int handMode;
    private int filterMode;
    private int delay;
    private boolean interrupt;

    private int tickCounter;

    private AutoDrinkSpam() {
        super("autodrinkspam", "Auto Drink Spam", "Constantly use drinkable/edible items in hand.", "Utility");
        handMode = Config.getInt(baseConfig + ".hand", 0);
        filterMode = Config.getInt(baseConfig + ".filter", 0);
        delay = Config.getInt(baseConfig + ".delay", 0);
        interrupt = Config.getBool(baseConfig + ".interrupt", true);
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        tickCounter++;
        if (tickCounter <= delay) return;
        tickCounter = 0;

        switch (handMode) {
            case 0 -> tryUse(InteractionHand.MAIN_HAND);
            case 1 -> tryUse(InteractionHand.OFF_HAND);
            default -> {
                tryUse(InteractionHand.MAIN_HAND);
                tryUse(InteractionHand.OFF_HAND);
            }
        }
    }

    private void tryUse(InteractionHand hand) {
        ItemStack stack = mc.player.getItemInHand(hand);
        if (stack.isEmpty() || !matchesFilter(stack.getUseAnimation())) return;

        if (interrupt && mc.player.isUsingItem() && mc.player.getUsedItemHand() == hand) {
            mc.player.stopUsingItem();
        }
        mc.gameMode.useItem(mc.player, hand);
    }

    private boolean matchesFilter(ItemUseAnimation anim) {
        return switch (filterMode) {
            case 0 -> anim == ItemUseAnimation.DRINK;
            case 1 -> anim == ItemUseAnimation.EAT;
            default -> anim == ItemUseAnimation.DRINK || anim == ItemUseAnimation.EAT;
        };
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##autoDrinkEnabled", isActive())) toggle();

        ImGui.separator();

        handMode = comboValue("Hand##autoDrinkHand", handMode, HAND_LABELS, ".hand");
        filterMode = comboValue("Filter##autoDrinkFilter", filterMode, FILTER_LABELS, ".filter");

        int[] d = {delay};
        if (ImGui.sliderInt("Delay (ticks)##autoDrinkDelay", d, 0, 20)) {
            delay = d[0];
            Config.setProperty(baseConfig + ".delay", String.valueOf(delay));
        }

        if (ImGui.checkbox("Interrupt Use##autoDrinkInterrupt", interrupt)) {
            interrupt = !interrupt;
            Config.setProperty(baseConfig + ".interrupt", String.valueOf(interrupt));
        }

        ImGui.end();
    }

    private int comboValue(String id, int current, String[] labels, String key) {
        imgui.type.ImInt sel = new imgui.type.ImInt(current);
        ImGui.setNextItemWidth(160);
        if (ImGui.combo(id, sel, labels)) {
            Config.setProperty(baseConfig + key, String.valueOf(sel.get()));
            return sel.get();
        }
        return current;
    }
}
