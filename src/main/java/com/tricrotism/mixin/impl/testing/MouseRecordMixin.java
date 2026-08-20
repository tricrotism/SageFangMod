package com.tricrotism.mixin.impl.testing;

import com.tricrotism.modules.testing.InputRecorder;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds raw mouse presses to {@link InputRecorder}.
 * <p>
 * Kept separate from the ImGui-capture and free-look mixins on the same class, as those already are
 * from each other. Injected at the head and never cancels: a recording that changed what the click
 * did would not be a recording of the click.
 */
@Mixin(MouseHandler.class)
public class MouseRecordMixin {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void sagefang$recordClick(long window, MouseButtonInfo mouseButtonInfo, int action, CallbackInfo ci) {
        InputRecorder.instance.onMouseButton(mouseButtonInfo.button(), action);
    }
}
