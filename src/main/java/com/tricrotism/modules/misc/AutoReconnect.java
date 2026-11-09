package com.tricrotism.modules.misc;

import com.tricrotism.api.modules.Category;
import com.tricrotism.api.modules.Module;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.api.text.MiniMessage;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.kyori.adventure.platform.modcommon.MinecraftClientAudiences;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * Counts down on the vanilla {@link DisconnectedScreen} and reconnects to the last server.
 * {@code ConnectScreenMixin} records the address, so a failed login retries too. Leaving from the
 * pause menu never opens {@link DisconnectedScreen}, so it never triggers a reconnect.
 */
public final class AutoReconnect extends Module {

    public static final AutoReconnect instance = new AutoReconnect();

    private final Settings.Int delay =
        integer("Delay (s)", "delay", "Seconds to wait on the disconnect screen before reconnecting", 5, 1, 60);

    private ServerAddress lastAddress;
    private ServerData lastServer;
    private Screen countdownScreen;
    private Button button;
    private int ticksLeft;

    private AutoReconnect() {
        super("autoreconnect", "Auto Reconnect", "Reconnect to the last server after being disconnected.", Category.UTILITY);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DisconnectedScreen) onDisconnectedInit(screen, width, height);
        });
    }

    public void remember(ServerAddress address, ServerData server) {
        lastAddress = address;
        lastServer = server;
    }

    @Override
    public void onDeactivate() {
        cancel();
    }

    private void onDisconnectedInit(Screen screen, int width, int height) {
        if (!isActive() || lastAddress == null || lastServer == null) return;
        // init also runs on resize, so only a new screen restarts the countdown
        if (screen != countdownScreen) {
            countdownScreen = screen;
            ticksLeft = delay.get() * 20;
        }
        button = Button.builder(label(), b -> cancel()).bounds(width / 2 - 100, height - 30, 200, 20).build();
        button.active = ticksLeft > 0;
        Screens.getWidgets(screen).add(button);
        ScreenEvents.afterTick(screen).register(s -> tick());
    }

    private void tick() {
        if (ticksLeft <= 0) return;
        if (--ticksLeft == 0) {
            reconnect();
        } else if (ticksLeft % 20 == 0) {
            button.setMessage(label());
        }
    }

    private void cancel() {
        ticksLeft = 0;
        if (button == null) return;
        button.setMessage(label());
        button.active = false;
    }

    private Component label() {
        if (ticksLeft <= 0) {
            return MinecraftClientAudiences.of().asNative(MiniMessage.format("<error>Reconnect cancelled"));
        }
        return MinecraftClientAudiences.of().asNative(MiniMessage.format(
            "<off_white>Reconnecting in <highlight><seconds>s</highlight>",
            Placeholder.unparsed("seconds", String.valueOf((ticksLeft + 19) / 20))
        ));
    }

    private void reconnect() {
        button = null;
        countdownScreen = null;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, lastAddress, lastServer, false, null);
    }
}
