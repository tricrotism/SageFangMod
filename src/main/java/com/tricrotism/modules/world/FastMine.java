package com.tricrotism.modules.world;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.mixin.accessors.ClientLevelAccessor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import io.avaje.config.Config;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Dig-packet cycler with an AbortRestart mode that interleaves normal progress
 * digs with occasional {@code START → ABORT↓0 → START → STOP} cheat cycles to
 * confuse anti-cheats, plus Reversed/Normal pair modes. Ported from the Meteor
 * addon's fast-mine; the Vanilla/Current sequence modes use the vanilla
 * prediction handler via {@code ClientLevelAccessor}, and manual.get() mode is enforced
 * by {@code MultiPlayerGameModeMixin}.
 */
public final class FastMine extends Module {

    public static final FastMine instance = new FastMine();

    private final Settings.Int customSequence = integer("Custom Seq", "customSequence", "Sequence number for Custom mode", 0, 0, 100000);
    private final Settings.Bool abortAfter = bool("Abort After", "abortAfter", "Send abort-destroy after the cheat", false);
    private final Settings.Decimal speed = decimal("Speed", "speed", "Break-progress multiplier", 1.0, 0.1, 5.0);
    private final Settings.Int normalsBeforeCheat = integer("Normals Before Cheat", "normalsBeforeCheat", "Vanilla breaks before cheating", 2, 0, 20);
    private final Settings.Bool doubleStop = bool("Double Stop", "doubleStop", "Send a second stop packet", true);
    private final Settings.Int doubleStopEvery = integer("Double Stop Every", "doubleStopEvery", "Breaks between double-stops", 5, 1, 20);
    private final Settings.Bool waitForBreak = bool("Wait For Break", "waitForBreak", "Wait for the block to break", true);
    private final Settings.Int retryTicks = integer("Retry Ticks", "retryTicks", "Ticks before retrying", 8, 1, 40);
    private final Settings.Bool manual = bool("Manual (hold attack)", "manual", "Only mine while attack is held", true);
    private final Settings.Int cycleDelay = integer("Cycle Delay", "cycleDelay", "Ticks between cycles", 0, 0, 20);
    private final Settings.Bool noSwing = bool("No Swing", "noSwing", "Skip the swing packet", false);
    private final Settings.Bool swingEachPacket = bool("Swing Each Packet", "swingEachPacket", "Swing on every packet", false);

    public enum SequenceMode {Vanilla, Current, Zero, Custom}

    public enum PacketOrder {Reversed, Normal, AbortRestart}

    public enum TimingMode {SameTick, NextTick}

    private enum Phase {Idle, Digging, CheatAbort, CheatStart2, CheatStop, ExtraStop, WaitAck, WaitSecond, Cooldown}

    private static final String[] ORDER_LABELS = {"Reversed", "Normal", "AbortRestart"};
    private static final String[] SEQ_LABELS = {"Vanilla", "Current", "Zero", "Custom"};
    private static final String[] TIMING_LABELS = {"Same tick", "Next tick"};

    private PacketOrder packetOrder;
    private SequenceMode sequenceMode;
    private TimingMode timingMode;

    private Phase phase = Phase.Idle;
    private BlockPos targetPos;
    private Direction targetDirection;
    private double breakProgress;
    private int ackTicks;
    private int cooldown;
    private int normalsDone;
    private int digsCompleted;
    private boolean cheatNext;

    private FastMine() {
        super("fastmine", "Fast Mine", "AbortRestart / packet-cycle mining.", Category.WORLD);
        packetOrder = enumOf(PacketOrder.values(), ".order", 2);
        sequenceMode = enumOf(SequenceMode.values(), ".sequenceMode", 0);
        timingMode = enumOf(TimingMode.values(), ".timing", 1);
    }

    /**
     * Used by MultiPlayerGameModeMixin to suppress vanilla mining.
     */
    public boolean isManualMode() {
        return manual.get();
    }

    private boolean isAbortRestart() {
        return packetOrder == PacketOrder.AbortRestart;
    }

    private Action beginAction() {
        return packetOrder == PacketOrder.Normal ? Action.START_DESTROY_BLOCK : Action.STOP_DESTROY_BLOCK;
    }

    private Action finishAction() {
        return packetOrder == PacketOrder.Normal ? Action.STOP_DESTROY_BLOCK : Action.START_DESTROY_BLOCK;
    }

    private BlockStatePredictionHandler predictionHandler() {
        if (mc.level == null) return null;
        return ((ClientLevelAccessor) mc.level).sagefang$getBlockStatePredictionHandler();
    }

    private void sendSequenced(Action action, BlockPos pos, Direction dir) {
        if (mc.getConnection() == null) return;
        switch (sequenceMode) {
            case Vanilla -> {
                BlockStatePredictionHandler h = predictionHandler();
                if (h == null) {
                    mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, 0));
                    return;
                }
                h.startPredicting();
                mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, h.currentSequence()));
                h.close();
            }
            case Current -> {
                BlockStatePredictionHandler h = predictionHandler();
                int seq = h != null ? h.currentSequence() : 0;
                mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, seq));
            }
            case Zero -> mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, 0));
            case Custom ->
                mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, customSequence.get()));
        }
    }

    private void sendAbortDownZero(BlockPos pos) {
        if (mc.getConnection() == null || pos == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN, 0));
    }

    private void sendAbortFaceZero(BlockPos pos, Direction dir) {
        if (mc.getConnection() == null || pos == null) return;
        Direction d = dir != null ? dir : Direction.DOWN;
        mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, pos, d, 0));
    }

    private void sendAbortZero(BlockPos pos, Direction dir) {
        if (mc.getConnection() == null || !abortAfter.get()) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, pos, dir, 0));
    }

    private void swing() {
        if (noSwing.get() || mc.player == null) return;
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void clearCrack() {
        if (targetPos != null && mc.level != null && mc.player != null) {
            mc.level.destroyBlockProgress(mc.player.getId(), targetPos, -1);
        }
    }

    private void reset() {
        clearCrack();
        phase = Phase.Idle;
        targetPos = null;
        targetDirection = null;
        breakProgress = 0;
        ackTicks = 0;
        cooldown = 0;
    }

    private void enterCooldownOrIdle() {
        if (cycleDelay.get() > 0) {
            phase = Phase.Cooldown;
            cooldown = cycleDelay.get();
        } else {
            phase = Phase.Idle;
        }
        targetPos = null;
        targetDirection = null;
        breakProgress = 0;
        ackTicks = 0;
    }

    private void afterPairComplete(BlockPos pos, Direction dir) {
        sendAbortZero(pos, dir);
        if (!swingEachPacket.get()) swing();
        enterCooldownOrIdle();
    }

    private void finishPair(BlockPos pos, Direction dir) {
        sendSequenced(finishAction(), pos, dir);
        if (swingEachPacket.get()) swing();
        afterPairComplete(pos, dir);
    }

    private void beginPair(BlockPos pos, Direction dir) {
        targetPos = pos;
        targetDirection = dir;
        sendSequenced(beginAction(), pos, dir);
        if (swingEachPacket.get()) swing();
        if (timingMode == TimingMode.SameTick) {
            finishPair(pos, dir);
        } else {
            phase = Phase.WaitSecond;
        }
    }

    private boolean isStillSolid(BlockPos pos) {
        return pos != null && mc.level != null && !mc.level.getBlockState(pos).isAir();
    }

    private void afterStop(BlockPos pos, Direction dir, boolean wasCheat) {
        digsCompleted++;
        if (!wasCheat) {
            normalsDone++;
            cheatNext = normalsBeforeCheat.get() <= 0 || normalsDone >= normalsBeforeCheat.get();
            if (cheatNext) normalsDone = 0;
        } else {
            cheatNext = normalsBeforeCheat.get() <= 0;
            normalsDone = 0;
        }

        clearCrack();
        if (!swingEachPacket.get()) swing();

        boolean doDouble = doubleStop.get() && doubleStopEvery.get() > 0 && digsCompleted % doubleStopEvery.get() == 0;
        if (doDouble) {
            targetPos = pos.immutable();
            targetDirection = dir;
            phase = Phase.ExtraStop;
            return;
        }
        enterWaitAckOrCooldown(pos, dir);
    }

    private void enterWaitAckOrCooldown(BlockPos pos, Direction dir) {
        if (waitForBreak.get()) {
            targetPos = pos.immutable();
            targetDirection = dir;
            phase = Phase.WaitAck;
            ackTicks = 0;
            return;
        }
        enterCooldownOrIdle();
    }

    private void beginNormalDig(BlockPos pos, Direction dir) {
        targetPos = pos.immutable();
        targetDirection = dir;
        breakProgress = 0;
        sendSequenced(Action.START_DESTROY_BLOCK, pos, dir);
        swing();

        BlockState state = mc.level.getBlockState(pos);
        if (state.getDestroyProgress(mc.player, mc.level, pos) >= 1.0f) {
            clearCrack();
            afterStop(pos, dir, false);
            return;
        }
        phase = Phase.Digging;
    }

    private void beginCheatDig(BlockPos pos, Direction dir) {
        targetPos = pos.immutable();
        targetDirection = dir;
        breakProgress = 0;
        sendSequenced(Action.START_DESTROY_BLOCK, pos, dir);
        swing();
        phase = Phase.CheatAbort;
    }

    private void beginAbortRestartDig(BlockPos pos, Direction dir) {
        if (cheatNext || normalsBeforeCheat.get() <= 0) {
            beginCheatDig(pos, dir);
        } else {
            beginNormalDig(pos, dir);
        }
    }

    @Override
    public void onActivate() {
        reset();
        normalsDone = 0;
        digsCompleted = 0;
        cheatNext = normalsBeforeCheat.get() <= 0;
    }

    @Override
    public void onDeactivate() {
        if (targetPos != null && mc.getConnection() != null && (isAbortRestart() || abortAfter.get())) {
            Direction abortDir = isAbortRestart() ? Direction.DOWN
                : (targetDirection != null ? targetDirection : Direction.DOWN);
            mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, targetPos, abortDir, 0));
        }
        clearCrack();
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            toggle();
            return;
        }

        if (manual.get() && !mc.options.keyAttack.isDown()) {
            if (phase != Phase.Idle && phase != Phase.Cooldown && targetPos != null) {
                if (isAbortRestart()) sendAbortDownZero(targetPos);
                else sendAbortZero(targetPos, targetDirection);
            }
            reset();
            return;
        }

        if (phase == Phase.Cooldown) {
            if (--cooldown <= 0) {
                phase = Phase.Idle;
                targetPos = null;
                targetDirection = null;
            } else {
                return;
            }
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

        if (isAbortRestart()) {
            tickAbortRestart(lookPos, lookDir);
            return;
        }

        if (phase == Phase.WaitSecond) {
            BlockPos pos = targetPos != null ? targetPos : lookPos;
            Direction dir = targetDirection != null ? targetDirection : lookDir;
            if (pos == null) {
                reset();
                return;
            }
            finishPair(pos, dir);
            return;
        }

        if (lookPos == null) {
            if (phase == Phase.WaitSecond && targetPos != null) sendAbortZero(targetPos, targetDirection);
            reset();
            return;
        }

        if (phase != Phase.Idle) return;
        beginPair(lookPos, lookDir);
    }

    private void tickAbortRestart(BlockPos lookPos, Direction lookDir) {
        if (phase == Phase.ExtraStop && targetPos != null) {
            Direction dir = targetDirection != null ? targetDirection : (lookDir != null ? lookDir : Direction.DOWN);
            sendSequenced(Action.STOP_DESTROY_BLOCK, targetPos, dir);
            if (swingEachPacket.get()) swing();
            enterWaitAckOrCooldown(targetPos, dir);
            return;
        }

        if (phase == Phase.CheatAbort && targetPos != null) {
            if (lookPos != null && !lookPos.equals(targetPos)) {
                sendAbortFaceZero(targetPos, targetDirection);
                beginAbortRestartDig(lookPos, lookDir);
                return;
            }
            sendAbortDownZero(targetPos);
            phase = Phase.CheatStart2;
            return;
        }

        if (phase == Phase.CheatStart2 && targetPos != null) {
            if (lookPos != null && !lookPos.equals(targetPos)) {
                beginAbortRestartDig(lookPos, lookDir);
                return;
            }
            Direction dir = targetDirection != null ? targetDirection : (lookDir != null ? lookDir : Direction.DOWN);
            sendSequenced(Action.START_DESTROY_BLOCK, targetPos, dir);
            if (swingEachPacket.get()) swing();
            phase = Phase.CheatStop;
            return;
        }

        if (phase == Phase.CheatStop && targetPos != null) {
            if (lookPos != null && !lookPos.equals(targetPos)) {
                sendAbortFaceZero(targetPos, targetDirection);
                beginAbortRestartDig(lookPos, lookDir);
                return;
            }
            Direction dir = targetDirection != null ? targetDirection : (lookDir != null ? lookDir : Direction.DOWN);
            sendSequenced(Action.STOP_DESTROY_BLOCK, targetPos, dir);
            if (swingEachPacket.get()) swing();
            afterStop(targetPos, dir, true);
            return;
        }

        if (phase == Phase.Digging && targetPos != null) {
            if (lookPos != null && !lookPos.equals(targetPos)) {
                sendAbortFaceZero(targetPos, targetDirection);
                clearCrack();
                beginAbortRestartDig(lookPos, lookDir);
                return;
            }
            if (!isStillSolid(targetPos)) {
                clearCrack();
                enterCooldownOrIdle();
                return;
            }

            BlockState state = mc.level.getBlockState(targetPos);
            float delta = state.getDestroyProgress(mc.player, mc.level, targetPos);
            if (delta <= 0) {
                sendAbortDownZero(targetPos);
                clearCrack();
                enterCooldownOrIdle();
                return;
            }

            breakProgress += delta * speed.get();
            int stage = Math.min((int) (breakProgress * 10.0), 9);
            mc.level.destroyBlockProgress(mc.player.getId(), targetPos, stage);
            swing();

            if (breakProgress >= 1.0) {
                Direction dir = targetDirection != null ? targetDirection : (lookDir != null ? lookDir : Direction.DOWN);
                sendSequenced(Action.STOP_DESTROY_BLOCK, targetPos, dir);
                if (swingEachPacket.get()) swing();
                afterStop(targetPos, dir, false);
            }
            return;
        }

        if (phase == Phase.WaitAck && targetPos != null) {
            if (!isStillSolid(targetPos)) {
                enterCooldownOrIdle();
                return;
            }
            ackTicks++;
            if (ackTicks >= retryTicks.get()) {
                Direction dir = targetDirection != null ? targetDirection : (lookDir != null ? lookDir : Direction.DOWN);
                if (lookPos != null && lookPos.equals(targetPos)) {
                    cheatNext = true;
                    beginCheatDig(targetPos, dir);
                } else {
                    sendAbortDownZero(targetPos);
                    enterCooldownOrIdle();
                }
            }
            return;
        }

        if (lookPos == null) {
            if (phase == Phase.Digging || phase == Phase.CheatAbort || phase == Phase.CheatStart2
                || phase == Phase.CheatStop || phase == Phase.ExtraStop) {
                if (targetPos != null) sendAbortDownZero(targetPos);
            }
            if (phase != Phase.WaitAck) reset();
            return;
        }

        if (phase != Phase.Idle) return;
        beginAbortRestartDig(lookPos, lookDir);
    }

    @Override
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##fastMineEnabled", isActive())) toggle();
        ImGui.separator();

        int order = combo("Packet Order##fmOrder", packetOrder.ordinal(), ORDER_LABELS, ".order");
        packetOrder = PacketOrder.values()[order];
        int seq = combo("Sequence##fmSeq", sequenceMode.ordinal(), SEQ_LABELS, ".sequenceMode");
        sequenceMode = SequenceMode.values()[seq];
        if (sequenceMode == SequenceMode.Custom) customSequence.render();

        manual.render();
        noSwing.render();
        if (!noSwing.get()) swingEachPacket.render();
        cycleDelay.render();

        if (packetOrder == PacketOrder.AbortRestart) {
            ImGui.separatorText("AbortRestart");
            speed.render();
            normalsBeforeCheat.render();
            doubleStop.render();
            if (doubleStop.get()) doubleStopEvery.render();
            waitForBreak.render();
            if (waitForBreak.get()) retryTicks.render();
        } else {
            ImGui.separatorText("Reversed / Normal");
            int timing = combo("Timing##fmTiming", timingMode.ordinal(), TIMING_LABELS, ".timing");
            timingMode = TimingMode.values()[timing];
            abortAfter.render();
        }

        ImGui.end();
    }


    private int combo(String id, int current, String[] labels, String key) {
        ImInt sel = new ImInt(current);
        ImGui.setNextItemWidth(160);
        if (ImGui.combo(id, sel, labels)) {
            Config.setProperty(baseConfig + key, String.valueOf(sel.get()));
            return sel.get();
        }
        return current;
    }

    private <E extends Enum<E>> E enumOf(E[] values, String key, int def) {
        int i = Config.getInt(baseConfig + key, def);
        return values[i >= 0 && i < values.length ? i : def];
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
