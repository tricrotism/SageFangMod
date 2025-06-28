package com.tricrotism;

import com.tricrotism.config.Config;
import com.tricrotism.data.ServerInfo;
import com.tricrotism.data.ServerInfoCustomPayload;
import com.tricrotism.event.menu.MenuRegistrationEvent;
import com.tricrotism.features.commands.SFCommandManager;
import com.tricrotism.features.menus.MiscMenu;
import com.tricrotism.features.menus.PlayerInfoMenu;
import com.tricrotism.features.menus.ServerInfoMenu;
import com.tricrotism.features.menus.SettingsMenu;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class Main implements ModInitializer {
    public static ServerInfo lastServerInfo = null;
    public static final Logger LOGGER = LogManager.getLogger("SageFang");
    @Getter
    public static Config config = Config.create();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Mod");
        PayloadTypeRegistry.playS2C().register(ServerInfoCustomPayload.ID, ServerInfoCustomPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(
                ServerInfoCustomPayload.ID,
                (payload, ctx) -> lastServerInfo = payload.serverInfo()
        );

        MenuRegistrationEvent.INSTANCE.addListeners(it -> {
            it.register(new SettingsMenu());
            it.register(new ServerInfoMenu());
            it.register(new MiscMenu());
            it.register(new PlayerInfoMenu());
        });

        ClientCommandRegistrationCallback.EVENT.register(((SFCommandManager::register)));
    }

    public static void logReportMsg(@NotNull Throwable error) {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        String clazz = walker.getCallerClass().getSimpleName();
        String method = walker.walk(frames -> frames.skip(1).findFirst().orElseThrow().getMethodName());

        if (method.isBlank())
            method = error.getStackTrace()[0].getMethodName();

        LOGGER.error("[{}#{}] /!\\ Error! Full log file attached! /!\\", clazz, method, error);
    }

}