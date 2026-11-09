package com.tricrotism.modules.render;

import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.events.world.TickEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Maximum brightness everywhere. Lightmap mode forces the lightmap white at extraction time and
 * touches no game state. Night Vision mode gives the local player a client-only night vision effect.
 */
public final class FullBright extends Module {

    public static final FullBright instance = new FullBright();

    private static final int LIGHTMAP = 0;

    private final Settings.Mode mode =
        mode("Mode", "mode", "How brightness is forced", LIGHTMAP, "Lightmap", "Night Vision");

    // The effect this module added, so removal never strips a server-sent night vision.
    private MobEffectInstance applied;

    private FullBright() {
        super("fullbright", "Full Bright", "Render everything at full brightness.", Category.RENDER);
    }

    public boolean forcesLightmap() {
        return mode.get() == LIGHTMAP;
    }

    @Override
    public void onDeactivate() {
        removeApplied();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;

        if (forcesLightmap()) {
            removeApplied();
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || player.hasEffect(MobEffects.NIGHT_VISION)) return;

        applied = new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false);
        player.addEffect(applied);
    }

    private void removeApplied() {
        LocalPlayer player = mc.player;
        if (applied != null && player != null && player.getEffect(MobEffects.NIGHT_VISION) == applied) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        applied = null;
    }
}
