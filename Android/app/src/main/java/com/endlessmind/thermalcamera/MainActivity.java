package com.endlessmind.thermalcamera;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothDevice;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.endlessmind.thermalcamera.helpers.DrawHelper;
import com.endlessmind.thermalcamera.network.UDPSocket;
import com.endlessmind.thermalcamera.views.OverlayView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;



public class MainActivity extends AppCompatActivity implements View.OnTouchListener {
    final String TAG = "MainActivity";
    private UDPSocket mUdpClient;
    private String mServerAddressBroadCast = "192.168.4.1";//"192.168.1.230";
    InetAddress mServerAddr;
    int mServerPort = 6868;

    final byte[] mRequestConnect      = new byte[]{'w','h','o','a','m','i'};
    final byte[] mLedOn = new byte[]{'l','e','d','o','n'};
    final byte[] mLedOff = new byte[]{'l','e','d','o','f','f'};
    double[] thermalBytes;
    private Bitmap mBitmap;
    double voltage, soc = 50f;
    double maxTemp = 0, minTemp = 255;

    ImageView mServerImageView;
    Handler mHandler = new Handler();

    private WebSocketClient mWebSocketClient;
    private String mServerExactAddress;
    private boolean mInitStream = false;
    private boolean mInitTrackObj = false;
    private boolean mStream = false;
    private boolean mObjDet = false;
    private boolean mLed = false;
    private boolean connect = false;



    private final Size CamResolution = new Size(640, 480);

    private OverlayView mTrackingOverlay;
    private Bitmap mBitmapDebug;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mServerImageView = findViewById(R.id.imageView);



        mUdpClient = new UDPSocket(12345);
        mUdpClient.runUdpServer();

        try {
            mServerAddr = InetAddress.getByName(mServerAddressBroadCast);
        }catch (Exception e){
            e.printStackTrace();
        }
        mServerImageView.setOnTouchListener(MainActivity.this);
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    private void connectWebSocket() {
        URI uri;
        try {
            uri = new URI("ws://"+mServerExactAddress+":86/");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.d("Websocket", "Open");
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.d("Websocket", "Closed " + s);
                //§ if (connect)
                    //connectWebSocket();
            }

            @Override
            public void onMessage(String message){

                    try {
                        JSONObject obj = new JSONObject(message);
                        if (message.length() > 768) { //Thermal data would be a lot bigger than this. There is 768 of datapoints, not counting delimiters and decimals.
                            JSONArray dataPoints = obj.getJSONArray("data");
                            thermalBytes = new double[dataPoints.length()]; //Our new points will be float/double
                            for (int i = 0; i < dataPoints.length(); i++) {
                                thermalBytes[i] =  dataPoints.getDouble(i);
                            }

                            maxTemp = 0; minTemp = 255; //reset the temps for each frame
                            for (double b : thermalBytes) {
                                if (b > maxTemp)
                                    maxTemp = b;

                                if (b < minTemp)
                                    minTemp = b;
                            }
                        } else {
                            if (obj.has("voltage")) {
                                voltage = obj.getDouble("voltage");
                            }
                            if (obj.has("soc")) {
                                soc = obj.getDouble("soc");
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }


                Log.d("Websocket", message);
            }

            @Override
            public void onMessage(ByteBuffer message){
//                Log.d("Websocket", "Receive");
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        byte[] imageBytes= new byte[message.remaining()];
                        message.get(imageBytes);
                        if (message.limit() == 768) {
                            //thermalBytes = imageBytes.clone();


                        } else {

                            final Bitmap bmp= BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);

                            if (bmp == null)
                            {
                                return;
                            }
                            int viewWidth = mServerImageView.getWidth();
                            Matrix matrix = new Matrix();
                            matrix.postRotate(90);
                            //matrix.preScale(1.6f, 1f);

                            int bmpWidth = bmp.getWidth();
                            int bmpHeight = bmp.getHeight();
                            //Bitmap croppedBmp = Bitmap.createBitmap(bmp, 32, 24, bmp.getWidth() - 32, bmp.getHeight() - 24);

                            final Bitmap bmp_traspose = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true );
                            Bitmap croppedBmp = Bitmap.createScaledBitmap(bmp_traspose, (int) (bmp.getWidth() * 1.1f), (int) (bmp.getHeight() * 1.5f), false);
                            Bitmap dstBitmap = Bitmap.createBitmap(
                                    bmp.getWidth() , // Width
                                    bmp.getHeight() , // Height
                                    Bitmap.Config.ARGB_8888 // Config
                            );

                            Canvas canvas = new Canvas(dstBitmap);
                            canvas.drawBitmap(
                                    croppedBmp, // Bitmap
                                    0, // Left
                                    -(bmpHeight * 0.2f), // Top
                                    null // Paint
                            );
                            if (thermalBytes != null && thermalBytes.length > 0) {
                                Bitmap ThermalBmp = DrawHelper.drawThermal(thermalBytes, bmp.getHeight(), bmp.getWidth());
                                canvas.drawBitmap(
                                        ThermalBmp, // Bitmap
                                        0, // Left
                                        0, // Top
                                        null // Paint
                                );
                            }

                            DrawHelper.drawBatteryStatus(canvas, (int)soc,voltage, maxTemp, minTemp, 60f, 25f, 6f);
                            Bitmap icon = Bitmap.createScaledBitmap(DrawHelper.getTintentIcon(MainActivity.this, mLed), 36, 36, false);
                            Bitmap iconShadowed = DrawHelper.addShadow(icon, icon.getHeight(), icon.getWidth(), Color.BLACK, 1, 1,3);
                            canvas.drawBitmap(DrawHelper.createFlippedBitmap(iconShadowed, true, false), dstBitmap.getWidth() - (icon.getWidth() * 1.2f) , 10f, null);

                            float imagRatio = (float)bmp.getHeight()/(float)bmp.getWidth();
                            int dispViewH = (int)(viewWidth*imagRatio);
                            mServerImageView.setImageBitmap(DrawHelper.createFlippedBitmap(dstBitmap, true, false));
                        }
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.d("Websocket", "Error " + e.getMessage());
            }
        };
        mWebSocketClient.connect();
    }

    private void handleConnect() {

        if (!mStream && connect) {
            try {
                mServerAddr = InetAddress.getByName(mServerAddressBroadCast);
            }catch (Exception e){
                e.printStackTrace();
            }
            mUdpClient.sendBytes(mServerAddr, mServerPort, mRequestConnect);
            Pair<SocketAddress, String> res = mUdpClient.getResponse();
            int cnt = 3;
            while (res.first == null && cnt > 0) {
                res = mUdpClient.getResponse();
                cnt--;
            }
            if (res.first != null) {
                Log.d(TAG, res.first.toString() + ":" + res.second);
                mServerExactAddress = res.first.toString().split(":")[0].replace("/","");
                mStream = true;
                connectWebSocket();
                try {
                    mServerAddr = InetAddress.getByName(mServerExactAddress);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }else{
                Toast toast = Toast.makeText(MainActivity.this, "Cannot connect to ESP32 Camera", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        } else {
            mStream = false;
            mWebSocketClient.close();
        }
    }



    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (view == mServerImageView) {
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                float xPos = motionEvent.getX() - 500;
                float yPos = motionEvent.getY();

                if (xPos < 100 && xPos > 0) {
                    if (yPos < 100 && yPos > 0) {
                        Log.e(MainActivity.class.getSimpleName(), "YES!");
                        if (mLed) {
                            mUdpClient.sendBytes(mServerAddr, mServerPort, mLedOff);
                        } else {
                            mUdpClient.sendBytes(mServerAddr, mServerPort, mLedOn);

                        }
                        mLed = !mLed;
                    } else {
                        Log.e(MainActivity.class.getSimpleName(), "X: " +  xPos + " - Y: " + yPos);
                        connect = !connect;
                        handleConnect();
                    }
                } else {
                    Log.e(MainActivity.class.getSimpleName(), "X: " +  xPos + " - Y: " + yPos);
                    connect = !connect;
                    handleConnect();
                }
            }
        }
        return true;
    }
}