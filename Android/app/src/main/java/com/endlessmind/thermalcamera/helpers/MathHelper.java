package com.endlessmind.thermalcamera.helpers;

public class MathHelper {

    /**
     *
     * @param x In value
     * @param in_min Min input
     * @param in_max Max inout
     * @param out_min Min output
     * @param out_max Max output
     * @return
     */
    public static long map(long x, long in_min, long in_max, long out_min, long out_max)
    {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }
}
