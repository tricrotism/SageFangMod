package com.tricrotism.events.game;

public class GameQuitEvent {
    private static final GameQuitEvent INSTANCE = new GameQuitEvent();

    public static GameQuitEvent get() {
        return INSTANCE;
    }
}