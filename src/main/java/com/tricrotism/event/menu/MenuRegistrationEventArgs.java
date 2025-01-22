package com.tricrotism.event.menu;

import com.tricrotism.Menu;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class MenuRegistrationEventArgs {
    private final List<Menu> menus = new ArrayList<>();

    public void register(Menu menu) {
        menus.add(menu);
    }

}