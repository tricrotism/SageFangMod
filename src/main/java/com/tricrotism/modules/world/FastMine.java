package com.tricrotism.modules.world;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
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
 * prediction handler via {@code ClientLevelAccessor}, and manual mode is enforced
 * by {@code MultiPlayerGameModeMixin}.
 */
public final class FastMine extends Module implements Menu {

    public static final FastMine instance = new FastMine();

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
    private int customSequence;
    private boolean abortAfter;
    private double speed;
    private int normalsBeforeCheat;
    private boolean doubleStop;
    private int doubleStopEvery;
    private boolean waitForBreak;
    private int retryTicks;
    private boolean manual;
    private int cycleDelay;
    private boolean noSwing;
    private boolean swingEachPacket;

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
        super("fastmine", "Fast Mine", "AbortRestart / packet-cycle mining.", "World");
        packetOrder = enumOf(PacketOrder.values(), ".order", 2);
        sequenceMode = enumOf(SequenceMode.values(), ".sequenceMode", 0);
        timingMode = enumOf(TimingMode.values(), ".timing", 1);
        customSequence = Config.getInt(baseConfig + ".customSequence", 0);
        abortAfter = Config.getBool(baseConfig + ".abortAfter", false);
        speed = parseDouble(Config.get(baseConfig + ".speed", "1.0"), 1.0);
        normalsBeforeCheat = Config.getInt(baseConfig + ".normalsBeforeCheat", 2);
        doubleStop = Config.getBool(baseConfig + ".doubleStop", true);
        doubleStopEvery = Config.getInt(baseConfig + ".doubleStopEvery", 5);
        waitForBreak = Config.getBool(baseConfig + ".waitForBreak", true);
        retryTicks = Config.getInt(baseConfig + ".retryTicks", 8);
        manual = Config.getBool(baseConfig + ".manual", true);
        cycleDelay = Config.getInt(baseConfig + ".cycleDelay", 0);
        noSwing = Config.getBool(baseConfig + ".noSwing", false);
        swingEachPacket = Config.getBool(baseConfig + ".swingEachPacket", false);
    }

    /**
     * Used by MultiPlayerGameModeMixin to suppress vanilla mining.
     */
    public boolean isManualMode() {
        return manual;
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
            case Custom -> mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir, customSequence));
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
        if (mc.getConnection() == null || !abortAfter) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, pos, dir, 0));
    }

    private void swing() {
        if (noSwing || mc.player == null) return;
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
        if (cycleDelay > 0) {
            phase = Phase.Cooldown;
            cooldown = cycleDelay;
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
        if (!swingEachPacket) swing();
        enterCooldownOrIdle();
    }

    private void finishPair(BlockPos pos, Direction dir) {
        sendSequenced(finishAction(), pos, dir);
        if (swingEachPacket) swing();
        afterPairComplete(pos, dir);
    }

    private void beginPair(BlockPos pos, Direction dir) {
        targetPos = pos;
        targetDirection = dir;
        sendSequenced(beginAction(), pos, dir);
        if (swingEachPacket) swing();
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
            cheatNext = normalsBeforeCheat <= 0 || normalsDone >= normalsBeforeCheat;
            if (cheatNext) normalsDone = 0;
        } else {
            cheatNext = normalsBeforeCheat <= 0;
            normalsDone = 0;
        }

        clearCrack();
        if (!swingEachPacket) swing();

        boolean doDouble = doubleStop && doubleStopEvery > 0 && digsCompleted % doubleStopEvery == 0;
        if (doDouble) {
            targetPos = pos.immutable();
            targetDirection = dir;
            phase = Phase.ExtraStop;
            return;
        }
        enterWaitAckOrCooldown(pos, dir);
    }

    private void enterWaitAckOrCooldown(BlockPos pos, Direction dir) {
        if (waitForBreak) {
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
        if (cheatNext || normalsBeforeCheat <= 0) {
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
        cheatNext = normalsBeforeCheat <= 0;
    }

    @Override
    public void onDeactivate() {
        if (targetPos != null && mc.getConnection() != null && (isAbortRestart() || abortAfter)) {
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

        if (manual && !mc.options.keyAttack.isDown()) {
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
            if (swingEachPacket) swing();
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
            if (swingEachPacket) swing();
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
            if (swingEachPacket) swing();
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

            breakProgress += delta * speed;
            int stage = Math.min((int) (breakProgress * 10.0), 9);
            mc.level.destroyBlockProgress(mc.player.getId(), targetPos, stage);
            swing();

            if (breakProgress >= 1.0) {
                Direction dir = targetDirection != null ? targetDirection : (lookDir != null ? lookDir : Direction.DOWN);
                sendSequenced(Action.STOP_DESTROY_BLOCK, targetPos, dir);
                if (swingEachPacket) swing();
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
            if (ackTicks >= retryTicks) {
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
        if (sequenceMode == SequenceMode.Custom) {
            int[] cs = {customSequence};
            ImGui.setNextItemWidth(160);
            if (ImGui.dragInt("Custom Seq##fmCustomSeq", cs)) {
                customSequence = cs[0];
                Config.setProperty(baseConfig + ".customSequence", String.valueOf(customSequence));
            }
        }

        manual = boolRow("Manual (hold attack)##fmManual", manual, ".manual");
        noSwing = boolRow("No Swing##fmNoSwing", noSwing, ".noSwing");
        if (!noSwing) swingEachPacket = boolRow("Swing Each Packet##fmSwingEach", swingEachPacket, ".swingEachPacket");
        cycleDelay = slider("Cycle Delay##fmCycleDelay", cycleDelay, 0, 20, ".cycleDelay");

        if (packetOrder == PacketOrder.AbortRestart) {
            ImGui.separatorText("AbortRestart");
            float[] sp = {(float) speed};
            ImGui.setNextItemWidth(160);
            if (ImGui.sliderFloat("Speed##fmSpeed", sp, 0.1f, 5.0f)) {
                speed = sp[0];
                Config.setProperty(baseConfig + ".speed", String.valueOf(speed));
            }
            normalsBeforeCheat = slider("Normals Before Cheat##fmNorm", normalsBeforeCheat, 0, 20, ".normalsBeforeCheat");
            doubleStop = boolRow("Double Stop##fmDoubleStop", doubleStop, ".doubleStop");
            if (doubleStop)
                doubleStopEvery = slider("Double Stop Every##fmDoubleEvery", doubleStopEvery, 1, 20, ".doubleStopEvery");
            waitForBreak = boolRow("Wait For Break##fmWait", waitForBreak, ".waitForBreak");
            if (waitForBreak) retryTicks = slider("Retry Ticks##fmRetry", retryTicks, 1, 40, ".retryTicks");
        } else {
            ImGui.separatorText("Reversed / Normal");
            int timing = combo("Timing##fmTiming", timingMode.ordinal(), TIMING_LABELS, ".timing");
            timingMode = TimingMode.values()[timing];
            abortAfter = boolRow("Abort After##fmAbortAfter", abortAfter, ".abortAfter");
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

    private int slider(String id, int value, int min, int max, String key) {
        int[] v = {value};
        ImGui.setNextItemWidth(160);
        if (ImGui.sliderInt(id, v, min, max)) {
            value = v[0];
            Config.setProperty(baseConfig + key, String.valueOf(value));
        }
        return value;
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
