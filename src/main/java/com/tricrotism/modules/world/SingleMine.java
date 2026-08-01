package com.tricrotism.modules.world;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.mixin.accessors.ClientLevelAccessor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
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
 * adjustable break-speed multiplier, action-mode (which START/STOP/finish packets
 * to send), optional continuous/manual mining and a post-break hotbar swap.
 * Ported from the Meteor addon's single-mine. Sequence modes Vanilla/Current use
 * the vanilla prediction handler (via {@code ClientLevelAccessor}); manual mode is
 * enforced by {@code MultiPlayerGameModeMixin} suppressing vanilla mining.
 */
public final class SingleMine extends Module implements Menu {

    public static final SingleMine instance = new SingleMine();

    private static final int DESTROY_DELAY = 5;
    private static final String[] SEQ_LABELS = {"Vanilla", "Current", "Zero", "Custom"};
    private static final String[] ACTION_LABELS = {"Both", "Start only", "Stop only", "Reversed"};

    private int sequenceMode;
    private int customSequence;
    private int actionMode;
    private boolean noSwing;
    private boolean noAbort;
    private boolean continuous;
    private boolean manual;
    private double speed;
    private boolean swapAfterMine;
    private int swapSlot;

    private BlockPos targetPos;
    private Direction targetDirection;
    private double breakProgress;
    private boolean mining;
    private int destroyDelay;
    private BlockPos lastFinishedPos;

    private SingleMine() {
        super("singlemine", "Single Mine", "Packet-mine the block you look at.", "World");
        sequenceMode = Config.getInt(baseConfig + ".sequenceMode", 1);
        customSequence = Config.getInt(baseConfig + ".customSequence", 0);
        actionMode = Config.getInt(baseConfig + ".actionMode", 0);
        noSwing = Config.getBool(baseConfig + ".noSwing", false);
        noAbort = Config.getBool(baseConfig + ".noAbort", false);
        continuous = Config.getBool(baseConfig + ".continuous", true);
        manual = Config.getBool(baseConfig + ".manual", false);
        speed = parseDouble(Config.get(baseConfig + ".speed", "1.0"), 1.0);
        swapAfterMine = Config.getBool(baseConfig + ".swapAfterMine", false);
        swapSlot = Config.getInt(baseConfig + ".swapSlot", 1);
    }

    @Override
    public void onActivate() {
        resetTarget();
        destroyDelay = 0;
        lastFinishedPos = null;
    }

    @Override
    public void onDeactivate() {
        if (mining && targetPos != null && mc.player != null && mc.getConnection() != null && !noAbort) {
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
        if (manual && !mc.options.keyAttack.isDown()) {
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

        breakProgress += delta * speed;
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

        if (actionMode == 0) {
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
        if (swapAfterMine) doSwap();
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
        if (action == Action.ABORT_DESTROY_BLOCK && noAbort) return;

        switch (sequenceMode) {
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
            case 3 -> mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, customSequence));
            default -> mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, 0)); // Zero
        }
    }

    private BlockStatePredictionHandler predictionHandler() {
        if (mc.level == null) return null;
        return ((ClientLevelAccessor) mc.level).sagefang$getBlockStatePredictionHandler();
    }

    /**
     * Used by MultiPlayerGameModeMixin to suppress vanilla mining while this is the manual miner.
     */
    public boolean isManualMode() {
        return manual;
    }

    private boolean staysOn() {
        return continuous || manual;
    }

    private Action beginAction() {
        return switch (actionMode) {
            case 0, 1 -> Action.START_DESTROY_BLOCK;
            case 3 -> Action.STOP_DESTROY_BLOCK;
            default -> null;
        };
    }

    private Action finishAction() {
        return switch (actionMode) {
            case 0, 2 -> Action.STOP_DESTROY_BLOCK;
            case 3 -> Action.START_DESTROY_BLOCK;
            default -> null;
        };
    }

    private void swing() {
        if (!noSwing && mc.player != null) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void doSwap() {
        if (mc.player == null || mc.getConnection() == null) return;
        int original = mc.player.getInventory().getSelectedSlot();
        if (swapSlot == original) return;
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(swapSlot));
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(original));
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##singleMineEnabled", isActive())) toggle();
        ImGui.separator();

        sequenceMode = combo("Sequence##smSeq", sequenceMode, SEQ_LABELS, ".sequenceMode");
        if (sequenceMode == 1) {
            int[] cs = {customSequence};
            ImGui.setNextItemWidth(160);
            if (ImGui.dragInt("Custom Seq##smCustomSeq", cs)) {
                customSequence = cs[0];
                Config.setProperty(baseConfig + ".customSequence", String.valueOf(customSequence));
            }
        }
        actionMode = combo("Actions##smAction", actionMode, ACTION_LABELS, ".actionMode");

        float[] sp = {(float) speed};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderFloat("Speed##smSpeed", sp, 0.1f, 5.0f)) {
            speed = sp[0];
            Config.setProperty(baseConfig + ".speed", String.valueOf(speed));
        }

        continuous = boolRow("Continuous##smCont", continuous, ".continuous");
        manual = boolRow("Manual (hold attack)##smManual", manual, ".manual");
        noSwing = boolRow("No Swing##smNoSwing", noSwing, ".noSwing");
        noAbort = boolRow("No Abort##smNoAbort", noAbort, ".noAbort");
        swapAfterMine = boolRow("Swap After Mine##smSwap", swapAfterMine, ".swapAfterMine");
        if (swapAfterMine) {
            int[] ss = {swapSlot};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderInt("Swap Slot##smSwapSlot", ss, 0, 8)) {
                swapSlot = ss[0];
                Config.setProperty(baseConfig + ".swapSlot", String.valueOf(swapSlot));
            }
        }

        ImGui.end();
    }

    private boolean boolRow(String id, boolean value, String key) {
        if (ImGui.checkbox(id, value)) {
            value = !value;
            Config.setProperty(baseConfig + key, String.valueOf(value));
        }
        return value;
    }

    private int combo(String id, int current, String[] labels, String key) {
        imgui.type.ImInt sel = new imgui.type.ImInt(current);
        ImGui.setNextItemWidth(160);
        if (ImGui.combo(id, sel, labels)) {
            Config.setProperty(baseConfig + key, String.valueOf(sel.get()));
            return sel.get();
        }
        return current;
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
