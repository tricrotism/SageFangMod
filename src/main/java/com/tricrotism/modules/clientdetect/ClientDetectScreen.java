package com.tricrotism.modules.clientdetect;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A minimal vanilla screen that exists only to free the mouse cursor so the
 * ImGui-rendered Client Detect window (drawn by {@link ClientDetect#frame}) is
 * interactive: friend list, chat input and status controls. The screen draws
 * nothing of its own beyond the default dimmed background; closing it (Esc)
 * returns to the game. The game is not paused so LabyConnect keeps flowing.
 */
public final class ClientDetectScreen extends Screen {

    public ClientDetectScreen() {
        super(Component.literal("Client Detect"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
