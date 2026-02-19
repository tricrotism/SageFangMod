package com.tricrotism.utils;

import com.tricrotism.config.SageFangConfig;

/**
 * Stores rolling history of server/client metrics for graph rendering.
 * Buffer capacity is driven by {@link SageFangConfig#getGraphHistory()}.
 */
public final class GraphHistory {

    public static final GraphHistory INSTANCE = new GraphHistory();

    private static final int DEFAULT_CAPACITY = 300;

    public final RingBuffer tps = new RingBuffer(DEFAULT_CAPACITY);
    public final RingBuffer mspt = new RingBuffer(DEFAULT_CAPACITY);
    public final RingBuffer serverMemory = new RingBuffer(DEFAULT_CAPACITY);
    public final RingBuffer clientMemory = new RingBuffer(DEFAULT_CAPACITY);
    public final RingBuffer fps = new RingBuffer(DEFAULT_CAPACITY);

    private GraphHistory() {}

    /**
     * Ensures all buffers match the configured capacity.
     * Call once per frame before pushing data.
     */
    public void ensureCapacity() {
        int target = SageFangConfig.getGraphHistory();
        if (target < 10) target = 10;
        if (target > 3600) target = 3600;
        if (tps.capacity() != target) {
            tps.resize(target);
            mspt.resize(target);
            serverMemory.resize(target);
            clientMemory.resize(target);
            fps.resize(target);
        }
    }

    public void pushServerMetrics(float tpsVal, float msptVal, float memUsedMb) {
        tps.push(tpsVal);
        mspt.push(msptVal);
        serverMemory.push(memUsedMb);
    }

    public void pushClientMetrics(float fpsVal, float memUsedMb) {
        fps.push(fpsVal);
        clientMemory.push(memUsedMb);
    }

    public void clear() {
        tps.clear();
        mspt.clear();
        serverMemory.clear();
        clientMemory.clear();
        fps.clear();
    }
}
