package com.endlessmind.thermalcamera.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

import com.endlessmind.thermalcamera.R;

public class DrawHelper {

    static int camColors[] = {0x480F,
            0x400F,0x400F,0x400F,0x4010,0x3810,0x3810,0x3810,0x3810,0x3010,0x3010,
            0x3010,0x2810,0x2810,0x2810,0x2810,0x2010,0x2010,0x2010,0x1810,0x1810,
            0x1811,0x1811,0x1011,0x1011,0x1011,0x0811,0x0811,0x0811,0x0011,0x0011,
            0x0011,0x0011,0x0011,0x0031,0x0031,0x0051,0x0072,0x0072,0x0092,0x00B2,
            0x00B2,0x00D2,0x00F2,0x00F2,0x0112,0x0132,0x0152,0x0152,0x0172,0x0192,
            0x0192,0x01B2,0x01D2,0x01F3,0x01F3,0x0213,0x0233,0x0253,0x0253,0x0273,
            0x0293,0x02B3,0x02D3,0x02D3,0x02F3,0x0313,0x0333,0x0333,0x0353,0x0373,
            0x0394,0x03B4,0x03D4,0x03D4,0x03F4,0x0414,0x0434,0x0454,0x0474,0x0474,
            0x0494,0x04B4,0x04D4,0x04F4,0x0514,0x0534,0x0534,0x0554,0x0554,0x0574,
            0x0574,0x0573,0x0573,0x0573,0x0572,0x0572,0x0572,0x0571,0x0591,0x0591,
            0x0590,0x0590,0x058F,0x058F,0x058F,0x058E,0x05AE,0x05AE,0x05AD,0x05AD,
            0x05AD,0x05AC,0x05AC,0x05AB,0x05CB,0x05CB,0x05CA,0x05CA,0x05CA,0x05C9,
            0x05C9,0x05C8,0x05E8,0x05E8,0x05E7,0x05E7,0x05E6,0x05E6,0x05E6,0x05E5,
            0x05E5,0x0604,0x0604,0x0604,0x0603,0x0603,0x0602,0x0602,0x0601,0x0621,
            0x0621,0x0620,0x0620,0x0620,0x0620,0x0E20,0x0E20,0x0E40,0x1640,0x1640,
            0x1E40,0x1E40,0x2640,0x2640,0x2E40,0x2E60,0x3660,0x3660,0x3E60,0x3E60,
            0x3E60,0x4660,0x4660,0x4E60,0x4E80,0x5680,0x5680,0x5E80,0x5E80,0x6680,
            0x6680,0x6E80,0x6EA0,0x76A0,0x76A0,0x7EA0,0x7EA0,0x86A0,0x86A0,0x8EA0,
            0x8EC0,0x96C0,0x96C0,0x9EC0,0x9EC0,0xA6C0,0xAEC0,0xAEC0,0xB6E0,0xB6E0,
            0xBEE0,0xBEE0,0xC6E0,0xC6E0,0xCEE0,0xCEE0,0xD6E0,0xD700,0xDF00,0xDEE0,
            0xDEC0,0xDEA0,0xDE80,0xDE80,0xE660,0xE640,0xE620,0xE600,0xE5E0,0xE5C0,
            0xE5A0,0xE580,0xE560,0xE540,0xE520,0xE500,0xE4E0,0xE4C0,0xE4A0,0xE480,
            0xE460,0xEC40,0xEC20,0xEC00,0xEBE0,0xEBC0,0xEBA0,0xEB80,0xEB60,0xEB40,
            0xEB20,0xEB00,0xEAE0,0xEAC0,0xEAA0,0xEA80,0xEA60,0xEA40,0xF220,0xF200,
            0xF1E0,0xF1C0,0xF1A0,0xF180,0xF160,0xF140,0xF100,0xF0E0,0xF0C0,0xF0A0,
            0xF080,0xF060,0xF040,0xF020,0xF800};

    public static Bitmap createFlippedBitmap(Bitmap source, boolean xFlip, boolean yFlip) {
        Matrix matrix = new Matrix();
        matrix.postScale(xFlip ? -1 : 1, yFlip ? -1 : 1, source.getWidth() / 2f, source.getHeight() / 2f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public static Bitmap getTintentIcon(Context c, boolean led) {
        Bitmap originalBitmap = BitmapFactory.decodeResource(c.getResources(),led ? R.drawable.flash_on_icon : R.drawable.flash_off_icon);
        Bitmap newStrokedBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newStrokedBitmap);
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(originalBitmap, 0, 0, paint);
        return newStrokedBitmap;
    }

    public static Bitmap addShadow(final Bitmap bm, final int dstHeight, final int dstWidth, int color, int size, float dx, float dy) {
        final Bitmap mask = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ALPHA_8);

        final Matrix scaleToFit = new Matrix();
        final RectF src = new RectF(0, 0, bm.getWidth(), bm.getHeight());
        final RectF dst = new RectF(0, 0, dstWidth - dx, dstHeight - dy);
        scaleToFit.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);

        final Matrix dropShadow = new Matrix(scaleToFit);
        dropShadow.postTranslate(dx, dy);

        final Canvas maskCanvas = new Canvas(mask);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskCanvas.drawBitmap(bm, scaleToFit, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
        maskCanvas.drawBitmap(bm, dropShadow, paint);

        final BlurMaskFilter filter = new BlurMaskFilter(size, BlurMaskFilter.Blur.NORMAL);
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setMaskFilter(filter);
        paint.setFilterBitmap(true);

        final Bitmap ret = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888);
        final Canvas retCanvas = new Canvas(ret);
        retCanvas.drawBitmap(mask, 0,  0, paint);
        retCanvas.drawBitmap(bm, scaleToFit, null);
        mask.recycle();
        return ret;
    }

    static public Path RoundedRect(float left, float top, float right, float bottom, float rx, float ry, boolean conformToOriginalPost) {
        Path path = new Path();
        if (rx < 0) rx = 0;
        if (ry < 0) ry = 0;
        float width = right - left;
        float height = bottom - top;
        if (rx > width/2) rx = width/2;
        if (ry > height/2) ry = height/2;
        float widthMinusCorners = (width - (2 * rx));
        float heightMinusCorners = (height - (2 * ry));

        path.moveTo(right, top + ry);
        path.rQuadTo(0, -ry, -rx, -ry);//top-right corner
        path.rLineTo(-widthMinusCorners, 0);
        path.rQuadTo(-rx, 0, -rx, ry); //top-left corner
        path.rLineTo(0, heightMinusCorners);

        if (conformToOriginalPost) {
            path.rLineTo(0, ry);
            path.rLineTo(width, 0);
            path.rLineTo(0, -ry);
        }
        else {
            path.rQuadTo(0, ry, rx, ry);//bottom-left corner
            path.rLineTo(widthMinusCorners, 0);
            path.rQuadTo(rx, 0, rx, -ry); //bottom-right corner
        }

        path.rLineTo(0, -heightMinusCorners);

        path.close();//Given close, last lineto can be removed.

        return path;
    }

    public static void drawBatteryStatus(Canvas canvas,int soc, double voltage, int max, int min, float w, float h, float round) {
        //Remember, this is mirrored later on.
        float width = w, height =h, left = 20f, top = 10f;
        float lineOffset = 2f;
        Paint rectPaint = new Paint();
        rectPaint.setColor(Color.GRAY);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(4f);
        canvas.drawPath(RoundedRect(left, top,  width + left,  height + top, round, round, false), rectPaint);
        canvas.drawLine(left - 3,top + 3,left -3,(height + top) - 3, rectPaint);

        if (soc > 0.9d) {
            rectPaint.setStyle(Paint.Style.FILL);
            int wholePercent = (int)soc;

            if (wholePercent < 60 && wholePercent > 24) {
                rectPaint.setColor(Color.rgb(255, 165, 0));
            } else if (wholePercent < 25) {
                rectPaint.setColor(Color.RED);
            } else {
                rectPaint.setColor(Color.GREEN);
            }

            float maxLenght = width - 4;
            float lenght = maxLenght * ((float)wholePercent / 100f);
            canvas.drawPath(DrawHelper.RoundedRect( (left + lineOffset) + (maxLenght - lenght), top + lineOffset,  (width + left) - lineOffset,  (height + top) - lineOffset, 0f, 0f, true), rectPaint);

            String procentage = soc + "%";
            String volStr = voltage + "v";
            String maxStr = "Max: " + max + "ºC";
            String minStr = "Min: " + min + "ºC";

            canvas.save();
            canvas.scale(-1f,1f, canvas.getWidth() / 2f, canvas.getHeight() / 2f);
            float textSize = height * 0.9f;
            rectPaint.setAntiAlias(true);
            rectPaint.setColor(Color.WHITE);
            rectPaint.setFakeBoldText(true);
            rectPaint.setTextSize(textSize);


            float strLen = rectPaint.measureText(procentage);
            float xPos = canvas.getWidth() - (left + (width / 2));
            float yPos = top + ((height + lineOffset) / 2);
            float finalY = yPos - ((rectPaint.descent() + rectPaint.ascent()) / 2);
            canvas.drawText(procentage, xPos - (strLen / 2), finalY, rectPaint);

            rectPaint.setColor(Color.BLACK);
            rectPaint.setStyle(Paint.Style.STROKE);
            rectPaint.setStrokeWidth(1f);
            canvas.drawText(procentage, xPos - (strLen / 2), finalY, rectPaint);


            rectPaint.setColor(Color.WHITE);
            rectPaint.setStyle(Paint.Style.FILL);
            strLen = rectPaint.measureText(volStr);
            yPos = top + height + lineOffset;
            canvas.drawText(volStr, xPos - (strLen / 2), yPos + textSize, rectPaint);

            rectPaint.setColor(Color.BLACK);
            rectPaint.setStyle(Paint.Style.STROKE);
            rectPaint.setStrokeWidth(1f);
            canvas.drawText(volStr, xPos - (strLen / 2), yPos + textSize, rectPaint);


            rectPaint.setColor(Color.WHITE);
            rectPaint.setStyle(Paint.Style.FILL);
            strLen = rectPaint.measureText(minStr);
            xPos = canvas.getWidth() - (left + (width + 20));
            canvas.drawText(minStr, xPos - (strLen * 2.2f), finalY, rectPaint);

            rectPaint.setColor(Color.BLACK);
            rectPaint.setStyle(Paint.Style.STROKE);
            rectPaint.setStrokeWidth(1f);
            canvas.drawText(minStr, xPos - (strLen * 2.2f), finalY, rectPaint);

            rectPaint.setColor(Color.WHITE);
            rectPaint.setStyle(Paint.Style.FILL);
            strLen = rectPaint.measureText(maxStr);
            xPos = canvas.getWidth() - (left + (width + 20));
            canvas.drawText(maxStr, xPos - (strLen), finalY, rectPaint);

            rectPaint.setColor(Color.BLACK);
            rectPaint.setStyle(Paint.Style.STROKE);
            rectPaint.setStrokeWidth(1f);
            canvas.drawText(maxStr, xPos - (strLen), finalY, rectPaint);


            canvas.scale(1f,1f, canvas.getWidth() / 2f, canvas.getHeight() / 2f);
            canvas.restore();
        }
    }



    public static Bitmap drawThermal(byte[] imageBytes, int height, int width) {
        //Byte array should have a site of 768 bytes:
        Bitmap dstBitmap = Bitmap.createBitmap(
                width , // Width
                height , // Height
                Bitmap.Config.ARGB_8888 // Config
        );
        Canvas c = new Canvas(dstBitmap);
        if (imageBytes.length == 768) {
            int hMulti = height / 24;
            int wMulti = width / 32;
            int maxTemp = 0, minTemp = 255;
            for (byte b : imageBytes) {
                if (b > maxTemp)
                    maxTemp = b;

                if (b < minTemp)
                    minTemp = b;
            }
            /*
            if (maxTemp < 50 && maxTemp > 32) {
                maxTemp = 50;
            }
            if (maxTemp <33 && maxTemp > 15) {
                maxTemp = 33;
            } */
            Paint rectPaint = new Paint();

            for (int h=0; h<24; h++) {
                for (int w=0; w<32; w++) {
                    int t = imageBytes[h*32 + w];

                    int colorIndex = (int) MathHelper.map(t, minTemp, maxTemp, 0, 255);
                    int colorCode = camColors[colorIndex];
                    int red = (colorCode & 0xF800) >> 8;;
                    int green = (colorCode & 0x07E0) >> 3;
                    int blue = (colorCode & 0x1F) << 3;
                    rectPaint.setColor(Color.rgb(red, green, blue));
                    rectPaint.setAlpha(160);
                    Rect r = new Rect();
                    r.left = w * wMulti;
                    r.right = (w +1) * wMulti;
                    r.top = h * hMulti;
                    r.bottom = (h + 1) * hMulti;

                    c.drawRect(r, rectPaint);
                }
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(180);
            Bitmap bmp_traspose = Bitmap.createBitmap(dstBitmap, 0, 0, dstBitmap.getWidth(), dstBitmap.getHeight(), matrix, true );
            return bmp_traspose;
        } else {
            return null; //Size is not what we expect for this operation.
        }

    }
}
