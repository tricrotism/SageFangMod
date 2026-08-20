package com.tricrotism.api.settings;

import imgui.ImGui;

/**
 * One configurable value on a module: its label, its config key, and how it draws itself.
 * <p>
 * Modules declare settings instead of hand-writing an ImGui control and a pair of
 * {@code Config.getX}/{@code Config.setProperty} calls for every field. Persistence happens here, so
 * a setting cannot be rendered without being saved or saved under a key that does not match the one
 * it loads from.
 */
public abstract class Setting<T> {

    protected final String label;
    protected final String key;
    private final String description;

    protected T value;

    protected Setting(String label, String key, String description) {
        this.label = label;
        this.key = key;
        this.description = description;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        if (newValue.equals(value)) return;
        value = newValue;
        persist();
    }

    /**
     * Writes the current value through to the config layer.
     */
    protected abstract void persist();

    /**
     * Draws the control. Implementations call {@link #tooltip()} after their widget.
     */
    public abstract void render();

    /**
     * Unique ImGui id, derived from the config key so two settings can share a label.
     */
    protected String id() {
        return label + "##" + key;
    }

    protected void tooltip() {
        if (description != null && !description.isEmpty() && ImGui.isItemHovered()) {
            ImGui.setTooltip(description);
        }
    }
}
