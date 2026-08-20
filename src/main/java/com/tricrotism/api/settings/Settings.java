package com.tricrotism.api.settings;

import com.tricrotism.utils.KeybindUtil;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import io.avaje.config.Config;
import org.lwjgl.glfw.GLFW;

/**
 * The concrete setting types. They live together because each is only a few lines and they are
 * always reached through {@link Setting}; splitting them across files would be six imports for no
 * added clarity.
 */
public final class Settings {

    private Settings() {}

    /**
     * Checkbox.
     */
    public static final class Bool extends Setting<Boolean> {

        public Bool(String label, String key, String description, boolean defaultValue) {
            super(label, key, description);
            this.value = Config.getBool(key, defaultValue);
        }

        @Override
        protected void persist() {
            Config.setProperty(key, String.valueOf(value));
        }

        @Override
        public void render() {
            if (ImGui.checkbox(id(), value)) set(!value);
            tooltip();
        }
    }

    /**
     * Integer slider.
     */
    public static final class Int extends Setting<Integer> {

        private final int min;
        private final int max;
        private final int[] buffer = new int[1];

        public Int(String label, String key, String description, int defaultValue, int min, int max) {
            super(label, key, description);
            this.min = min;
            this.max = max;
            this.value = Config.getInt(key, defaultValue);
        }

        @Override
        protected void persist() {
            Config.setProperty(key, String.valueOf(value));
        }

        @Override
        public void render() {
            buffer[0] = value;
            ImGui.setNextItemWidth(WIDTH);
            if (ImGui.sliderInt(id(), buffer, min, max)) set(buffer[0]);
            tooltip();
        }
    }

    /**
     * Floating-point slider. Stored as a string so the config keeps full precision.
     */
    public static final class Decimal extends Setting<Double> {

        private final float min;
        private final float max;
        private final float[] buffer = new float[1];

        public Decimal(String label, String key, String description, double defaultValue, double min, double max) {
            super(label, key, description);
            this.min = (float) min;
            this.max = (float) max;
            this.value = parse(Config.get(key, String.valueOf(defaultValue)), defaultValue);
        }

        private static double parse(String raw, double fallback) {
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        @Override
        protected void persist() {
            Config.setProperty(key, String.valueOf(value));
        }

        @Override
        public void render() {
            buffer[0] = value.floatValue();
            ImGui.setNextItemWidth(WIDTH);
            if (ImGui.sliderFloat(id(), buffer, min, max)) set((double) buffer[0]);
            tooltip();
        }
    }

    /**
     * Dropdown over a fixed option list. Persisted by index rather than label so renaming an option
     * does not silently reset every existing config.
     */
    public static final class Mode extends Setting<Integer> {

        private final String[] options;
        private final ImInt buffer = new ImInt();

        public Mode(String label, String key, String description, int defaultIndex, String... options) {
            super(label, key, description);
            this.options = options;
            this.value = Math.clamp(Config.getInt(key, defaultIndex), 0, options.length - 1);
        }

        /**
         * The selected option's label, for logging and display.
         */
        public String option() {
            return options[value];
        }

        @Override
        protected void persist() {
            Config.setProperty(key, String.valueOf(value));
        }

        @Override
        public void render() {
            buffer.set(value);
            ImGui.setNextItemWidth(WIDTH);
            if (ImGui.combo(id(), buffer, options)) set(buffer.get());
            tooltip();
        }
    }

    /**
     * A single-line text field, stored as-is. The ImGui buffer is owned here so a module never has to
     * hold an {@link ImString} of its own.
     */
    public static final class Text extends Setting<String> {

        private final ImString buffer;

        public Text(String label, String key, String description, String defaultValue, int maxLength) {
            super(label, key, description);
            this.value = Config.get(key, defaultValue);
            this.buffer = new ImString(maxLength);
            this.buffer.set(this.value);
        }

        @Override
        protected void persist() {
            Config.setProperty(key, value);
        }

        @Override
        public void render() {
            ImGui.setNextItemWidth(WIDTH);
            if (ImGui.inputText(id(), buffer)) set(buffer.get());
            tooltip();
        }
    }

    /**
     * A rebindable key. Holds the GLFW key code and drives the listen-for-a-keypress capture button
     * itself, so a module no longer needs a {@code getKeybind}/{@code setKeybind} pair, an
     * {@code awaiting} flag, and the ten-line result switch in its frame.
     */
    public static final class Key extends Setting<Integer> {

        private boolean awaiting;

        public Key(String label, String key, String description, int defaultKey) {
            super(label, key, description);
            this.value = Config.getInt(key, defaultKey);
        }

        /**
         * Whether the bound key is currently held with no screen open.
         */
        public boolean isDown() {
            return KeybindUtil.isKeyDown(value);
        }

        @Override
        protected void persist() {
            Config.setProperty(key, String.valueOf(value));
        }

        @Override
        public void render() {
            int result = KeybindUtil.renderKeybindButton(label, key, value, awaiting);
            if (result == KeybindUtil.START_LISTENING) {
                awaiting = true;
            } else if (result == KeybindUtil.CLEAR_BIND) {
                set(GLFW.GLFW_KEY_UNKNOWN);
                awaiting = false;
            } else if (result != KeybindUtil.NO_CHANGE) {
                set(result);
                awaiting = false;
            }
            tooltip();
        }
    }

    private static final int WIDTH = 160;
}
