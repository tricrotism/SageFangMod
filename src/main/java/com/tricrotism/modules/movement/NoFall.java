package com.tricrotism.modules.movement;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;

import java.util.Random;

/**
 * Claims ground while airborne, which is the whole of what a ground-claim check has to see.
 * <p>
 * The claim is decided once per tick and read from the movement mixin, because
 * {@code sendPosition()} reads the flag several times and its own {@code lastOnGround} bookkeeping
 * has to agree with the packet that went out. A per-read decision would desynchronise them and emit
 * status packets the client never meant to send.
 * <p>
 * {@link #claimRate} is the knob to sweep. A check with a violation buffer does not care that a
 * claim happened, it cares how often, so a run at 100% establishes that the geometry fires at all
 * and the descent from there finds where the buffer stops keeping up.
 */
public final class NoFall extends Module {

    public static final NoFall instance = new NoFall();

    private final Settings.Decimal minFall =
        decimal("Min Fall Distance", "minFall", "Blocks to fall before claiming ground", 0.5, 0.0, 5.0);
    private final Settings.Int claimRate =
        integer("Claim Rate (%)", "claimRate", "Share of airborne ticks that claim ground", 100, 0, 100);

    private final Random rng = TestLog.rng("nofall");

    /**
     * Read from the render thread by the movement mixin, written on the client tick.
     */
    private static volatile boolean claiming;

    private int claims;

    private NoFall() {
        super("nofall", "No Fall", "Claim ground while airborne; sweeps the ground-claim check.",
            Category.EXPLOIT);
    }

    /**
     * Whether this tick's movement packets should say the player is on the ground.
     */
    public static boolean isClaiming() {
        return claiming;
    }

    @Override
    public void onActivate() {
        claims = 0;
        TestLog.event("nofall_enable", "minFall", minFall.get(), "claimRate", claimRate.get());
    }

    @Override
    public void onDeactivate() {
        claiming = false;
        TestLog.event("nofall_disable", "claims", claims);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.player.onGround()) {
            claiming = false;
            return;
        }

        double fallen = mc.player.fallDistance;
        claiming = fallen >= minFall.get() && rng.nextInt(100) < claimRate.get();
        if (!claiming) return;

        claims++;
        TestLog.event("ground_claim", "fallDistance", fallen, "y", mc.player.getY(),
            "velocityY", mc.player.getDeltaMovement().y);
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Claims: " + claims);
    }
}
