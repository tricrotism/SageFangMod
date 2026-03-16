package com.tricrotism.modules.clientdetect.labymod.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PacketRegistry {
    private final Map<Integer, Supplier<Packet>> idToFactory = new HashMap<>();
    private final Map<Class<? extends Packet>, Integer> classToId = new HashMap<>();

    public void register(int id, Supplier<Packet> factory) {
        idToFactory.put(id, factory);
        classToId.put(factory.get().getClass(), id);
    }

    public int getId(Packet packet) {
        Integer id = classToId.get(packet.getClass());
        if (id == null) {
            throw new IllegalArgumentException("Unregistered packet: " + packet.getClass().getName());
        }
        return id;
    }

    public Packet create(int id) {
        Supplier<Packet> factory = idToFactory.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown packet ID: " + id);
        }
        return factory.get();
    }
}
