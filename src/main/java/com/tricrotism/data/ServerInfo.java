package com.tricrotism.data;

public record ServerInfo(String instance, String world, long uptime, int entityCount, int loadedChunks, float tps,
                         float mspt, long memoryUsed, long memoryMax, String javaVersion, String hostname,
                         int onlinePlayers, int logins) {

}
