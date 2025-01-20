package com.tricrotism;

import com.tricrotism.data.ServerInfo;
import com.tricrotism.data.ServerInfoCustomPayload;
import com.tricrotism.event.menu.MenuRegistrationEvent;
import com.tricrotism.menus.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Main implements ModInitializer {
    public static ServerInfo lastServerInfo = null;
    public static final Logger LOGGER = LogManager.getLogger("SageFang");
    public static final List<Menu> MENUS = new ArrayList<>();
    public static String glslVersion = "#version 110";
    public static final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    public static final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Mod");
        PayloadTypeRegistry.playS2C().register(ServerInfoCustomPayload.ID, ServerInfoCustomPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(
            ServerInfoCustomPayload.ID,
            (payload, ctx) -> lastServerInfo = payload.getServerInfo()
        );

        MenuRegistrationEvent.INSTANCE.addListener(it -> {
            it.register(new ServerInfoMenu());
        });
    }
}
