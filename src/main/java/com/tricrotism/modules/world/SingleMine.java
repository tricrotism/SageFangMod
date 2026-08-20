package com.tricrotism.modules.world;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.mixin.accessors.ClientLevelAccessor;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Mines the block under your crosshair by sending the raw dig packets, with an
 * adjustable break-speed.get() multiplier, action-mode (which START/STOP/finish packets
 * to send), optional continuous.get()/manual.get() mining and a post-break hotbar swap.
 * Ported from the Meteor addon's single-mine. Sequence modes Vanilla/Current use
 * the vanilla prediction handler (via {@code ClientLevelAccessor}); manual.get() mode is
 * enforced by {@code MultiPlayerGameModeMixin} suppressing vanilla mining.
 */
public final class SingleMine extends Module {

    public static final SingleMine instance = new SingleMine();

    private final Settings.Mode sequenceMode = mode("Sequence", "sequenceMode", "Which break-sequence to send", 1, "Vanilla", "Current", "Zero", "Custom");
    private final Settings.Int customSequence = integer("Custom Seq", "customSequence", "Sequence number for Custom mode", 0, 0, 100000);
    private final Settings.Mode actionMode = mode("Actions", "actionMode", "Which START/STOP packets to send", 0, "Both", "Start only", "Stop only", "Reversed");
    private final Settings.Decimal speed = decimal("Speed", "speed", "Break-progress multiplier", 1.0, 0.1, 5.0);
    private final Settings.Bool continuous = bool("Continuous", "continuous", "Keep mining without release", true);
    private final Settings.Bool manual = bool("Manual (hold attack)", "manual", "Only mine while attack is held", false);
    private final Settings.Bool noSwing = bool("No Swing", "noSwing", "Skip the swing packet", false);
    private final Settings.Bool noAbort = bool("No Abort", "noAbort", "Never send abort-destroy", false);
    private final Settings.Bool swapAfterMine = bool("Swap After Mine", "swapAfterMine", "Switch hotbar slot after a break", false);
    private final Settings.Int swapSlot = integer("Swap Slot", "swapSlot", "Slot to swap to after mining", 1, 0, 8);

    private static final int DESTROY_DELAY = 5;


    private BlockPos targetPos;
    private Direction targetDirection;
    private double breakProgress;
    private boolean mining;
    private int destroyDelay;
    private BlockPos lastFinishedPos;

    private SingleMine() {
        super("singlemine", "Single Mine", "Packet-mine the block you look at.", Category.WORLD);
    }

    @Override
    public void onActivate() {
        resetTarget();
        destroyDelay = 0;
        lastFinishedPos = null;
    }

    @Override
    public void onDeactivate() {
        if (mining && targetPos != null && mc.player != null && mc.getConnection() != null && !noAbort.get()) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, targetPos, targetDirection, 0));
        }
        clearProgress();
        resetTarget();
        destroyDelay = 0;
        lastFinishedPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            toggle();
            return;
        }

        // Manual mode: only mine while the attack key is held; releasing aborts any dig in progress.
        if (manual.get() && !mc.options.keyAttack.isDown()) {
            if (mining) abortMining();
            destroyDelay = 0;
            return;
        }

        BlockPos lookPos = null;
        Direction lookDir = null;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && mc.hitResult instanceof BlockHitResult bhr) {
            BlockPos p = bhr.getBlockPos();
            if (!mc.level.getBlockState(p).isAir()) {
                lookPos = p;
                lookDir = bhr.getDirection();
            }
        }

        if (lastFinishedPos != null
            && (mc.level.getBlockState(lastFinishedPos).isAir() || lookPos == null || !lookPos.equals(lastFinishedPos))) {
            lastFinishedPos = null;
        }

        if (destroyDelay > 0) {
            destroyDelay--;
            if (lookPos != null) swing();
            return;
        }

        if (lookPos == null) {
            if (mining) abortMining();
            if (!staysOn()) toggle();
            return;
        }

        if (lookPos.equals(lastFinishedPos)) return;

        if (!mining || !lookPos.equals(targetPos)) {
            beginBlock(lookPos, lookDir);
            return;
        }

        BlockState state = mc.level.getBlockState(targetPos);
        float delta = state.getDestroyProgress(mc.player, mc.level, targetPos);
        if (delta <= 0) {
            abortMining();
            if (!staysOn()) toggle();
            return;
        }

        breakProgress += delta * speed.get();
        int stage = Math.min((int) (breakProgress * 10.0), 9);
        mc.level.destroyBlockProgress(mc.player.getId(), targetPos, stage);
        swing();

        if (breakProgress >= 1.0) finishMining();
    }

    private void beginBlock(BlockPos pos, Direction dir) {
        if (mining && targetPos != null && !targetPos.equals(pos)) {
            sendAction(Action.ABORT_DESTROY_BLOCK, targetPos, targetDirection);
            clearProgress();
        }

        targetPos = pos;
        targetDirection = dir;
        breakProgress = 0;
        mining = true;

        Action begin = beginAction();
        if (begin != null) sendAction(begin, pos, dir);
        swing();

        if (actionMode.get() == 0) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.getDestroyProgress(mc.player, mc.level, pos) >= 1.0f) {
                mc.level.destroyBlockProgress(mc.player.getId(), pos, -1);
                finalizeBreak(pos, false);
            }
        }
    }

    private void finishMining() {
        BlockPos pos = targetPos;
        Action finish = finishAction();
        if (finish != null) sendAction(finish, targetPos, targetDirection);
        clearProgress();
        finalizeBreak(pos, true);
    }

    private void finalizeBreak(BlockPos pos, boolean applyDelay) {
        if (swapAfterMine.get()) doSwap();
        lastFinishedPos = pos;
        resetTarget();
        if (applyDelay) destroyDelay = DESTROY_DELAY;
        if (!staysOn()) toggle();
    }

    private void abortMining() {
        if (mining && targetPos != null && mc.getConnection() != null) {
            sendAction(Action.ABORT_DESTROY_BLOCK, targetPos, targetDirection);
        }
        clearProgress();
        resetTarget();
    }

    private void clearProgress() {
        if (targetPos != null && mc.level != null && mc.player != null) {
            mc.level.destroyBlockProgress(mc.player.getId(), targetPos, -1);
        }
    }

    private void resetTarget() {
        mining = false;
        breakProgress = 0;
        targetPos = null;
        targetDirection = null;
    }

    private void sendAction(Action action, BlockPos pos, Direction dir) {
        if (mc.getConnection() == null) return;
        if (action == Action.ABORT_DESTROY_BLOCK && noAbort.get()) return;

        switch (sequenceMode.get()) {
            case 0 -> { // Vanilla: advance the prediction handler like vanilla does
                BlockStatePredictionHandler h = predictionHandler();
                if (h == null) {
                    mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, 0));
                    return;
                }
                h.startPredicting();
                mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, h.currentSequence()));
                h.close();
            }
            case 1 -> { // Current: reuse the current sequence without advancing it
                BlockStatePredictionHandler h = predictionHandler();
                int seq = h != null ? h.currentSequence() : 0;
                mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, seq));
            }
            case 3 ->
                mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, customSequence.get()));
            default -> mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, 0)); // Zero
        }
    }

    private BlockStatePredictionHandler predictionHandler() {
        if (mc.level == null) return null;
        return ((ClientLevelAccessor) mc.level).sagefang$getBlockStatePredictionHandler();
    }

    /**
     * Used by MultiPlayerGameModeMixin to suppress vanilla mining while this is the manual.get() miner.
     */
    public boolean isManualMode() {
        return manual.get();
    }

    private boolean staysOn() {
        return continuous.get() || manual.get();
    }

    private Action beginAction() {
        return switch (actionMode.get()) {
            case 0, 1 -> Action.START_DESTROY_BLOCK;
            case 3 -> Action.STOP_DESTROY_BLOCK;
            default -> null;
        };
    }

    private Action finishAction() {
        return switch (actionMode.get()) {
            case 0, 2 -> Action.STOP_DESTROY_BLOCK;
            case 3 -> Action.START_DESTROY_BLOCK;
            default -> null;
        };
    }

    private void swing() {
        if (!noSwing.get() && mc.player != null) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void doSwap() {
        if (mc.player == null || mc.getConnection() == null) return;
        int original = mc.player.getInventory().getSelectedSlot();
        if (swapSlot.get() == original) return;
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(swapSlot.get()));
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(original));
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
