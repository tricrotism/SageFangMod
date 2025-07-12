package com.tricrotism.eventbus;

/**
 * Thrown when an {@link IEventBus} can't find a registered lambda factory to use.
 */
public class NoLambdaFactoryException extends RuntimeException {
    public NoLambdaFactoryException(Class<?> clazz) {
        super("No registered lambda listener for '" + clazz.getName() + "'.");
    }
}