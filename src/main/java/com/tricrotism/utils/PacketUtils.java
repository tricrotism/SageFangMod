package com.tricrotism.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class PacketUtils {

    public static boolean checkNaN(String axis, String name, double value, String packet, CallbackInfo ci) {
        if (Double.isNaN(value)) {
            MessageUtils.sendMessage(Minecraft.getInstance(),
                String.format("You have received an invalid %s %s \"%s\" from %s!", axis, name, value, packet));
            ci.cancel();
            return true;
        }
        return false;
    }

    public static boolean checkNaN(String axis, String name, float value, String packet, CallbackInfo ci) {
        if (Float.isNaN(value)) {
            MessageUtils.sendMessage(Minecraft.getInstance(),
                String.format("You have received an invalid %s %s \"%f\" from %s!", axis, name, value, packet));
            ci.cancel();
            return true;
        }
        return false;
    }

    public static boolean checkInfinite(String axis, String name, double value, String packet, CallbackInfo ci) {
        if (Double.isInfinite(value)) {
            MessageUtils.sendMessage(Minecraft.getInstance(),
                String.format("You have received an infinite %s %s \"%s\" from %s!", axis, name, value, packet));
            ci.cancel();
            return true;
        }
        return false;
    }

    public static boolean checkInfinite(String axis, String name, float value, String packet, CallbackInfo ci) {
        if (Float.isInfinite(value)) {
            MessageUtils.sendMessage(Minecraft.getInstance(),
                String.format("You have received an infinite %s %s \"%f\" from %s!", axis, name, value, packet));
            ci.cancel();
            return true;
        }
        return false;
    }

    public static boolean checkBad(String axis, String name, double value, String packet, CallbackInfo ci) {
        if (checkNaN(axis, name, value, packet, ci)) return true;
        return checkInfinite(axis, name, value, packet, ci);
    }

    public static boolean checkBad(String axis, String name, float value, String packet, CallbackInfo ci) {
        if (checkNaN(axis, name, value, packet, ci)) return true;
        return checkInfinite(axis, name, value, packet, ci);
    }

    public static boolean checkVec3(String name, Vec3 vec3, String packet, CallbackInfo ci) {
        if (checkBad("x", name, vec3.x(), packet, ci)) return true;
        if (checkBad("y", name, vec3.y(), packet, ci)) return true;
        return checkBad("z", name, vec3.z(), packet, ci);
    }
}
