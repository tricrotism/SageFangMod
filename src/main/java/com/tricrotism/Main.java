package com.tricrotism;

import com.tricrotism.data.ServerInfo;
import com.tricrotism.data.ServerInfoCustomPayload;
import com.tricrotism.event.menu.MenuRegistrationEvent;
import com.tricrotism.menus.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main implements ModInitializer {
    public static ServerInfo lastServerInfo = null;
    public static final Logger LOGGER = LogManager.getLogger("SageFang");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Mod");
        PayloadTypeRegistry.playS2C().register(ServerInfoCustomPayload.ID, ServerInfoCustomPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(
            ServerInfoCustomPayload.ID,
            (payload, ctx) -> lastServerInfo = payload.getServerInfo()
        );

        MenuRegistrationEvent.INSTANCE.addListeners(it -> {
            it.register(new ServerInfoMenu());
            it.register(new UiUtilsMenu());
        });
    }
}
