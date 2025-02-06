package com.tricrotism.utils;

import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.List;

public class UIUtilVariables {
    public static boolean bypassResourcePack = false;
    public static boolean resourcePackForceDeny = false;

    public static boolean delayUIPackets = false;
    public static List<Packet<?>> delayedUIPackets = new ArrayList<>();

    public static boolean shouldEditSign = true;
    public static boolean sendUIPackets = true;
    public static boolean shouldForceWakeUp = false;

}
