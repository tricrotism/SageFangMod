package com.tricrotism.event;

@FunctionalInterface
public interface EventListener<A> {
    void invoke(A eventArgs);
}