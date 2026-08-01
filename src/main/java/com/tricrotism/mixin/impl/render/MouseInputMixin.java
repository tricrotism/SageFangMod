package com.tricrotism.mixin.impl.render;

import com.tricrotism.modules.freecam.Freecam;
import com.tricrotism.modules.freelook.FreeLook;
import com.tricrotism.modules.zoom.Zoom;
import imgui.ImGui;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extension mixin on {@code MouseHandler} for zoom scroll interception
 * and free look turn suppression.
 * <p>
 * Separate from the existing {@code MouseMixin} (which handles ImGui capture)
 * to keep feature concerns isolated.
 */
@Mixin(MouseHandler.class)
public class MouseInputMixin {

    /**
     * When zoom is engaged with scroll-to-zoom enabled, intercept the scroll
     * event and feed it to the zoom module instead of changing the hotbar slot.
     */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void sagefang$handleZoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ImGui.getIO().getWantCaptureMouse()) return;
        if (Zoom.instance.isZoomEngaged() && Zoom.instance.isScrollToZoom()) {
            Zoom.instance.handleScroll(vertical);
            ci.cancel();
        }
    }

    /**
     * When free look is engaged, redirect the {@code LocalPlayer.turn()} call
     * to accumulate mouse movement onto the free camera instead of rotating
     * the player's body.
     */
    @Redirect(
        method = "turnPlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void sagefang$redirectTurn(LocalPlayer player, double yRot, double xRot) {
        if (FreeLook.instance.isFreeLookEngaged()) {
            FreeLook.instance.handleMouseDelta(yRot, xRot);
            return;
        }
        if (Freecam.instance.isEngaged()) {
            Freecam.instance.handleMouseDelta(yRot, xRot);
            return;
        }
        player.turn(yRot, xRot);
    }
}
