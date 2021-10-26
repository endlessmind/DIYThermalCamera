package com.endlessmind.thermalcamera.helpers;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

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

    public static double map(double x, double in_min, double in_max, double out_min, double out_max)
    {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public static double getTempAtPoint(MotionEvent mEvent,  int bW, int bH, View image, double[] imageBytes) {
        //A lot of screen size, pixel density and aspect ration compensation.. kind of.. sounds cool and complex tho..
        int height = image.getHeight();
        int width = image.getWidth();
        //The ImageView is not in top-left corner of the display, so we compensate for that.
        float x = mEvent.getX() - image.getLeft();
        float y = mEvent.getY() - image.getTop();

        //The bitmap will be scaled up to fit the devices screen height, we need to calculate the scaling factor.
        float heightR =  (float)height / (float)bH;
        float widthR = (float)width / (float)bW;
        //and then we can use that to compensate for the scaling
        x = x / widthR;
        y = y / heightR;

        /* This is just a dog

           / \__
          (    @\___
          /         O
         /   (_____/
        /_____/

         */

        if (imageBytes.length >= 768) {
            int hMulti = height / 24;
            int wMulti = width / 32;

            for (int h=0; h<24; h++) {
                for (int w=0; w<32; w++) {
                    double t = imageBytes[h*32 + w];
                    Rect r = new Rect();
                    r.left = w * wMulti;
                    r.right = (w +1) * wMulti;
                    r.top = h * hMulti;
                    r.bottom = (h + 1) * hMulti;
                    if (r.contains((int)x,(int)y)) {
                        return t;
                    }
                }
            }
        }

        return 0.0d;
    }
}
