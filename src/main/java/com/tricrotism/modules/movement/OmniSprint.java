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

/**
 * Keeps the sprint state on in directions vanilla will not sprint in.
 * <p>
 * The module every client ships under the name Sprint: vanilla only sprints while the input has a
 * forward component and drops it the moment that goes, so strafing and walking backwards cost a
 * third of the speed. Forcing the flag on regardless is a free thirty percent in every direction,
 * and it is the cheapest movement cheat there is because it never touches a position: the client
 * simply keeps a boolean set that vanilla would have cleared.
 * <p>
 * That is also its weakness, and what this module exists to demonstrate: the keys travel to the
 * server in one packet and the sprint state in another, so a server that reads both can see the
 * player sprinting with nothing pressed that a sprint could come from.
 */
public final class OmniSprint extends Module {

    public static final OmniSprint instance = new OmniSprint();

    private final Settings.Bool whileSneaking =
        bool("While Sneaking", "whileSneaking", "Keep sprinting even while crouched", false);

    private int forcedTicks;

    private OmniSprint() {
        super("omnisprint", "Omni Sprint", "Sprint in every direction, which vanilla refuses to do.",
            Category.EXPLOIT);
    }

    @Override
    public void onActivate() {
        forcedTicks = 0;
        TestLog.event("omnisprint_enable", "whileSneaking", whileSneaking.get());
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
            // The forced starts never touched wasSprinting, so vanilla will not send the stop itself.
            var connection = mc.getConnection();
            if (forcedTicks > 0 && connection != null) {
                connection.send(new ServerboundPlayerCommandPacket(
                    mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            }
        }
        TestLog.event("omnisprint_disable", "forcedTicks", forcedTicks);
    }

    /**
     * Asserts the sprint to the server rather than only to the local entity.
     * <p>
     * Setting the flag client-side alone achieves nothing: vanilla clears it again inside the same
     * tick, before the comparison that would have sent the packet, so the server never hears about
     * it and never applies the speed. What the real module ends up doing, whatever route it takes
     * to get there, is leaving the *server* believing the player is sprinting, because that is
     * where the thirty percent is applied. So this sends the state change directly and keeps
     * re-sending it, which is the smallest faithful version of the attack.
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null || mc.getConnection() == null) return;

        boolean anyInput = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
            || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        if (!anyInput) return;
        if (mc.player.isShiftKeyDown() && !whileSneaking.get()) return;

        mc.player.setSprinting(true);
        if (forcedTicks % RESEND_TICKS == 0) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(
                mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
        forcedTicks++;
    }

    /**
     * Vanilla keeps clearing the state, so the claim is renewed on a cycle rather than once.
     */
    private static final int RESEND_TICKS = 4;

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.textUnformatted("Forced sprint ticks: " + forcedTicks);
    }
}
