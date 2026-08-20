package com.tricrotism.config;

import io.avaje.config.Config;

/**
 * Centralized configuration backed by avaje-config.
 * All settings are stored in config/sagefang.properties and auto-saved.
 */
public final class SageFangConfig {

    private SageFangConfig() {}

    private static final String MENU_PLAYER_INFO = "menu.playerInfo.enabled";
    private static final String MENU_SERVER_INFO = "menu.serverInfo.enabled";
    private static final String MENU_MERGED = "menu.info.merged";
    private static final String MENU_SHOW_GRAPHS = "menu.info.showGraphs";

    private static final String GRAPH_FPS = "graph.fps";
    private static final String GRAPH_CLIENT_MEM = "graph.clientMemory";
    private static final String GRAPH_TPS = "graph.tps";
    private static final String GRAPH_MSPT = "graph.mspt";
    private static final String GRAPH_SERVER_MEM = "graph.serverMemory";
    private static final String GRAPH_WIDTH = "graph.width";
    private static final String GRAPH_HEIGHT = "graph.height";
    private static final String GRAPH_HISTORY = "graph.history";

    private static final String ESP_FILLED = "esp.filled";

    private static final String RPACK_BYPASS = "resourcepack.bypass";
    private static final String RPACK_FORCE_DENY = "resourcepack.forceDeny";
    private static final String RPACK_DISABLE_UNOBF = "resourcepack.disableUnobf";

    private static final String UI_DELAY = "uipackets.delay";
    private static final String UI_SEND = "uipackets.send";

    private static final String MISC_EDIT_SIGN = "misc.shouldEditSign";
    private static final String MISC_FORCE_WAKE = "misc.shouldForceWakeUp";
    private static final String MISC_LOG_SOUNDS = "misc.shouldLogSounds";
    private static final String MISC_SLOT_NUMBERS = "misc.shouldShowSlotNumbers";

    private static boolean getBool(String key, boolean def) {
        return Config.getBool(key, def);
    }

    private static void setBool(String key, boolean value) {
        Config.setProperty(key, String.valueOf(value));
    }

    private static int getInt(String key, int def) {
        return Config.getInt(key, def);
    }

    private static void setInt(String key, int value) {
        Config.setProperty(key, String.valueOf(value));
    }

    public static boolean isPlayerInfoMenuEnabled() {return getBool(MENU_PLAYER_INFO, true);}

    public static void setPlayerInfoMenuEnabled(boolean v) {setBool(MENU_PLAYER_INFO, v);}

    public static boolean isServerInfoMenuEnabled() {return getBool(MENU_SERVER_INFO, true);}

    public static void setServerInfoMenuEnabled(boolean v) {setBool(MENU_SERVER_INFO, v);}


    public static boolean isMergedInfoMenu() {return getBool(MENU_MERGED, false);}

    public static void setMergedInfoMenu(boolean v) {setBool(MENU_MERGED, v);}

    public static boolean isShowGraphs() {return getBool(MENU_SHOW_GRAPHS, false);}

    public static void setShowGraphs(boolean v) {setBool(MENU_SHOW_GRAPHS, v);}

    public static boolean isGraphFps() {return getBool(GRAPH_FPS, true);}

    public static void setGraphFps(boolean v) {setBool(GRAPH_FPS, v);}

    public static boolean isGraphClientMemory() {return getBool(GRAPH_CLIENT_MEM, true);}

    public static void setGraphClientMemory(boolean v) {setBool(GRAPH_CLIENT_MEM, v);}

    public static boolean isGraphTps() {return getBool(GRAPH_TPS, true);}

    public static void setGraphTps(boolean v) {setBool(GRAPH_TPS, v);}

    public static boolean isGraphMspt() {return getBool(GRAPH_MSPT, true);}

    public static void setGraphMspt(boolean v) {setBool(GRAPH_MSPT, v);}

    public static boolean isGraphServerMemory() {return getBool(GRAPH_SERVER_MEM, true);}

    public static void setGraphServerMemory(boolean v) {setBool(GRAPH_SERVER_MEM, v);}

    public static int getGraphWidth() {return getInt(GRAPH_WIDTH, 280);}

    public static void setGraphWidth(int v) {setInt(GRAPH_WIDTH, v);}

    public static int getGraphHeight() {return getInt(GRAPH_HEIGHT, 120);}

    public static void setGraphHeight(int v) {setInt(GRAPH_HEIGHT, v);}

    public static int getGraphHistory() {return getInt(GRAPH_HISTORY, 300);}

    public static void setGraphHistory(int v) {setInt(GRAPH_HISTORY, v);}

    public static boolean isEspFilled() {return getBool(ESP_FILLED, false);}

    public static void setEspFilled(boolean v) {setBool(ESP_FILLED, v);}

    public static boolean isBypassResourcePack() {return getBool(RPACK_BYPASS, false);}

    public static void setBypassResourcePack(boolean v) {setBool(RPACK_BYPASS, v);}

    public static boolean isResourcePackForceDeny() {return getBool(RPACK_FORCE_DENY, false);}

    public static void setResourcePackForceDeny(boolean v) {setBool(RPACK_FORCE_DENY, v);}

    public static boolean isDisablePackUnobf() {return getBool(RPACK_DISABLE_UNOBF, false);}

    public static void setDisablePackUnobf(boolean v) {setBool(RPACK_DISABLE_UNOBF, v);}

    public static boolean isDelayUIPackets() {return getBool(UI_DELAY, false);}

    public static void setDelayUIPackets(boolean v) {setBool(UI_DELAY, v);}

    public static boolean isSendUIPackets() {return getBool(UI_SEND, true);}

    public static void setSendUIPackets(boolean v) {setBool(UI_SEND, v);}

    public static boolean isShouldEditSign() {return getBool(MISC_EDIT_SIGN, true);}

    public static void setShouldEditSign(boolean v) {setBool(MISC_EDIT_SIGN, v);}

    public static boolean isShouldForceWakeUp() {return getBool(MISC_FORCE_WAKE, false);}

    public static void setShouldForceWakeUp(boolean v) {setBool(MISC_FORCE_WAKE, v);}

    public static boolean isShouldLogSounds() {return getBool(MISC_LOG_SOUNDS, false);}

    public static void setShouldLogSounds(boolean v) {setBool(MISC_LOG_SOUNDS, v);}

    public static boolean isShouldShowSlotNumbers() {return getBool(MISC_SLOT_NUMBERS, false);}

    public static void setShouldShowSlotNumbers(boolean v) {setBool(MISC_SLOT_NUMBERS, v);}
}
