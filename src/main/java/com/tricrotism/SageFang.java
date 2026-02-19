package com.tricrotism;

import com.tricrotism.api.eventbus.EventBus;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.eventbus.IEventBus;
import com.tricrotism.config.ConfigPersistence;
import com.tricrotism.data.ServerInfo;
import com.tricrotism.data.ServerInfoCustomPayload;
import com.tricrotism.events.ui.MenuRegistrationEvent;
import com.tricrotism.features.commands.SFCommandManager;
import com.tricrotism.features.menus.MiscMenu;
import com.tricrotism.features.menus.InfoMenu;
import com.tricrotism.features.menus.PlayerInfoMenu;
import com.tricrotism.features.menus.ServerInfoMenu;
import com.tricrotism.features.menus.SettingsMenu;
import com.tricrotism.modules.crash.SkillCrash;
import com.tricrotism.modules.crash.OffhandCrash;
import com.tricrotism.modules.blink.Blink;
import com.tricrotism.modules.packets.PacketManager;
import com.tricrotism.modules.items.ItemViewer;
import com.tricrotism.modules.macros.ChatMacros;
import com.tricrotism.modules.ghost.GhostBlock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.Packet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class SageFang implements ModInitializer {
    public static ServerInfo lastServerInfo = null;
    public static final Logger LOGGER = LogManager.getLogger("SageFang");
    public static final IEventBus EVENT_BUS = new EventBus();
    public static final String MOD_ID = "sagefang";
    public static final int MAX_LENGTH_MINUS_ONE = Integer.MAX_VALUE - 1;
    public static final List<Packet<?>> delayedUIPackets = new ArrayList<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Mod");

        io.avaje.config.Config.loadIntoSystemProperties();
        ConfigPersistence.init();

        PayloadTypeRegistry.playS2C().register(ServerInfoCustomPayload.ID, ServerInfoCustomPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(
                ServerInfoCustomPayload.ID,
                (payload, ctx) -> lastServerInfo = payload.serverInfo()
        );

        EVENT_BUS.registerLambdaFactory("com.tricrotism", (lookupInMethod, clazz) ->
                (MethodHandles.Lookup) lookupInMethod.invoke(null, clazz, MethodHandles.lookup()));

        EVENT_BUS.subscribe(this);
        EVENT_BUS.subscribe(SkillCrash.instance);
        EVENT_BUS.subscribe(OffhandCrash.instance);
        EVENT_BUS.subscribe(Blink.instance);
        EVENT_BUS.subscribe(PacketManager.instance);
        EVENT_BUS.subscribe(ItemViewer.instance);
        EVENT_BUS.subscribe(ChatMacros.instance);
        EVENT_BUS.subscribe(GhostBlock.instance);

        ClientCommandRegistrationCallback.EVENT.register(((SFCommandManager::register)));
    }

    @EventHandler
    private void onMenuRegistration(MenuRegistrationEvent event) {
        event.registerAll(
                new SettingsMenu(),
                new ServerInfoMenu(),
                new InfoMenu(),
                new MiscMenu(),
                new PlayerInfoMenu(),
                SkillCrash.instance,
                OffhandCrash.instance,
                Blink.instance,
                PacketManager.instance,
                ItemViewer.instance,
                ChatMacros.instance,
                GhostBlock.instance
        );
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
