package com.tricrotism.utils;

/**
 * Fixed-size circular buffer of floats. When full, new values overwrite the oldest.
 * Provides a contiguous {@code float[]} snapshot suitable for ImPlot.
 */
public final class RingBuffer {

    private float[] data;
    private int head;
    private int count;

    public RingBuffer(int capacity) {
        this.data = new float[capacity];
    }

    public void push(float value) {
        data[head] = value;
        head = (head + 1) % data.length;
        if (count < data.length) count++;
    }

    /**
     * Returns the number of values currently stored.
     */
    public int size() {
        return count;
    }

    /**
     * Returns a contiguous array in insertion order (oldest first).
     */
    public float[] toArray() {
        float[] result = new float[count];
        if (count < data.length) {
            System.arraycopy(data, 0, result, 0, count);
        } else {
            int tail = head;
            System.arraycopy(data, tail, result, 0, data.length - tail);
            System.arraycopy(data, 0, result, data.length - tail, tail);
        }
        return result;
    }

    /**
     * Returns the most recently pushed value, or 0 if empty.
     */
    public float last() {
        if (count == 0) return 0f;
        return data[(head - 1 + data.length) % data.length];
    }

    public void clear() {
        head = 0;
        count = 0;
    }

    /**
     * Returns the current capacity.
     */
    public int capacity() {
        return data.length;
    }

    /**
     * Resizes the buffer. Existing data is preserved (oldest trimmed if shrinking).
     */
    public void resize(int newCapacity) {
        if (newCapacity == data.length) return;
        float[] old = toArray();
        data = new float[newCapacity];
        int toCopy = Math.min(old.length, newCapacity);
        // keep the most recent values
        System.arraycopy(old, old.length - toCopy, data, 0, toCopy);
        head = toCopy % newCapacity;
        count = toCopy;
    }
}
