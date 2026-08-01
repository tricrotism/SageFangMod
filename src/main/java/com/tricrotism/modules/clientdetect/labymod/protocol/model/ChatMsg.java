package com.tricrotism.modules.clientdetect.labymod.protocol.model;

import java.util.UUID;

/**
 * A single direct-message history entry kept locally for the chat UI.
 *
 * @param peer     the friend this conversation is with
 * @param text     message body
 * @param time     epoch millis when sent/received
 * @param outgoing true if sent by the local player, false if received
 */
public record ChatMsg(UUID peer, String text, long time, boolean outgoing) {}
