package com.tricrotism.utils.event.menu;

import com.tricrotism.utils.event.Event;
import com.tricrotism.utils.event.EventListener;

public class MenuRegistrationEvent extends Event<EventListener<MenuRegistrationEventArgs>, MenuRegistrationEventArgs> {
    public static final MenuRegistrationEvent INSTANCE = new MenuRegistrationEvent();
}