package com.tricrotism.modules.combat;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.testing.TestLog;
import com.tricrotism.events.world.TickEvent;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * Deals extra knockback by dropping sprint at the moment of the hit.
 * <p>
 * A sprinting attacker deals more knockback, and vanilla clears the sprint flag when the hit lands,
 * so a player who stops sprinting on the same tick and starts again immediately gets the bonus on
 * every swing instead of every first swing. Doing it with the keyboard is the old w-tap; doing it
 * with two packets and no key is this, and the difference is visible in the traffic rather than in
 * the damage.
 * <p>
 * Nothing checks it today, but it is cheap to build and it is the packet-level family most likely to
 * be caught as a side effect of something else: the transitions arrive in a fixed relationship to
 * every attack, which is a pattern the way {@code combat/FakeClose} keys on a pattern, not an
 * impossibility. A ratio of sprint transitions to attacks would find this the same way.
 * <p>
 * The stop is emitted before the interaction rather than after, which is the whole mechanism. The
 * hook runs at the head of the send path, so the packet ordering on the wire matches the ordering
 * here, and a restore that arrived first would simply undo the effect.
 */
public final class SuperKnockback extends Module {

    public static final SuperKnockback instance = new SuperKnockback();

    private final Settings.Int restoreDelay =
        integer("Restore Delay", "restoreDelay", "Ticks before sprint is started again", 1, 1, 5);

    private int restoreIn;
    private int taps;

    private SuperKnockback() {
        super("superknockback", "Super Knockback", "Drop sprint on each hit for extra knockback.",
            Category.COMBAT);
    }

    /**
     * Called on the outbound send path before the packet is written. Interactions that are not
     * attacks also pass through here; the extra transition is harmless and reading the action off
     * the packet would cost a visitor dispatch on every send.
     */
    public void onOutbound(Packet<?> packet) {
        if (!isActive() || !(packet instanceof ServerboundInteractPacket)) return;
        if (mc.player == null || mc.getConnection() == null) return;

        mc.getConnection().send(new ServerboundPlayerCommandPacket(
            mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        restoreIn = restoreDelay.get();
        taps++;

        TestLog.event("superknockback_tap", "restoreDelay", restoreDelay.get());
    }

    @Override
    public void onActivate() {
        taps = 0;
        restoreIn = 0;
        TestLog.event("superknockback_enable", "restoreDelay", restoreDelay.get());
    }

    @Override
    public void onDeactivate() {
        restoreIn = 0;
        TestLog.event("superknockback_disable", "taps", taps);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (restoreIn == 0 || mc.player == null || mc.getConnection() == null) return;
        if (--restoreIn > 0) return;

        mc.getConnection().send(new ServerboundPlayerCommandPacket(
            mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        ImGui.text("Taps: " + taps);
    }
}
