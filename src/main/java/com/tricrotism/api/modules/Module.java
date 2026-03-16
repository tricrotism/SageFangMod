package com.tricrotism.api.modules;

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
 * Base class for all SageFang modules. Each module has a unique id, display
 * title, description, and a {@link #category} used by the settings menu to
 * group modules dynamically.
 * <p>
 * Modules self-register into a static {@link #REGISTRY} on construction so
 * the settings UI can discover them without hardcoded references.
 */
public abstract class Module implements Comparable<Module> {
    private static final Logger log = LoggerFactory.getLogger(Module.class);
    private static final List<Module> REGISTRY = new ArrayList<>();

    protected final Minecraft mc;

    public final String id;
    public final String title;
    public final String description;
    public final String category;
    public final String baseConfig;

    @Getter
    private boolean active;

    @Getter
    private boolean visible;

    public Module(String id, String title, String description, String category) {
        this.mc = Minecraft.getInstance();
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.baseConfig = "module." + this.id;
        this.active = Config.getBool("module." + id + ".enabled", false);
        this.visible = Config.getBool("module." + id + ".visible", false);
        REGISTRY.add(this);
    }

    /**
     * Returns an unmodifiable view of all constructed modules, in creation order.
     */
    public static List<Module> getRegistry() {
        return Collections.unmodifiableList(REGISTRY);
    }

    public void onActivate() {}

    public void onDeactivate() {}

    public void toggle() {
        log.info("Module '{}' current state: {}", id, active);
        active = !active;
        log.info("Module '{}' toggled to: {}", id, active);

        Config.setProperty("module." + id + ".enabled", String.valueOf(active));

        boolean savedValue = Config.getBool("module." + id + ".enabled");
        log.info("Module '{}' persisted state: {}", id, savedValue);

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
        Config.setProperty("module." + id + ".visible", String.valueOf(visible));
    }

    @Override
    public int compareTo(@NotNull Module o) {
        return id.compareTo(o.id);
    }
}