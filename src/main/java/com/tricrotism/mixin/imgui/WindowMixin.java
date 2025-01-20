package com.tricrotism.mixin.imgui;

import com.tricrotism.Main;
import com.tricrotism.event.menu.MenuRegistrationEvent;
import com.tricrotism.event.menu.MenuRegistrationEventArgs;
import imgui.ImGui;
import net.minecraft.client.WindowEventHandler;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.util.MonitorTracker;
import net.minecraft.client.util.Window;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    void onWindowCreate(WindowEventHandler eventHandler, MonitorTracker monitorTracker, WindowSettings settings, @Nullable String fullscreenVideoMode, String title, CallbackInfo ci){
        ImGui.init();
        MenuRegistrationEventArgs args = new MenuRegistrationEventArgs();
        MenuRegistrationEvent.INSTANCE.invoke(args);
        Main.MENUS.addAll(args.getMenus());
        ImGui.createContext();
    }

    @Inject(method = "close", at = @At("HEAD"))
    void onWindowDispose(CallbackInfo ci){
        Main.imGuiGl3.shutdown();
        Main.imGuiGlfw.shutdown();
        ImGui.destroyContext();
    }
}