package com.tricrotism.modules.ghost;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import io.avaje.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Ghost Blocks — place and break blocks client-side only.
 * The server never sees these changes.
 */
public class GhostBlock extends Module implements Menu {

    public static final GhostBlock instance = new GhostBlock();

    private record GhostOp(BlockPos pos, BlockState oldState, BlockState newState) {}

    private final Deque<GhostOp> history = new ArrayDeque<>();
    private final Set<BlockPos> ghostPositions = new HashSet<>();

    private final ImString blockBuffer = new ImString(256);

    private boolean placeWasDown;
    private boolean breakWasDown;
    private boolean undoWasDown;

    private enum RebindTarget {NONE, PLACE, BREAK, UNDO}

    private RebindTarget awaitingRebind = RebindTarget.NONE;

    public GhostBlock() {
        super("ghostblock", "Ghost Blocks", "Place and break blocks client-side only.", "World");
        blockBuffer.set(Config.get(baseConfig + ".block", "minecraft:stone"));
    }

    private BlockState getSelectedBlock() {
        String blockId = Config.get(baseConfig + ".block", "minecraft:stone");
        try {
            Identifier rl = Identifier.parse(blockId);
            var ref = BuiltInRegistries.BLOCK.get(rl);
            if (ref.isPresent()) {
                Block block = ref.get().value();
                if (block != Blocks.AIR) {
                    return block.defaultBlockState();
                }
            }
        } catch (Exception e) {
            SageFang.LOGGER.warn("[GhostBlock] Invalid block ID '{}', falling back to stone", blockId);
        }
        return Blocks.STONE.defaultBlockState();
    }

    private int getPlaceKeybind() {
        return Config.getInt(baseConfig + ".keybindPlace", GLFW.GLFW_KEY_RIGHT_BRACKET);
    }

    private void setPlaceKeybind(int key) {
        Config.setProperty(baseConfig + ".keybindPlace", String.valueOf(key));
    }

    private int getBreakKeybind() {
        return Config.getInt(baseConfig + ".keybindBreak", GLFW.GLFW_KEY_LEFT_BRACKET);
    }

    private void setBreakKeybind(int key) {
        Config.setProperty(baseConfig + ".keybindBreak", String.valueOf(key));
    }

    private int getUndoKeybind() {
        return Config.getInt(baseConfig + ".keybindUndo", GLFW.GLFW_KEY_BACKSLASH);
    }

    private void setUndoKeybind(int key) {
        Config.setProperty(baseConfig + ".keybindUndo", String.valueOf(key));
    }

    private void ghostPlace() {
        if (mc.level == null || mc.player == null) return;
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return;
        if (bhr.getType() == HitResult.Type.MISS) return;

        BlockPos placePos = bhr.getBlockPos().relative(bhr.getDirection());

        if (placePos.equals(mc.player.blockPosition()) || placePos.equals(mc.player.blockPosition().above())) {
            return;
        }

        BlockState oldState = mc.level.getBlockState(placePos);
        BlockState newState = getSelectedBlock();

        mc.level.setBlock(placePos, newState, 2);
        history.push(new GhostOp(placePos, oldState, newState));
        ghostPositions.add(placePos);
    }

    private void ghostBreak() {
        if (mc.level == null || mc.player == null) return;
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return;
        if (bhr.getType() == HitResult.Type.MISS) return;

        BlockPos breakPos = bhr.getBlockPos();
        BlockState oldState = mc.level.getBlockState(breakPos);

        mc.level.setBlock(breakPos, Blocks.AIR.defaultBlockState(), 2);
        history.push(new GhostOp(breakPos, oldState, Blocks.AIR.defaultBlockState()));
        ghostPositions.add(breakPos);
    }

    private void undo() {
        if (mc.level == null || history.isEmpty()) return;

        GhostOp op = history.pop();
        mc.level.setBlock(op.pos(), op.oldState(), 2);
        ghostPositions.remove(op.pos());
    }

    private void clearAll() {
        if (mc.level == null) return;

        while (!history.isEmpty()) {
            GhostOp op = history.pop();
            mc.level.setBlock(op.pos(), op.oldState(), 2);
        }
        ghostPositions.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;

        int placeKey = getPlaceKeybind();
        {
            boolean down = KeybindUtil.isKeyDown(placeKey);
            if (down && !placeWasDown) ghostPlace();
            placeWasDown = down;
        }

        int breakKey = getBreakKeybind();
        {
            boolean down = KeybindUtil.isKeyDown(breakKey);
            if (down && !breakWasDown) ghostBreak();
            breakWasDown = down;
        }

        int undoKey = getUndoKeybind();
        {
            boolean down = KeybindUtil.isKeyDown(undoKey);
            if (down && !undoWasDown) undo();
            undoWasDown = down;
        }
    }

    @EventHandler
    private void onGameQuit(GameQuitEvent event) {
        history.clear();
        ghostPositions.clear();
        if (isActive()) {
            toggle();
        }
    }

    @Override
    public void frame(ImGuiIO io) {
        try {
            if (!isVisible()) {
                return;
            }

            int flags = ImGuiWindowFlags.AlwaysAutoResize;
            ImGui.setNextWindowBgAlpha(0.55f);
            ImGui.begin(title, flags);

            if (ImGui.checkbox("Enabled##ghostEnabled", isActive())) {
                toggle();
            }

            ImGui.text("Ghost blocks: " + ghostPositions.size());
            ImGui.text("Undo history: " + history.size());

            ImGui.separatorText("Block");
            ImGui.inputText("##ghostBlockId", blockBuffer);
            ImGui.sameLine();
            if (ImGui.button("Apply##ghostApply")) {
                String val = blockBuffer.get().trim();
                if (!val.isEmpty()) {
                    Config.setProperty(baseConfig + ".block", val);
                }
            }

            ImGui.separatorText("Actions");
            if (ImGui.button("Undo Last##ghostUndo")) {
                undo();
            }
            ImGui.sameLine();
            if (ImGui.button("Clear All##ghostClearAll")) {
                clearAll();
            }

            ImGui.separatorText("Keybinds");

            renderKeybindButton("Place", RebindTarget.PLACE, getPlaceKeybind());
            renderKeybindButton("Break", RebindTarget.BREAK, getBreakKeybind());
            renderKeybindButton("Undo", RebindTarget.UNDO, getUndoKeybind());

            if (awaitingRebind != RebindTarget.NONE) {
                scanForKeybind();
            }

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in GhostBlock menu", e);
        }
    }

    private void renderKeybindButton(String label, RebindTarget target, int currentKey) {
        String btnLabel = (awaitingRebind == target) ? "Press a key..." : KeybindUtil.keyName(currentKey);
        if (ImGui.button(btnLabel + "##ghost" + label, 160, 0)) {
            awaitingRebind = target;
        }
        ImGui.sameLine();
        ImGui.text(label);
    }

    private void scanForKeybind() {
        int result = KeybindUtil.scanForKeyPress();
        if (result == KeybindUtil.CLEAR_BIND) {
            switch (awaitingRebind) {
                case PLACE -> setPlaceKeybind(GLFW.GLFW_KEY_UNKNOWN);
                case BREAK -> setBreakKeybind(GLFW.GLFW_KEY_UNKNOWN);
                case UNDO -> setUndoKeybind(GLFW.GLFW_KEY_UNKNOWN);
                default -> {
                }
            }
            awaitingRebind = RebindTarget.NONE;
        } else if (result != KeybindUtil.NO_CHANGE) {
            switch (awaitingRebind) {
                case PLACE -> setPlaceKeybind(result);
                case BREAK -> setBreakKeybind(result);
                case UNDO -> setUndoKeybind(result);
                default -> {
                }
            }
            awaitingRebind = RebindTarget.NONE;
        }
    }
}
