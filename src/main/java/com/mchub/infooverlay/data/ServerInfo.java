package com.mchub.infooverlay.data;

public class ServerInfo {
    private final String instance;
    private final String world;
    private final long uptime;
    private final int entityCount;
    private final int loadedChunks;
    private final float tps;
    private final float mspt;
    private final long memoryFree;
    private final long memoryMax;

    public ServerInfo(String instance, String world, long uptime, int entityCount, int loadedChunks, float tps, float mspt, long memoryFree, long memoryMax) {
        this.instance = instance;
        this.world = world;
        this.uptime = uptime;
        this.entityCount = entityCount;
        this.loadedChunks = loadedChunks;
        this.tps = tps;
        this.mspt = mspt;
        this.memoryFree = memoryFree;
        this.memoryMax = memoryMax;
    }

    public String getInstance() {
        return instance;
    }

    public String getWorld() {
        return world;
    }

    public long getUptime() {
        return uptime;
    }

    public int getEntityCount() {
        return entityCount;
    }

    public int getLoadedChunks() {
        return loadedChunks;
    }

    public float getTps() {
        return tps;
    }

    public float getMspt() {
        return mspt;
    }

    public long getMemoryFree() {
        return memoryFree;
    }

    public long getMemoryMax() {
        return memoryMax;
    }
}
