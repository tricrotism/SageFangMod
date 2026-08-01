package com.tricrotism;

import com.tricrotism.api.eventbus.EventBus;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.eventbus.IEventBus;
import com.tricrotism.config.ConfigPersistence;
import com.tricrotism.data.ServerInfo;
import com.tricrotism.data.ServerInfoCustomPayload;
import com.tricrotism.events.ui.MenuRegistrationEvent;
import com.tricrotism.features.commands.SFCommandManager;
import com.tricrotism.features.menus.InfoMenu;
import com.tricrotism.features.menus.LogMenu;
import com.tricrotism.features.menus.PlayerInfoMenu;
import com.tricrotism.features.menus.ServerInfoMenu;
import com.tricrotism.features.menus.SettingsMenu;
import com.tricrotism.modules.blink.Blink;
import com.tricrotism.modules.clientdetect.ClientDetect;
import com.tricrotism.modules.combat.AnchorMacro;
import com.tricrotism.modules.esp.AmethystESP;
import com.tricrotism.modules.esp.BlockEntityChunkESP;
import com.tricrotism.modules.esp.BlockUpdateESP;
import com.tricrotism.modules.esp.BlockEntityDataESP;
import com.tricrotism.modules.esp.BaseChunkESP;
import com.tricrotism.modules.esp.LightLevelESP;
import com.tricrotism.modules.esp.LightUpdateESP;
import com.tricrotism.modules.esp.RotatedDeepslateESP;
import com.tricrotism.modules.esp.SkyLightESP;
import com.tricrotism.modules.exploit.BlockWalk;
import com.tricrotism.modules.exploit.BoatNoClip;
import com.tricrotism.modules.exploit.ClickSlotFlood;
import com.tricrotism.modules.exploit.ElytraCast;
import com.tricrotism.modules.exploit.FallFlyingSpam;
import com.tricrotism.modules.exploit.GrimAirPlace;
import com.tricrotism.modules.exploit.GrimEntityBlink;
import com.tricrotism.modules.exploit.GrimFishingBlink;
import com.tricrotism.modules.exploit.LagExploit;
import com.tricrotism.modules.exploit.SpearStabSpam;
import com.tricrotism.modules.exploit.SpearSwap;
import com.tricrotism.modules.combat.DoubleAnchorMacro;
import com.tricrotism.modules.crash.*;
import com.tricrotism.modules.freecam.Freecam;
import com.tricrotism.modules.freelook.FreeLook;
import com.tricrotism.modules.ghost.GhostBlock;
import com.tricrotism.modules.items.ItemViewer;
import com.tricrotism.modules.logger.ChannelLogger;
import com.tricrotism.modules.logger.PayloadLogger;
import com.tricrotism.modules.logger.TeamDetector;
import com.tricrotism.modules.login.GameJoinSpoof;
import com.tricrotism.modules.login.HandshakeSpoofer;
import com.tricrotism.modules.login.LoginHelloSpoof;
import com.tricrotism.modules.login.LoginKeySpoof;
import com.tricrotism.modules.macros.ChatMacros;
import com.tricrotism.modules.math.MathChat;
import com.tricrotism.modules.misc.AllowInvalidChars;
import com.tricrotism.modules.misc.AutoDrinkSpam;
import com.tricrotism.modules.misc.ConnectionCut;
import com.tricrotism.modules.misc.GameStateBypass;
import com.tricrotism.modules.misc.DeathBypass;
import com.tricrotism.modules.misc.LaggySign;
import com.tricrotism.modules.misc.RealPlayerNames;
import com.tricrotism.modules.misc.S2CPacketDelayer;
import com.tricrotism.modules.packets.PacketManager;
import com.tricrotism.modules.world.FastMine;
import com.tricrotism.modules.world.SingleMine;
import com.tricrotism.modules.zoom.Zoom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;

public class SageFang implements ModInitializer {
    public static ServerInfo lastServerInfo = null;
    public static final Logger LOGGER = LogManager.getLogger("SageFang");
    public static final IEventBus EVENT_BUS = new EventBus();
    public static final String MOD_ID = "sagefang";
    public static final int MAX_LENGTH_MINUS_ONE = Integer.MAX_VALUE - 1;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Mod");

        io.avaje.config.Config.loadIntoSystemProperties();
        ConfigPersistence.init();

        PayloadTypeRegistry.clientboundPlay().register(ServerInfoCustomPayload.ID, ServerInfoCustomPayload.CODEC);
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
        EVENT_BUS.subscribe(Zoom.instance);
        EVENT_BUS.subscribe(FreeLook.instance);
        EVENT_BUS.subscribe(Freecam.instance);
        EVENT_BUS.subscribe(MathChat.instance);
        EVENT_BUS.subscribe(ClientDetect.instance);
        EVENT_BUS.subscribe(DeathBypass.instance);
        EVENT_BUS.subscribe(AutoDrinkSpam.instance);
        EVENT_BUS.subscribe(AnchorMacro.instance);
        EVENT_BUS.subscribe(DoubleAnchorMacro.instance);
        EVENT_BUS.subscribe(SingleMine.instance);
        EVENT_BUS.subscribe(FastMine.instance);
        EVENT_BUS.subscribe(AmethystESP.instance);
        EVENT_BUS.subscribe(RotatedDeepslateESP.instance);
        EVENT_BUS.subscribe(BaseChunkESP.instance);
        EVENT_BUS.subscribe(LightLevelESP.instance);
        EVENT_BUS.subscribe(FallFlyingSpam.instance);
        EVENT_BUS.subscribe(BlockWalk.instance);
        EVENT_BUS.subscribe(SpearStabSpam.instance);
        EVENT_BUS.subscribe(GrimAirPlace.instance);
        EVENT_BUS.subscribe(SpearSwap.instance);
        EVENT_BUS.subscribe(ElytraCast.instance);
        EVENT_BUS.subscribe(TeamDetector.instance);
        EVENT_BUS.subscribe(BoatNoClip.instance);
        EVENT_BUS.subscribe(ChannelLogger.instance);
        EVENT_BUS.subscribe(PositionCrash.instance);
        EVENT_BUS.subscribe(BookCrash.instance);
        EVENT_BUS.subscribe(CreativeExploit.instance);
        EVENT_BUS.subscribe(PacketFlood.instance);
        EVENT_BUS.subscribe(KickCrash.instance);

        SFCommandManager.init();
    }

    @EventHandler
    private void onMenuRegistration(MenuRegistrationEvent event) {
        event.registerAll(
            new SettingsMenu(),
            new ServerInfoMenu(),
            new InfoMenu(),
            new PlayerInfoMenu(),
            new LogMenu(),
            SkillCrash.instance,
            OffhandCrash.instance,
            Blink.instance,
            PacketManager.instance,
            ItemViewer.instance,
            ChatMacros.instance,
            GhostBlock.instance,
            Zoom.instance,
            FreeLook.instance,
            Freecam.instance,
            MathChat.instance,
            PositionCrash.instance,
            BookCrash.instance,
            CreativeExploit.instance,
            PacketFlood.instance,
            KickCrash.instance,
            ClientDetect.instance,
            DeathBypass.instance,
            AutoDrinkSpam.instance,
            ConnectionCut.instance,
            AllowInvalidChars.instance,
            HandshakeSpoofer.instance,
            LoginHelloSpoof.instance,
            GameJoinSpoof.instance,
            LoginKeySpoof.instance,
            AnchorMacro.instance,
            DoubleAnchorMacro.instance,
            RealPlayerNames.instance,
            LaggySign.instance,
            SingleMine.instance,
            FastMine.instance,
            GameStateBypass.instance,
            S2CPacketDelayer.instance,
            BlockUpdateESP.instance,
            BlockEntityChunkESP.instance,
            AmethystESP.instance,
            RotatedDeepslateESP.instance,
            LightUpdateESP.instance,
            BlockEntityDataESP.instance,
            BaseChunkESP.instance,
            LightLevelESP.instance,
            SkyLightESP.instance,
            FallFlyingSpam.instance,
            BlockWalk.instance,
            SpearStabSpam.instance,
            GrimEntityBlink.instance,
            GrimFishingBlink.instance,
            GrimAirPlace.instance,
            SpearSwap.instance,
            LagExploit.instance,
            ElytraCast.instance,
            ClickSlotFlood.instance,
            TeamDetector.instance,
            BoatNoClip.instance,
            PayloadLogger.instance,
            ChannelLogger.instance
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
