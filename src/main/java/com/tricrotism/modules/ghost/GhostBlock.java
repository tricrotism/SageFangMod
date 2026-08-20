package com.tricrotism.modules.ghost;

import com.tricrotism.SageFang;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
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
 * Ghost Blocks: place and break blocks client-side only.
 * The server never sees these changes.
 */
public class GhostBlock extends Module {

    public static final GhostBlock instance = new GhostBlock();

    private record GhostOp(BlockPos pos, BlockState oldState, BlockState newState) {}

    private final Deque<GhostOp> history = new ArrayDeque<>();
    private final Set<BlockPos> ghostPositions = new HashSet<>();

    private final Settings.Text blockId = text("Block", "block", "Block id to place", "minecraft:stone", 256);
    private final Settings.Key placeKey = key("Place", "keybindPlace", "Place a ghost block", GLFW.GLFW_KEY_RIGHT_BRACKET);
    private final Settings.Key breakKey = key("Break", "keybindBreak", "Break a ghost block", GLFW.GLFW_KEY_LEFT_BRACKET);
    private final Settings.Key undoKey = key("Undo", "keybindUndo", "Undo the last op", GLFW.GLFW_KEY_BACKSLASH);

    private boolean placeWasDown;
    private boolean breakWasDown;
    private boolean undoWasDown;

    public GhostBlock() {
        super("ghostblock", "Ghost Blocks", "Place and break blocks client-side only.", Category.WORLD);
    }

    private BlockState getSelectedBlock() {
        String id = this.blockId.get();
        try {
            Identifier rl = Identifier.parse(id);
            var ref = BuiltInRegistries.BLOCK.get(rl);
            if (ref.isPresent()) {
                Block block = ref.get().value();
                if (block != Blocks.AIR) {
                    return block.defaultBlockState();
                }
            }
        } catch (Exception e) {
            SageFang.LOGGER.warn("[GhostBlock] Invalid block ID '{}', falling back to stone", id);
        }
        return Blocks.STONE.defaultBlockState();
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

        int placeKey = this.placeKey.get();
        {
            boolean down = KeybindUtil.isKeyDown(placeKey);
            if (down && !placeWasDown) ghostPlace();
            placeWasDown = down;
        }

        int breakKey = this.breakKey.get();
        {
            boolean down = KeybindUtil.isKeyDown(breakKey);
            if (down && !breakWasDown) ghostBreak();
            breakWasDown = down;
        }

        int undoKey = this.undoKey.get();
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
            blockId.render();

            ImGui.separatorText("Actions");
            if (ImGui.button("Undo Last##ghostUndo")) {
                undo();
            }
            ImGui.sameLine();
            if (ImGui.button("Clear All##ghostClearAll")) {
                clearAll();
            }

            ImGui.separatorText("Keybinds");

            placeKey.render();
            breakKey.render();
            undoKey.render();

            ImGui.end();
        } catch (Exception e) {
            SageFang.LOGGER.error("Error in GhostBlock menu", e);
        }
    }


}
