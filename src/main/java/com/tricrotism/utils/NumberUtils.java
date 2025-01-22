package com.tricrotism.utils;

import java.text.NumberFormat;
import java.util.Locale;

public class NumberUtils {

    public static final NumberFormat DOUBLE_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    static {
        DOUBLE_FORMAT.setMinimumFractionDigits(2);
        DOUBLE_FORMAT.setMaximumFractionDigits(2);
    }

    public static long round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException("places must be >= 0");

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return tmp / factor;
    }

    public static float round(float value, int places) {
        if (places < 0) throw new IllegalArgumentException("places must be >= 0");

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (float) (tmp / factor);
    }

    public static String format(double d) {
        return DOUBLE_FORMAT.format(d);
    }

    public static String formatMemorySize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), unit);
    }

}
