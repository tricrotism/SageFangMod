package com.tricrotism.api.modules;

import com.tricrotism.api.menus.Menu;
import com.tricrotism.api.settings.Setting;
import com.tricrotism.api.settings.Settings;
import com.tricrotism.utils.ImGuiUtil;
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

    // Read from the network thread by packet hooks.
    @Getter
    private volatile boolean active;

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
     * Returns an unmodifiable view of all constructed modules, in creation order.
     */
    public static List<Module> getRegistry() {
        return Collections.unmodifiableList(REGISTRY);
    }

    /**
     * Runs {@link #onActivate()} for every module the config says was left on.
     * <p>
     * The constructor restores {@code active} from config, so every {@code isActive()} guard in a
     * restored module already passes on the first tick. Nothing ran the activation side effects that
     * belong with it, which leaves the module half on: its handlers fire while the scheduler, sampler
     * thread or captured state they depend on was never created, and the first toggle off then runs
     * {@link #onDeactivate()} against state that was never acquired.
     * <p>
     * A module that throws on the way up is switched off rather than left in that state, since the
     * alternative is the same half-on module with a stack trace in front of it.
     */
    public static void restoreActive() {
        for (Module module : REGISTRY) {
            if (!module.active) continue;

            try {
                module.onActivate();
            } catch (RuntimeException e) {
                log.error("Module '{}' failed to restore and has been switched off", module.id, e);
                module.active = false;
                Config.setProperty(module.baseConfig + ".enabled", "false");
            }
        }
    }

    /**
     * Runs {@link #onDeactivate()} for every active module, for client shutdown.
     * <p>
     * Without this the process exits with sampler threads unjoined, profiles unwritten and the mining
     * abort packet unsent, because {@code toggle()} was the only path that ever reached teardown.
     * Failures are logged and the sweep continues: one module refusing to stop must not keep the
     * others from doing it.
     */
    public static void shutdownAll() {
        for (Module module : REGISTRY) {
            if (!module.active) continue;

            try {
                module.onDeactivate();
            } catch (RuntimeException e) {
                log.error("Module '{}' failed to shut down cleanly", module.id, e);
            }
        }
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
        if (!description.isEmpty() && ImGui.isItemHovered()) ImGuiUtil.setTooltip(description);

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
