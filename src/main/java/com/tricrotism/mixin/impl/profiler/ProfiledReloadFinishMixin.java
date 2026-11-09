package com.tricrotism.mixin.impl.profiler;

import com.tricrotism.modules.profiler.ReloadProfiler;
import net.minecraft.server.packs.resources.ProfiledReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hands each reload listener's prepare and apply time to {@link ReloadProfiler} when the reload ends.
 */
@Mixin(ProfiledReloadInstance.class)
public class ProfiledReloadFinishMixin {

    @Inject(method = "finish", at = @At("HEAD"))
    private void sagefang$finish(List<ProfiledReloadInstance.State> states, CallbackInfoReturnable<List<ProfiledReloadInstance.State>> cir) {
        List<String> names = new ArrayList<>(states.size());
        List<Long> prepare = new ArrayList<>(states.size());
        List<Long> apply = new ArrayList<>(states.size());
        for (ProfiledReloadInstance.State state : states) {
            names.add(state.name());
            prepare.add(state.preparationNanos().get());
            apply.add(state.reloadNanos().get());
        }
        ReloadProfiler.finish(names, prepare, apply);
    }
}
