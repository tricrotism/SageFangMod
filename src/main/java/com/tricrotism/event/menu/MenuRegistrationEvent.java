package com.tricrotism.event.menu;

import com.tricrotism.event.Event;
import com.tricrotism.event.EventListener;

public class MenuRegistrationEvent extends Event<EventListener<MenuRegistrationEventArgs>, MenuRegistrationEventArgs> {
    public static final MenuRegistrationEvent INSTANCE = new MenuRegistrationEvent();
}