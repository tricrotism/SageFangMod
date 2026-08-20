package com.tricrotism.events.ui;

import com.tricrotism.api.menus.Menu;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fired each frame to collect menus that should be rendered.
 * Listeners call {@link #register(Menu)} or {@link #registerAll(Menu...)} to add menus.
 */
@Getter
public class MenuRegistrationEvent {
    private static final MenuRegistrationEvent INSTANCE = new MenuRegistrationEvent();

    private final List<Menu> menus = new ArrayList<>();

    public static MenuRegistrationEvent get() {
        INSTANCE.menus.clear();
        return INSTANCE;
    }

    public void register(Menu menu) {
        menus.add(menu);
    }

    public void registerAll(Menu... menu) {
        menus.addAll(Arrays.asList(menu));
    }

    public void registerAll(List<Menu> menu) {
        menus.addAll(menu);
    }
}
