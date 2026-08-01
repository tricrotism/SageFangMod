package com.tricrotism.modules.combat;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Automatically charges then explodes the respawn anchor you're looking at while
 * you hold right-click: swaps to glowstone and charges an uncharged anchor, or
 * swaps to a totem slot and detonates a charged one. Ported from the Meteor
 * addon's anchor-macro (Meteor helper utils inlined).
 */
public final class AnchorMacro extends Module implements Menu {

    public static final AnchorMacro instance = new AnchorMacro();

    private int switchDelay;
    private int glowstoneDelay;
    private int explodeDelay;
    private int totemSlot;

    private int switchCounter;
    private int glowstoneCounter;
    private int explodeCounter;
    private boolean hasPlacedGlowstone;
    private boolean hasExplodedAnchor;

    private AnchorMacro() {
        super("anchormacro", "Anchor Macro", "Auto-charge and explode respawn anchors while holding right-click.", "Combat");
        switchDelay = Config.getInt(baseConfig + ".switchDelay", 0);
        glowstoneDelay = Config.getInt(baseConfig + ".glowstoneDelay", 0);
        explodeDelay = Config.getInt(baseConfig + ".explodeDelay", 0);
        totemSlot = Config.getInt(baseConfig + ".totemSlot", 1);
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    private void resetState() {
        switchCounter = 0;
        glowstoneCounter = 0;
        explodeCounter = 0;
        hasPlacedGlowstone = false;
        hasExplodedAnchor = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.screen != null) return;
        if (isShieldOrFoodActive()) return;

        boolean rightHeld = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (!rightHeld) {
            hasPlacedGlowstone = false;
            hasExplodedAnchor = false;
            return;
        }
        handleAnchorInteraction();
    }

    private boolean isShieldOrFoodActive() {
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        boolean food = main.has(DataComponents.FOOD) || off.has(DataComponents.FOOD);
        boolean shield = main.getItem() instanceof ShieldItem || off.getItem() instanceof ShieldItem;
        boolean rightPressed = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        return (food || shield) && rightPressed;
    }

    private void handleAnchorInteraction() {
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() != Blocks.RESPAWN_ANCHOR) return;

        // Cancel vanilla's own use so we drive the interaction ourselves.
        mc.options.keyUse.setDown(false);

        int charge = state.getValue(RespawnAnchorBlock.CHARGE);
        if (charge == 0 && !hasPlacedGlowstone) {
            placeGlowstone(hit);
        } else if (charge != 0 && !hasExplodedAnchor) {
            explodeAnchor(hit);
        }
    }

    private void placeGlowstone(BlockHitResult hit) {
        if (mc.player.getMainHandItem().getItem() != Items.GLOWSTONE) {
            if (switchCounter < switchDelay) {
                switchCounter++;
                return;
            }
            switchCounter = 0;
            int slot = findHotbar(Items.GLOWSTONE);
            if (slot != -1) mc.player.getInventory().setSelectedSlot(slot);
            return;
        }
        if (glowstoneCounter < glowstoneDelay) {
            glowstoneCounter++;
            return;
        }
        glowstoneCounter = 0;
        interact(hit);
        hasPlacedGlowstone = true;
    }

    private void explodeAnchor(BlockHitResult hit) {
        int target = totemSlot - 1;
        if (mc.player.getInventory().getSelectedSlot() != target) {
            if (switchCounter < switchDelay) {
                switchCounter++;
                return;
            }
            switchCounter = 0;
            mc.player.getInventory().setSelectedSlot(target);
            return;
        }
        if (explodeCounter < explodeDelay) {
            explodeCounter++;
            return;
        }
        explodeCounter = 0;
        interact(hit);
        hasExplodedAnchor = true;
    }

    private int findHotbar(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    private void interact(BlockHitResult hit) {
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##anchorMacroEnabled", isActive())) toggle();
        ImGui.separator();

        switchDelay = slider("Switch Delay##amSwitch", switchDelay, 0, 20, ".switchDelay");
        glowstoneDelay = slider("Charge Delay##amGlow", glowstoneDelay, 0, 20, ".glowstoneDelay");
        explodeDelay = slider("Explode Delay##amExplode", explodeDelay, 0, 20, ".explodeDelay");
        totemSlot = slider("Totem Slot##amTotem", totemSlot, 1, 9, ".totemSlot");

        ImGui.end();
    }

    private int slider(String id, int value, int min, int max, String key) {
        int[] v = {value};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt(id, v, min, max)) {
            value = v[0];
            Config.setProperty(baseConfig + key, String.valueOf(value));
        }
        return value;
    }
}
