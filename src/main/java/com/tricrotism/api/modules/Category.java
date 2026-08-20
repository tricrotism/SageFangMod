package com.tricrotism.api.modules;

/**
 * Module groupings, in the order the settings panel lists them.
 * <p>
 * Each carries the one-line explanation shown when hovering its header, so the panel says what a
 * group is for rather than leaving the reader to infer it from whichever modules happen to be in it.
 * The set is deliberately small: the previous split kept ESP separate from Visual, and Chat separate
 * from the other single-purpose helpers, which meant several groups held one or two entries and the
 * boundary between them was not obvious from the names.
 */
public enum Category {

    COMBAT("Combat", "Attacking, aim and combat automation"),
    RENDER("Render", "World overlays, ESP boxes and camera changes"),
    WORLD("World", "Mining, block placement and interaction"),
    NETWORK("Network", "Packet timing, latency and protocol-level probes"),
    EXPLOIT("Exploit", "Server-specific and anticheat-specific bypasses"),
    UTILITY("Utility", "Chat helpers, item tools and quality-of-life"),
    LOGGING("Logging", "Passive observation and diagnostics");

    private final String displayName;
    private final String description;

    Category(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
