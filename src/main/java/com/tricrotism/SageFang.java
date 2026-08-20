package com.tricrotism;

import com.tricrotism.api.eventbus.EventBus;
import com.tricrotism.api.eventbus.EventHandler;
import com.tricrotism.api.eventbus.IEventBus;
import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.rotation.RotationManager;
import com.tricrotism.config.ConfigPersistence;
import com.tricrotism.data.ServerInfo;
import com.tricrotism.data.ServerInfoCustomPayload;
import com.tricrotism.events.game.GameQuitEvent;
import com.tricrotism.events.ui.MenuRegistrationEvent;
import com.tricrotism.features.commands.SFCommandManager;
import com.tricrotism.features.menus.*;
import com.tricrotism.modules.blink.Blink;
import com.tricrotism.modules.clientdetect.ClientDetect;
import com.tricrotism.modules.combat.*;
import com.tricrotism.modules.crash.*;
import com.tricrotism.modules.esp.*;
import com.tricrotism.modules.exploit.*;
import com.tricrotism.modules.freecam.Freecam;
import com.tricrotism.modules.freelook.FreeLook;
import com.tricrotism.modules.ghost.GhostBlock;
import com.tricrotism.modules.inventory.AutoTotem;
import com.tricrotism.modules.items.ItemViewer;
import com.tricrotism.modules.latency.BadPackets;
import com.tricrotism.modules.latency.ForgedAck;
import com.tricrotism.modules.latency.PingSpoof;
import com.tricrotism.modules.latency.Timer;
import com.tricrotism.modules.logger.ChannelLogger;
import com.tricrotism.modules.logger.PayloadLogger;
import com.tricrotism.modules.logger.TeamDetector;
import com.tricrotism.modules.login.GameJoinSpoof;
import com.tricrotism.modules.login.HandshakeSpoofer;
import com.tricrotism.modules.login.LoginHelloSpoof;
import com.tricrotism.modules.login.LoginKeySpoof;
import com.tricrotism.modules.macros.ChatMacros;
import com.tricrotism.modules.math.MathChat;
import com.tricrotism.modules.misc.*;
import com.tricrotism.modules.movement.*;
import com.tricrotism.modules.packets.PacketManager;
import com.tricrotism.modules.profiler.FrameProfiler;
import com.tricrotism.modules.testing.InputRecorder;
import com.tricrotism.modules.testing.InputReplay;
import com.tricrotism.modules.testing.JoinBurst;
import com.tricrotism.modules.testing.RefusalMonitor;
import com.tricrotism.modules.world.FastMine;
import com.tricrotism.modules.world.Nuker;
import com.tricrotism.modules.world.Scaffold;
import com.tricrotism.modules.world.SingleMine;
import com.tricrotism.modules.zoom.Zoom;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.util.List;

public class SageFang implements ClientModInitializer {
    public static volatile ServerInfo lastServerInfo = null;
    public static final Logger LOGGER = LogManager.getLogger("SageFang");
    public static final IEventBus EVENT_BUS = new EventBus();
    public static final String MOD_ID = "sagefang";
    public static final int MAX_LENGTH_MINUS_ONE = Integer.MAX_VALUE - 1;

    private List<Menu> menus;

    @Override
    public void onInitializeClient() {
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
        EVENT_BUS.subscribe(RotationManager.instance);
        EVENT_BUS.subscribe(SkillCrash.instance);
        EVENT_BUS.subscribe(OffhandCrash.instance);
        EVENT_BUS.subscribe(Blink.instance);
        EVENT_BUS.subscribe(PacketManager.instance);
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
        EVENT_BUS.subscribe(KillAura.instance);
        EVENT_BUS.subscribe(Backtrack.instance);
        EVENT_BUS.subscribe(AutoTotem.instance);
        EVENT_BUS.subscribe(NoFall.instance);
        EVENT_BUS.subscribe(NoSlow.instance);
        EVENT_BUS.subscribe(Nuker.instance);
        EVENT_BUS.subscribe(Scaffold.instance);
        EVENT_BUS.subscribe(InputReplay.instance);
        EVENT_BUS.subscribe(TPAura.instance);
        EVENT_BUS.subscribe(SuperKnockback.instance);
        EVENT_BUS.subscribe(Clip.instance);
        EVENT_BUS.subscribe(Speed.instance);
        EVENT_BUS.subscribe(HighJump.instance);
        EVENT_BUS.subscribe(Fly.instance);
        EVENT_BUS.subscribe(Exempt.instance);
        EVENT_BUS.subscribe(JoinBurst.instance);
        EVENT_BUS.subscribe(RefusalMonitor.instance);
        EVENT_BUS.subscribe(BadPackets.instance);
        EVENT_BUS.subscribe(SingleMine.instance);
        EVENT_BUS.subscribe(FastMine.instance);
        EVENT_BUS.subscribe(AmethystESP.instance);
        EVENT_BUS.subscribe(BlockUpdateESP.instance);
        EVENT_BUS.subscribe(BlockEntityChunkESP.instance);
        EVENT_BUS.subscribe(BlockEntityDataESP.instance);
        EVENT_BUS.subscribe(LightUpdateESP.instance);
        EVENT_BUS.subscribe(SkyLightESP.instance);
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
    private void onGameQuit(GameQuitEvent event) {
        lastServerInfo = null;
    }

    /**
     * Posted every frame. The menu list is a fixed set of singletons plus the five
     * standalone screens, so it is built once and handed over as-is.
     */
    @EventHandler
    private void onMenuRegistration(MenuRegistrationEvent event) {
        if (menus == null) {
            menus = List.of(
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
                KillAura.instance,
                Backtrack.instance,
                AutoTotem.instance,
                NoFall.instance,
                NoSlow.instance,
                Nuker.instance,
                Scaffold.instance,
                InputRecorder.instance,
                InputReplay.instance,
                TPAura.instance,
                Velocity.instance,
                SuperKnockback.instance,
                Clip.instance,
                Speed.instance,
                HighJump.instance,
                Fly.instance,
                TickEndSuppress.instance,
                Exempt.instance,
                JoinBurst.instance,
                RefusalMonitor.instance,
                FrameProfiler.instance,
                PingSpoof.instance,
                BadPackets.instance,
                ForgedAck.instance,
                Timer.instance,
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
        event.registerAll(menus);
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
