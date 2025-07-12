package com.tricrotism.utils.event;

import java.util.ArrayList;
import java.util.List;

public abstract class Event<T extends EventListener<A>, A> {
    private final List<T> listeners = new ArrayList<>();

    public void invoke(A eventArgs) {
        listeners.forEach(it -> it.invoke(eventArgs));
    }

    public void addListener(T listener) {
        listeners.add(listener);
    }

    @SafeVarargs
    public final void addListeners(T... listeners) {
        for (T listener : listeners) {
            addListener(listener);
        }
    }
}