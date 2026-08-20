package com.tricrotism.modules.movement;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

/**
 * Starts sprinting partway through an item use, which a vanilla client refuses to do.
 * <p>
 * Hold right-click on anything continuous (food, a bow, a shield) and this emits the sprint
 * transition the client would have suppressed. Nothing else about the use is touched, so the
 * {@code USE_ITEM} and {@code RELEASE_USE_ITEM} packets that bracket it are the player's own.
 * <p>
 * {@link #sprintAfter} is the knob to sweep. A check that ignores transitions near either end of the
 * use, where a held sprint key and a starting use genuinely race, has a margin at both ends, and
 * walking this value from zero upward finds where it stops forgiving and starts judging.
 */
public final class NoSlow extends Module {

    public static final NoSlow instance = new NoSlow();

    private final Settings.Int sprintAfter =
        integer("Sprint After (ms)", "sprintAfter", "Milliseconds into the use before sprinting", 250, 0, 1000);
    private final Settings.Bool repeat =
        bool("Repeat", "repeat", "Re-send the transition every second of a long use", false);
    private final Settings.Bool heldItemReset =
        bool("Held Item Reset", "heldItemReset",
            "Send a hotbar reselect after sprinting, which clears the server's record of the use", false);

    private long useStartedMs;
    private long lastSentMs;
    private int transitions;

    private NoSlow() {
        super("noslow", "No Slow", "Sprint through an item use; sweeps the sprint-through-use check.",
            Category.EXPLOIT);
    }

    @Override
    public void onActivate() {
        transitions = 0;
        useStartedMs = 0L;
        TestLog.event("noslow_enable", "sprintAfter", sprintAfter.get(), "repeat", repeat.get());
    }

    @Override
    public void onDeactivate() {
        useStartedMs = 0L;
        TestLog.event("noslow_disable", "transitions", transitions);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null || mc.getConnection() == null) return;

        if (!mc.player.isUsingItem()) {
            useStartedMs = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (useStartedMs == 0L) {
            useStartedMs = now;
            lastSentMs = 0L;
        }

        long intoUse = now - useStartedMs;
        if (intoUse < sprintAfter.get()) return;
        if (lastSentMs != 0L && (!repeat.get() || now - lastSentMs < REPEAT_INTERVAL_MS)) return;

        mc.getConnection().send(new ServerboundPlayerCommandPacket(
            mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        lastSentMs = now;
        transitions++;

        // A held-item change is taken as the end of the use, so the sprint that just went out is no
        // longer inside one and the check has nothing left to judge. Reselecting the slot already
        // held is a no-op for the player and a full reset for the detector.
        if (heldItemReset.get()) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(
                mc.player.getInventory().getSelectedSlot()));
        }

        TestLog.event("sprint_through_use",
            "intoUseMs", intoUse,
            "item", mc.player.getUseItem().getItem().toString(),
            "ticksUsingItem", mc.player.getTicksUsingItem());
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Transitions: " + transitions);
    }

    private static final long REPEAT_INTERVAL_MS = 1000L;
}
