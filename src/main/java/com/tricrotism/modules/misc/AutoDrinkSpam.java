package com.tricrotism.modules.misc;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.world.TickEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Repeatedly attempts to use a drinkable/edible item in hand (potions, food),
 * optionally interrupting the current use each cycle so drinking restarts every
 * tick for maximum spam. Ported from the Meteor addon's auto-drink-spam (the
 * Grim raw-packet path is dropped to keep this self-contained).
 */
public final class AutoDrinkSpam extends Module {

    public static final AutoDrinkSpam instance = new AutoDrinkSpam();

    private final Settings.Mode handMode =
        mode("Hand", "hand", "Which hand to use", 0, "Main hand", "Off hand", "Both");
    private final Settings.Mode filterMode =
        mode("Filter", "filter", "Which items to use", 0, "Drink", "Eat", "Drink or eat");
    private final Settings.Int delay =
        integer("Delay (ticks)", "delay", "Ticks between uses", 0, 0, 20);
    private final Settings.Bool interrupt =
        bool("Interrupt Use", "interrupt", "Restart the use each tick", false);


    private int tickCounter;

    private AutoDrinkSpam() {
        super("autodrinkspam", "Auto Drink Spam", "Constantly use drinkable/edible items in hand.", Category.UTILITY);
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        tickCounter++;
        if (tickCounter <= delay.get()) return;
        tickCounter = 0;

        switch (handMode.get()) {
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

        if (interrupt.get() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == hand) {
            mc.player.stopUsingItem();
        }
        mc.gameMode.useItem(mc.player, hand);
    }

    private boolean matchesFilter(ItemUseAnimation anim) {
        return switch (filterMode.get()) {
            case 0 -> anim == ItemUseAnimation.DRINK;
            case 1 -> anim == ItemUseAnimation.EAT;
            default -> anim == ItemUseAnimation.DRINK || anim == ItemUseAnimation.EAT;
        };
    }

}
