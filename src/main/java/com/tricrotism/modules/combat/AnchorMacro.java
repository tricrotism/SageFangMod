package com.tricrotism.modules.combat;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.world.TickEvent;
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
public final class AnchorMacro extends Module {

    public static final AnchorMacro instance = new AnchorMacro();

    private final Settings.Int switchDelay = integer("Switch Delay", "switchDelay", "Ticks before switching", 2, 0, 20);
    private final Settings.Int glowstoneDelay = integer("Charge Delay", "glowstoneDelay", "Ticks before charging", 1, 0, 20);
    private final Settings.Int explodeDelay = integer("Explode Delay", "explodeDelay", "Ticks before exploding", 1, 0, 20);
    private final Settings.Int totemSlot = integer("Totem Slot", "totemSlot", "Hotbar slot to hold a totem", 9, 1, 9);


    private int switchCounter;
    private int glowstoneCounter;
    private int explodeCounter;
    private boolean hasPlacedGlowstone;
    private boolean hasExplodedAnchor;

    private AnchorMacro() {
        super("anchormacro", "Anchor Macro", "Auto-charge and explode respawn anchors while holding right-click.", Category.COMBAT);
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
        if (mc.gui.screen() != null) return;
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
            if (switchCounter < switchDelay.get()) {
                switchCounter++;
                return;
            }
            switchCounter = 0;
            int slot = findHotbar(Items.GLOWSTONE);
            if (slot != -1) mc.player.getInventory().setSelectedSlot(slot);
            return;
        }
        if (glowstoneCounter < glowstoneDelay.get()) {
            glowstoneCounter++;
            return;
        }
        glowstoneCounter = 0;
        interact(hit);
        hasPlacedGlowstone = true;
    }

    private void explodeAnchor(BlockHitResult hit) {
        int target = totemSlot.get() - 1;
        if (mc.player.getInventory().getSelectedSlot() != target) {
            if (switchCounter < switchDelay.get()) {
                switchCounter++;
                return;
            }
            switchCounter = 0;
            mc.player.getInventory().setSelectedSlot(target);
            return;
        }
        if (explodeCounter < explodeDelay.get()) {
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
}
