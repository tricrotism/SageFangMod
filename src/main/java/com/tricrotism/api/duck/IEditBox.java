package com.tricrotism.api.duck;

/**
 * Duck interface for EditBox mixin — allows cross-mixin communication.
 * Must NOT live in the mixin package to avoid classloader issues.
 */
public interface IEditBox {
    void sageFang$setAsChatBox();
}
