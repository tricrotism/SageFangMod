package com.tricrotism.modules.combat;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Places and charges two respawn anchors in sequence at the block you're looking
 * at when the bound key is pressed. Ported from the Meteor addon's double-anchor
 * (Meteor InvUtils inlined). Requires a respawn anchor and glowstone in the hotbar.
 */
public final class DoubleAnchorMacro extends Module implements Menu {

    public static final DoubleAnchorMacro instance = new DoubleAnchorMacro();

    private int switchDelay;
    private int totemSlot;

    private int delayCounter;
    private int step;
    private boolean anchoring;
    private boolean keyWasDown;
    private boolean awaitingKeybind;

    private DoubleAnchorMacro() {
        super("doubleanchor", "Double Anchor", "Place and charge two respawn anchors on a keypress.", "Combat");
        switchDelay = Config.getInt(baseConfig + ".switchDelay", 2);
        totemSlot = Config.getInt(baseConfig + ".totemSlot", 1);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
        if (!hasRequiredItems()) return;

        boolean down = KeybindUtil.isKeyDown(getKeybind());
        if (!anchoring && down && !keyWasDown) {
            anchoring = true;
            step = 0;
            delayCounter = 0;
        }
        keyWasDown = down;

        if (!anchoring) return;

        if (!(mc.hitResult instanceof BlockHitResult hit)
            || mc.level.getBlockState(hit.getBlockPos()).getBlock() == Blocks.AIR) {
            anchoring = false;
            delayCounter = 0;
            return;
        }

        if (delayCounter < switchDelay) {
            delayCounter++;
            return;
        }

        switch (step) {
            case 0 -> swapTo(Items.RESPAWN_ANCHOR);
            case 1 -> use(hit);
            case 2 -> swapTo(Items.GLOWSTONE);
            case 3 -> use(hit);
            case 4 -> swapTo(Items.RESPAWN_ANCHOR);
            case 5 -> {
                use(hit);
                use(hit);
            }
            case 6 -> swapTo(Items.GLOWSTONE);
            case 7 -> use(hit);
            case 8 -> selectSlot(totemSlot - 1);
            case 9 -> use(hit);
            default -> {
                anchoring = false;
                step = 0;
                delayCounter = 0;
                return;
            }
        }

        step++;
        delayCounter = 0;
    }

    private void use(BlockHitResult hit) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    private void swapTo(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) {
                selectSlot(i);
                return;
            }
        }
    }

    private void selectSlot(int slot) {
        if (slot >= 0 && slot < 9) mc.player.getInventory().setSelectedSlot(slot);
    }

    private boolean hasRequiredItems() {
        boolean anchor = false, glow = false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.RESPAWN_ANCHOR) anchor = true;
            if (stack.getItem() == Items.GLOWSTONE) glow = true;
        }
        return anchor && glow;
    }

    private int getKeybind() {
        return Config.getInt(baseConfig + ".keybind", GLFW.GLFW_KEY_UNKNOWN);
    }

    private void setKeybind(int key) {
        Config.setProperty(baseConfig + ".keybind", String.valueOf(key));
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##doubleAnchorEnabled", isActive())) toggle();
        ImGui.separator();

        int[] sd = {switchDelay};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Step Delay##daDelay", sd, 0, 20)) {
            switchDelay = sd[0];
            Config.setProperty(baseConfig + ".switchDelay", String.valueOf(switchDelay));
        }

        int[] ts = {totemSlot};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt("Totem Slot##daTotem", ts, 1, 9)) {
            totemSlot = ts[0];
            Config.setProperty(baseConfig + ".totemSlot", String.valueOf(totemSlot));
        }

        ImGui.separator();
        int result = KeybindUtil.renderKeybindButton("Activate", "doubleAnchorKeybind", getKeybind(), awaitingKeybind);
        if (result == KeybindUtil.START_LISTENING) {
            awaitingKeybind = true;
        } else if (result == KeybindUtil.CLEAR_BIND) {
            setKeybind(GLFW.GLFW_KEY_UNKNOWN);
            awaitingKeybind = false;
        } else if (result != KeybindUtil.NO_CHANGE) {
            setKeybind(result);
            awaitingKeybind = false;
        }

        ImGui.end();
    }
}
