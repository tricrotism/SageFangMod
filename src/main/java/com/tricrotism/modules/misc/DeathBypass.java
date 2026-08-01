package com.tricrotism.modules.misc;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.modules.Module;
import com.tricrotism.events.game.GameJoinedEvent;
import com.tricrotism.events.world.TickEvent;
import com.tricrotism.utils.SFLog;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import net.minecraft.client.gui.screens.DeathScreen;

/**
 * Keep playing after you die: cancels the death screen and pins your health
 * (and optionally food) client-side, so you stay in the world as a "ghost"
 * until you disable the module (which sends a respawn request). Ported from the
 * Meteor addon's ghost-mode.
 */
public final class DeathBypass extends Module implements Menu {

    public static final DeathBypass instance = new DeathBypass();

    private boolean ghosting;
    private boolean fullFood;

    private DeathBypass() {
        super("ghostmode", "Ghost Mode", "Keep playing after you die (client-side).", "Utility");
        fullFood = Config.getBool(baseConfig + ".fullfood", true);
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        ghosting = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || mc.player == null) return;

        if (mc.screen instanceof DeathScreen) {
            mc.setScreen(null);
            if (!ghosting) {
                ghosting = true;
                SFLog.log(title, "Ghost mode engaged, you kept playing after death.");
            }
        }

        if (!ghosting) return;
        if (mc.player.getHealth() < 1f) mc.player.setHealth(20f);
        if (fullFood && mc.player.getFoodData().getFoodLevel() < 20) {
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
    public void frame(ImGuiIO io) {
        if (!isVisible()) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##ghostModeEnabled", isActive())) toggle();
        if (ImGui.checkbox("Full Food##ghostModeFood", fullFood)) {
            fullFood = !fullFood;
            Config.setProperty(baseConfig + ".fullfood", String.valueOf(fullFood));
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Also pin food to max while ghosting.");

        if (ghosting) ImGui.textDisabled("Currently ghosting");

        ImGui.end();
    }
}
