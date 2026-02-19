package com.tricrotism.config;

import io.avaje.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

/**
 * avaje-config has no built-in write-back. This class persists all
 * config changes to {@code config/sagefang.properties} with debouncing.
 * <p>
 * Call {@link #init()} once at mod startup. It registers:
 * <ul>
 *   <li>A global onChange listener that marks config dirty</li>
 *   <li>A timer that flushes dirty config every 5 seconds</li>
 *   <li>A shutdown hook for clean exit</li>
 * </ul>
 */
public final class ConfigPersistence {

    private static final Logger log = LoggerFactory.getLogger(ConfigPersistence.class);
    private static final Path CONFIG_PATH = Path.of("config", "sagefang.properties");
    private static volatile boolean dirty;

    private ConfigPersistence() {}

    public static void init() {
        // Listen for any property change
        Config.onChange(event -> dirty = true);

        // Periodic flush every 5 seconds
        Timer timer = new Timer("SageFang-ConfigSave", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (dirty) {
                    save();
                }
            }
        }, 5000, 5000);

        // Flush on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (dirty) {
                save();
            }
        }, "SageFang-ConfigSaveShutdown"));

        log.info("Config persistence initialized, saving to {}", CONFIG_PATH.toAbsolutePath());
    }

    /** Force an immediate save. */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            Properties props = Config.asProperties();

            // Sort keys for stable output, skip avaje internals
            TreeMap<String, String> sorted = new TreeMap<>();
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("config.") || key.startsWith("load.")) continue;
                sorted.put(key, props.getProperty(key));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# SageFang Configuration — auto-saved\n\n");

            String lastSection = "";
            for (var entry : sorted.entrySet()) {
                String key = entry.getKey();
                // Section header based on first dotted segment
                String section = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
                if (!section.equals(lastSection)) {
                    if (!lastSection.isEmpty()) sb.append('\n');
                    sb.append("## ").append(section).append('\n');
                    lastSection = section;
                }
                sb.append(key).append('=').append(escapeValue(entry.getValue())).append('\n');
            }

            Files.writeString(CONFIG_PATH, sb.toString());
            dirty = false;
        } catch (IOException e) {
            log.error("Failed to save config to {}", CONFIG_PATH, e);
        }
    }

    private static String escapeValue(String value) {
        // Properties file escaping for leading whitespace and special chars
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }
}
