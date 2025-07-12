package com.tricrotism;

import com.tricrotism.config.Config;
import com.tricrotism.data.ServerInfo;
import com.tricrotism.data.ServerInfoCustomPayload;
import com.tricrotism.eventbus.EventBus;
import com.tricrotism.eventbus.IEventBus;
import com.tricrotism.features.commands.SFCommandManager;
import com.tricrotism.features.crash.skill.SkillCrashEvents;
import com.tricrotism.features.menus.*;
import com.tricrotism.utils.event.menu.MenuRegistrationEvent;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class Main implements ModInitializer {
    public static ServerInfo lastServerInfo = null;
    public static final Logger LOGGER = LogManager.getLogger("SageFang");
    public static final IEventBus EVENT_BUS = new EventBus();
    public static final String MOD_ID = "sagefang";
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

        EVENT_BUS.subscribe(this);

        List<Class<?>> eventClasses = new ArrayList<>();
        eventClasses.add(SkillCrashEvents.class);

        eventClasses.forEach((entry) -> {
            try {
                EVENT_BUS.registerLambdaFactory(entry.getPackageName(), (lookupInMethod, clazz) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, clazz, MethodHandles.lookup()));
                Object instance = entry.getDeclaredConstructor().newInstance();
                EVENT_BUS.subscribe(instance);
                
                LOGGER.info("Successfully registered event handler: {}", entry.getSimpleName());
            } catch (Exception e) {
                throw new RuntimeException("Something went wrong loading \"%s\"!".formatted(entry.getSimpleName()), e);
            }
        });

        MenuRegistrationEvent.INSTANCE.addListeners(it -> {
            it.registerAll(new SettingsMenu(),
                    new ServerInfoMenu(),
                    new MiscMenu(),
                    new PlayerInfoMenu(),
                    new SkillCrashMenu()
            );
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