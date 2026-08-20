package com.tricrotism.api.modules;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.settings.Setting;
import com.tricrotism.api.settings.Settings;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiWindowFlags;
import io.avaje.config.Config;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Base class for all SageFang modules. Each module has a unique id, display title, description, and
 * a {@link Category} used by the settings menu to group modules dynamically.
 * <p>
 * Modules self-register into a static registry on construction so the settings UI can discover them
 * without hardcoded references.
 * <p>
 * Declare configuration through the {@code bool}/{@code integer}/{@code decimal}/{@code mode}
 * factories rather than reading and writing config by hand. A declared setting persists itself and
 * is drawn by the default {@link #frame(ImGuiIO)}, so a module with no bespoke UI needs no
 * {@code frame} override at all. Modules that need custom widgets override {@link #renderExtra} to
 * add to the standard window, or {@code frame} to replace it entirely.
 */
public abstract class Module implements Menu, Comparable<Module> {

    private static final Logger log = LoggerFactory.getLogger(Module.class);
    private static final List<Module> REGISTRY = new ArrayList<>();

    protected final Minecraft mc;

    public final String id;
    public final String title;
    public final String description;
    public final Category category;
    public final String baseConfig;

    private final List<Setting<?>> settings = new ArrayList<>();

    @Getter
    private boolean active;

    @Getter
    private boolean visible;

    protected Module(String id, String title, String description, Category category) {
        this.mc = Minecraft.getInstance();
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.baseConfig = "module." + id;
        this.active = Config.getBool(baseConfig + ".enabled", false);
        this.visible = Config.getBool(baseConfig + ".visible", false);
        REGISTRY.add(this);
    }

    /**
     * Retained so the modules still carrying a category string keep compiling while they migrate.
     * Unknown names fall back to {@link Category#UTILITY} rather than failing at class-init, which
     * would take the whole mod down for a typo in one module.
     */
    protected Module(String id, String title, String description, String legacyCategory) {
        this(id, title, description, mapLegacy(legacyCategory));
    }

    private static Category mapLegacy(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "combat" -> Category.COMBAT;
            case "esp", "visual", "render" -> Category.RENDER;
            case "world" -> Category.WORLD;
            case "network" -> Category.NETWORK;
            case "exploit" -> Category.EXPLOIT;
            case "logger", "logging" -> Category.LOGGING;
            case "chat", "utility" -> Category.UTILITY;
            default -> {
                log.warn("Unknown module category '{}', filing under Utility", name);
                yield Category.UTILITY;
            }
        };
    }

    /**
     * Returns an unmodifiable view of all constructed modules, in creation order.
     */
    public static List<Module> getRegistry() {
        return Collections.unmodifiableList(REGISTRY);
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    protected Settings.Bool bool(String label, String key, String description, boolean defaultValue) {
        return add(new Settings.Bool(label, baseConfig + "." + key, description, defaultValue));
    }

    protected Settings.Int integer(String label, String key, String description, int defaultValue, int min, int max) {
        return add(new Settings.Int(label, baseConfig + "." + key, description, defaultValue, min, max));
    }

    protected Settings.Decimal decimal(String label, String key, String description,
                                       double defaultValue, double min, double max) {
        return add(new Settings.Decimal(label, baseConfig + "." + key, description, defaultValue, min, max));
    }

    protected Settings.Mode mode(String label, String key, String description, int defaultIndex, String... options) {
        return add(new Settings.Mode(label, baseConfig + "." + key, description, defaultIndex, options));
    }

    protected Settings.Text text(String label, String key, String description, String defaultValue, int maxLength) {
        return add(new Settings.Text(label, baseConfig + "." + key, description, defaultValue, maxLength));
    }

    protected Settings.Key key(String label, String key, String description, int defaultKey) {
        return add(new Settings.Key(label, baseConfig + "." + key, description, defaultKey));
    }

    public void onActivate() {}

    public void onDeactivate() {}

    public void toggle() {
        active = !active;
        Config.setProperty(baseConfig + ".enabled", String.valueOf(active));
        log.info("Module '{}' toggled to {}", id, active);

        if (active) {
            onActivate();
        } else {
            onDeactivate();
        }
    }

    /**
     * Toggle window visibility without activating/deactivating the feature.
     */
    public void toggleVisible() {
        visible = !visible;
        Config.setProperty(baseConfig + ".visible", String.valueOf(visible));
    }

    /**
     * The standard module window: an enable checkbox, every declared setting, then whatever
     * {@link #renderExtra} adds.
     */
    @Override
    public void frame(ImGuiIO io) {
        if (!visible) return;

        ImGui.setNextWindowBgAlpha(0.45f);
        ImGui.begin(title, ImGuiWindowFlags.AlwaysAutoResize);

        if (ImGui.checkbox("Enabled##" + id, active)) toggle();
        if (!description.isEmpty() && ImGui.isItemHovered()) ImGui.setTooltip(description);

        for (Setting<?> setting : settings) {
            setting.render();
        }

        renderExtra(io);
        ImGui.end();
    }

    /**
     * Extra widgets for the standard window: live counters, buttons, read-only status.
     */
    protected void renderExtra(ImGuiIO io) {}

    @Override
    public int compareTo(@NotNull Module o) {
        return id.compareTo(o.id);
    }
}
