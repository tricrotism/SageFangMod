package com.tricrotism.modules.misc;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.SFLog;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.gui.screens.DeathScreen;

/**
 * Keep playing after you die: cancels the death screen and pins your health
 * (and optionally food) client-side, so you stay in the world as a "ghost"
 * until you disable the module (which sends a respawn request). Ported from the
 * Meteor addon's ghost-mode.
 */
public final class DeathBypass extends Module {

    public static final DeathBypass instance = new DeathBypass();

    private final Settings.Bool fullFood =
        bool("Full Food", "fullfood", "Also pin food to max while ghosting", true);

    private boolean ghosting;

    private DeathBypass() {
        super("ghostmode", "Ghost Mode", "Keep playing after you die (client-side).", Category.UTILITY);
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        ghosting = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;

        if (mc.gui.screen() instanceof DeathScreen) {
            mc.gui.setScreen(null);
            if (!ghosting) {
                ghosting = true;
                SFLog.log(title, "Ghost mode engaged, you kept playing after death.");
            }
        }

        if (!ghosting) return;
        if (mc.player.getHealth() < 1f) mc.player.setHealth(20f);
        if (fullFood.get() && mc.player.getFoodData().getFoodLevel() < 20) {
            mc.player.getFoodData().setFoodLevel(20);
        }
    }

    @Override
    public void onDeactivate() {
        if (!ghosting) return;
        ghosting = false;
        SFLog.log(title, "Ghost mode disabled, sending respawn request.");
        if (mc.player != null && mc.player.connection != null) {
            mc.player.respawn();
        }
    }

    @Override
    protected void renderExtra(ImGuiIO io) {
        if (ghosting) ImGui.textDisabled("Currently ghosting");
    }
}
