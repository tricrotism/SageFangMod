package com.tricrotism.modules.clientdetect.labymod.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bidirectional mapping between numeric LabyConnect packet ids and their
 * {@link Packet} implementations. Built once at construction of
 * {@link com.tricrotism.modules.clientdetect.labymod.LabyConnectClient} and used
 * to look up an id when sending and to instantiate a blank packet when
 * receiving.
 *
 * <p>Not thread-safe to mutate: it is fully populated on construction and only
 * read afterwards (from the Netty event loop).
 */
public class PacketRegistry {
    private final Map<Integer, Supplier<Packet>> idToFactory = new HashMap<>();
    private final Map<Class<? extends Packet>, Integer> classToId = new HashMap<>();

    /**
     * Registers a packet id together with a factory for fresh instances.
     * Invokes the factory immediately to learn the concrete class for the
     * reverse (class &rarr; id) lookup used when sending.
     *
     * @param id      the wire packet id (written/read as a leading varint)
     * @param factory creates a new, empty packet of this type
     */
    public void register(int id, Supplier<Packet> factory) {
        idToFactory.put(id, factory);
        classToId.put(factory.get().getClass(), id);
    }

    /**
     * Resolves the wire id for an outbound packet by its concrete class.
     *
     * @throws IllegalArgumentException if the packet's class was never registered
     */
    public int getId(Packet packet) {
        Integer id = classToId.get(packet.getClass());
        if (id == null) {
            throw new IllegalArgumentException("Unregistered packet: " + packet.getClass().getName());
        }
        return id;
    }

    /**
     * Instantiates a blank packet for an inbound id, ready to have
     * {@link Packet#read(PacketBuffer)} called on it.
     *
     * @throws IllegalArgumentException if no packet is registered for {@code id}
     */
    public Packet create(int id) {
        Supplier<Packet> factory = idToFactory.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown packet ID: " + id);
        }
        return factory.get();
    }
}
