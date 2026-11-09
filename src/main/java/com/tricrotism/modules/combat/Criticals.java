package com.tricrotism.modules.combat;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;

/**
 * Makes every hit a critical without leaving the ground.
 * <p>
 * A critical needs the attacker to be falling, so the module tells the server it is. Two shapes ship
 * in the corpus and both are here: the packet form, which simply reports being airborne while the
 * player stands still, and the hop form, which sends a tiny rise and fall around each attack so the
 * claim has a matching position.
 * <p>
 * It is the cheapest damage multiplier in any client: fifty percent, permanently, for one boolean,
 * which is why it is switched on by default in most of them.
 * <p>
 * The packet form is the interesting one to test against, because nothing about the *positions* is
 * wrong: the player really is where they say they are. The only thing wrong is a flag, and a server
 * that recomputes the flag rather than believing it has the whole cheat in one comparison.
 */
public final class Criticals extends Module {

    public static final Criticals instance = new Criticals();

    private final Settings.Mode mode =
        mode("Mode", "mode", "How the fall is manufactured", 0, "Packet", "Micro Hop");
    private final Settings.Decimal hop =
        decimal("Hop Height", "hop", "Rise sent in Micro Hop mode", 0.0625, 0.01, 0.4);

    private static volatile boolean claimingAirborne;

    private int ticks;
    private int spoofed;

    private Criticals() {
        super("criticals", "Criticals", "Report a fall that is not happening so every hit crits.",
            Category.EXPLOIT);
    }

    /**
     * Read by the ground mixin, which flips the transmitted flag without touching real state.
     */
    public static boolean isClaimingAirborne() {
        return claimingAirborne;
    }

    @Override
    public void onActivate() {
        ticks = 0;
        spoofed = 0;
        TestLog.event("criticals_enable", "mode", mode.option());
    }

    @Override
    public void onDeactivate() {
        claimingAirborne = false;
        TestLog.event("criticals_disable", "spoofed", spoofed);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) {
            claimingAirborne = false;
            return;
        }

        // Only worth doing while actually on the ground: in the air the hit already crits.
        if (!mc.player.onGround()) {
            claimingAirborne = false;
            return;
        }

        ticks++;
        if (mode.get() == 0) {
            claimingAirborne = true;
            spoofed++;
            return;
        }

        // Micro hop: a rise and a fall small enough to be invisible, on a cycle.
        claimingAirborne = true;
        if (ticks % 4 == 0) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, hop.get(), mc.player.getDeltaMovement().z);
            spoofed++;
        }
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textUnformatted("Spoofed ticks: " + spoofed);
    }
}
