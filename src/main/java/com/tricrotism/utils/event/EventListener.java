package com.tricrotism.utils.event;

@FunctionalInterface
public interface EventListener<A> {
    void invoke(A eventArgs);
}