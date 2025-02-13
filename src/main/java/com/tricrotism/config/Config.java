// src/main/java/com/tricrotism/config/Config.java

package com.tricrotism.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.tricrotism.Main;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.tricrotism.Main.LOGGER;

public class Config {
    public static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("sagefang.json");
    public static final Config DEFAULTS = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config config;

    // Disable/Enable Menus
    public boolean playerInfoMenu = true;
    public boolean serverInfoMenu = true;
    public boolean uiUtilsMenu = true;

    // UiUtils Implementation
    public List<Packet<?>> delayedUIPackets = new ArrayList<>();
    public boolean bypassResourcePack = false;
    public boolean resourcePackForceDeny = false;
    public boolean delayUIPackets = false;
    public boolean sendUIPackets = true;
    public boolean shouldEditSign = true;
    public boolean shouldForceWakeUp = false;

    /**
     * Creates a new Config. Should only be called once.
     */
    public static Config create() {
        if (config == null) {
            config = new Config();
            read();
            write();
        }
        return config;
    }

    public static void read() {
        if (Files.exists(PATH)) {
            try {
                String rawData = Files.readString(PATH);
                if (rawData.length() < 2 || !rawData.startsWith("{") || !rawData.endsWith("}")) {
                    throw new EOFException("Sagefang config file is empty or corrupted");
                }
                config = GSON.fromJson(rawData, Config.class);
                LOGGER.info("[Config#read] Loaded config info from '{}'!", PATH);
            } catch (JsonIOException | JsonSyntaxException | EOFException e) {
                LOGGER.info("[Config#read] The config couldn't be loaded; backing up and resetting:", e);
                writeCopy();
                config = DEFAULTS;
                write(); // Ensure the default config is written after resetting
            } catch (IOException e) {
                LOGGER.error("[Config#read] An error occurred while trying to load config data from '{}'; resetting:", PATH, e);
                config = DEFAULTS;
                write(); // Ensure the default config is written after resetting
            }
        } else {
            LOGGER.info("[Config#read] No config file found; using default values");
            config = DEFAULTS;
            write(); // Ensure the default config is written if no config file is found
        }
    }

    public static void write() {
        try (FileWriter fw = new FileWriter(PATH.toFile())) {
            GSON.toJson(config, Config.class, fw);
            LOGGER.info("[Config#write] Saved config info to '{}'!", PATH);
        } catch (Exception e) {
            LOGGER.error("[Config#write] An error occurred while trying to save the config to '{}':", PATH, e);
        }
    }

    public static void writeCopy() {
        try {
            Files.copy(PATH, PATH.resolveSibling("sagefang_" + Util.getFilenameFormattedDateTime() + ".json"));
        } catch (IOException e) {
            LOGGER.warn("[Config#writeCopy] An error occurred trying to write a copy of the original config file:", e);
        }
    }

    public List<Setting<?>> getOptions() {
        List<Setting<?>> options = new ArrayList<>(getClass().getFields().length);

        try {
            for (Field f : getClass().getFields())
                if (!Modifier.isStatic(f.getModifiers()))
                    options.add(new Setting<>(f.get(config), f.get(DEFAULTS), f.getName()));
        } catch (IllegalAccessException e) {
            Main.logReportMsg(e);
        }

        return options;
    }

    public <T> Setting<T> getOption(String key, Class<T> type) {
        return (Setting<T>) getOptions()
                .stream()
                .filter(opt -> opt.key.equals(key) && type.isInstance(opt.get()))
                .findFirst()
                .orElse(new Setting<>(null, null, key));
    }

    public <T> Setting<T> getOptionOrDefault(String key, T def, Class<T> type) {
        return (Setting<T>) getOptions()
                .stream()
                .filter(opt -> opt.key.equals(key) && type.isInstance(opt.get()))
                .findFirst()
                .orElse(new Setting<>(def, def, key));
    }

    public static class Setting<T> {
        public final String key;
        public final T def;
        private T val;

        public Setting(@Nullable T val, @Nullable T def, String key) {
            this.val = Objects.requireNonNull(val, "Cannot create a setting option without a value");
            this.def = Objects.requireNonNull(def, "Cannot create a setting option without a default value");
            this.key = Objects.requireNonNull(key, "Cannot create a setting option without a key");
        }

        public T get() {
            return val;
        }

        public Class<T> getType() {
            return (Class<T>) def.getClass();
        }

        public void set(Object obj) {
            try {
                T inc = (T) obj;

                if (inc != null && !inc.equals(val)) {
                    config.getClass().getField(key).set(config, inc);

                    this.val = inc;
                }
            } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
                LOGGER.error("[Setting#set({})] An error occurred trying to change config option '{}':", obj, key);
                Main.logReportMsg(e);
            }
        }

        public boolean changed() {
            return !def.equals(val);
        }
    }
}