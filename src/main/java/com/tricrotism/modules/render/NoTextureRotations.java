package com.tricrotism.modules.render;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;

/**
 * Pins the per-position block model seed, so randomly rotated textures like stone, dirt and grass
 * tops render the same everywhere. A screenshot then has no rotation pattern that someone could
 * match back to coordinates.
 */
public final class NoTextureRotations extends Module {

    public static final NoTextureRotations instance = new NoTextureRotations();

    // Read from section compile threads, so a volatile static rather than isActive().
    private static volatile boolean uniform;

    private NoTextureRotations() {
        super("notexturerotations", "No Texture Rotations",
            "Render random block textures with one fixed rotation.", Category.RENDER);
    }

    public static boolean isUniform() {
        return uniform;
    }

    @Override
    public void onActivate() {
        uniform = true;
        rebuildTerrain();
    }

    @Override
    public void onDeactivate() {
        uniform = false;
        rebuildTerrain();
    }

    private void rebuildTerrain() {
        // Already-compiled sections keep their old seeds until recompiled. Skipped during client shutdown.
        if (mc.level == null || !mc.isRunning()) return;
        mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
    }
}
