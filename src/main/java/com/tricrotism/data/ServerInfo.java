package com.tricrotism.data;

import lombok.Getter;

@Getter
public class ServerInfo {
    private final String instance;
    private final String world;
    private final long uptime;
    private final int entityCount;
    private final int loadedChunks;
    private final float tps;
    private final float mspt;
    private final long memoryUsed;
    private final long memoryMax;
    private final String javaVersion;
    private final String hostname;
    private final int onlinePlayers;
    private final int logins;

    public ServerInfo(String instance, String world, long uptime, int entityCount, int loadedChunks, float tps, float mspt, long memoryUsed, long memoryMax, String javaVersion, String hostname, int onlinePlayers, int logins) {
        this.instance = instance;
        this.world = world;
        this.uptime = uptime;
        this.entityCount = entityCount;
        this.loadedChunks = loadedChunks;
        this.tps = tps;
        this.mspt = mspt;
        this.memoryUsed = memoryUsed;
        this.memoryMax = memoryMax;
        this.javaVersion = javaVersion;
        this.hostname = hostname;
        this.onlinePlayers = onlinePlayers;
        this.logins = logins;
    }

}
