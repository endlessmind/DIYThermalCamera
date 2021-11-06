package com.endlessmind.thermalcamera.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.endlessmind.thermalcamera.MainActivity;
import com.endlessmind.thermalcamera.helpers.DrawHelper;

public class ThermalImageView extends androidx.appcompat.widget.AppCompatImageView implements View.OnTouchListener {

    public ThermalImageView(@NonNull Context context) {
        super(context);
    }

    public ThermalImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ThermalImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public interface LedButtonClickedListener {
        void onLedClicked(boolean status);
    }

    MotionEvent lastTap;
    double[] thermalBytes;
    double voltage, soc = 50f;
    double maxTemp = 0, minTemp = 255;

    private boolean mLed = false;
    private boolean gotThermal = false;
    LedButtonClickedListener mledList;



    public void setLedClickListener(LedButtonClickedListener list ) {
        mledList = list;
    }

    public LedButtonClickedListener getLedClickListener() {
        return mledList;
    }

    public void setOverlayValues(double v, double s, double min, double max) {
        voltage = v;
        soc = s;
        minTemp = min;
        maxTemp = max;
    }

    public void updateBufferts(double[] thr, byte[] imageBytes) {
        gotThermal = thr != null;

        thermalBytes = thr;

        final Bitmap bmp= BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);
        if (bmp == null) { return; }

        int viewHeight = this.getHeight();
        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        int bmpWidth = bmp.getWidth();
        int bmpHeight = bmp.getHeight();

        final Bitmap bmp_traspose = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true );
        Bitmap croppedBmp = Bitmap.createScaledBitmap(bmp_traspose, (int) (bmp.getWidth() * 1.1f), (int) (bmp.getHeight() * 1.5f), false);
        Bitmap dstBitmap = Bitmap.createBitmap(bmp.getWidth() , bmp.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(dstBitmap);
        canvas.drawBitmap(croppedBmp, 0, -(bmpHeight * 0.2f), null);
        if (gotThermal) {
            Bitmap ThermalBmp = DrawHelper.drawThermal(thermalBytes, bmp.getHeight(), bmp.getWidth());
            canvas.drawBitmap(ThermalBmp,0,0,null);
        }

        DrawHelper.drawBatteryStatus(canvas, (int)soc,voltage, maxTemp, minTemp, 60f, 25f, 6f);
        if (lastTap != null) {
            DrawHelper.drawCrosshair(canvas, lastTap, dstBitmap.getWidth(), dstBitmap.getHeight(), this);
        } else {
            MotionEvent ev = MotionEvent.obtain(100, 100, 1, bmpWidth / 2, bmpHeight / 2, 1);
            DrawHelper.drawCrosshair(canvas, ev, dstBitmap.getWidth(), dstBitmap.getHeight(), this);
        }

        Bitmap icon = Bitmap.createScaledBitmap(DrawHelper.getTintentIcon(this.getContext(), mLed), 36, 36, false);
        Bitmap iconShadowed = DrawHelper.addShadow(icon, icon.getHeight(), icon.getWidth(), Color.BLACK, 1, 1,3);
        canvas.drawBitmap(DrawHelper.createFlippedBitmap(iconShadowed, true, false), dstBitmap.getWidth() - (icon.getWidth() * 1.2f) , 10f, null);

        float imagRatio = (float)dstBitmap.getWidth()/(float)dstBitmap.getHeight();
        int dispViewH = (int)(viewHeight*imagRatio);

        this.setImageBitmap(DrawHelper.createFlippedBitmap(dstBitmap, true, false));
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            float xPos = motionEvent.getX();
            float yPos = motionEvent.getY();


            if (xPos < 100 && xPos > 0) {
                if (yPos < 100 && yPos > 0) {
                    if (mledList != null)
                        mledList.onLedClicked(mLed);

                    mLed = !mLed;
                } else {
                    lastTap = motionEvent;
                }
            } else {
                lastTap = motionEvent;
            }
        }
        return true;
    }
}
