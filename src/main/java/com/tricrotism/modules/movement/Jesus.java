package com.tricrotism.modules.movement;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;

/**
 * Walks on the surface of water.
 * <p>
 * The oldest movement cheat there is, and still shipped by every client under this name. It holds
 * the player at the surface and reports standing on the ground, which turns swimming, slow and
 * drifting and vertical, into ordinary walking across the top of an ocean.
 * <p>
 * The reason it survives modern anticheats is not that the movement is subtle. It is that water is
 * unmodelled: an engine that reconstructs positions has to decline to judge a swimming player,
 * because swimming physics is a second movement model, and declining covers the cheat as completely
 * as it covers the swimmer. The question that still has an answer in water is not "was this movement
 * legal" but "is there anything under you", and this module exists to show the difference.
 */
public final class Jesus extends Module {

    public static final Jesus instance = new Jesus();

    private final Settings.Bool claimGround =
        bool("Claim Ground", "claimGround", "Also report standing on the ground", true);

    private int ticksOnWater;

    @Getter private static volatile boolean claiming;

    private Jesus() {
        super("jesus", "Jesus", "Walk on water, reporting ground the water cannot provide.",
            Category.EXPLOIT);
    }

    @Override
    public void onActivate() {
        ticksOnWater = 0;
        TestLog.event("jesus_enable", "claimGround", claimGround.get());
    }

    @Override
    public void onDeactivate() {
        claiming = false;
        TestLog.event("jesus_disable", "ticksOnWater", ticksOnWater);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.level == null) {
            claiming = false;
            return;
        }

        if (!mc.player.isInWater()) {
            claiming = false;
            return;
        }

        Vec3 delta = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(delta.x, 0.0, delta.z);
        mc.player.setOnGround(true);
        mc.player.fallDistance = 0.0f;
        claiming = claimGround.get();
        ticksOnWater++;
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textUnformatted("Ticks on water: " + ticksOnWater);
    }
}
