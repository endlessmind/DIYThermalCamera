package com.endlessmind.thermalcamera;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothDevice;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.endlessmind.thermalcamera.helpers.DrawHelper;
import com.endlessmind.thermalcamera.network.UDPSocket;
import com.endlessmind.thermalcamera.views.OverlayView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;

import me.aflak.bluetooth.Bluetooth;
import me.aflak.bluetooth.interfaces.BluetoothCallback;
import me.aflak.bluetooth.interfaces.DeviceCallback;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, BluetoothCallback, DeviceCallback {
    final String TAG = "MainActivity";
    private UDPSocket mUdpClient;
    private String mServerAddressBroadCast = "192.168.1.230";
    InetAddress mServerAddr;
    int mServerPort = 6868;

    final byte[] mRequestConnect      = new byte[]{'w','h','o','a','m','i'};
    final byte[] mLedOn = new byte[]{'l','e','d','o','n'};
    final byte[] mLedOff = new byte[]{'l','e','d','o','f','f'};
    byte[] thermalBytes;
    private Bitmap mBitmap;

    ImageView mServerImageView;
    Handler mHandler = new Handler();

    private WebSocketClient mWebSocketClient;
    private String mServerExactAddress;
    private boolean mInitStream = false;
    private boolean mInitTrackObj = false;
    private boolean mStream = false;
    private boolean mObjDet = false;
    private boolean mLed = false;

    Bluetooth bluetooth;

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
        mServerImageView.setOnClickListener(MainActivity.this);
        bluetooth = new Bluetooth(this);
        bluetooth.setBluetoothCallback(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bluetooth.onStart();
        if(bluetooth.isEnabled()){
            // doStuffWhenBluetoothOn() ...
            bluetooth.connectToNameWithPortTrick("ThermalESP");
        } else {
            bluetooth.showEnableDialog(MainActivity.this);
        }
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
            }

            @Override
            public void onMessage(String message){
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
                            thermalBytes = imageBytes.clone();
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
                            Bitmap croppedBmp = Bitmap.createScaledBitmap(bmp_traspose, bmp.getWidth(), (int) (bmp.getHeight() * 1.5f), false);
                            Bitmap dstBitmap = Bitmap.createBitmap(
                                    bmp.getWidth() , // Width
                                    bmp.getHeight() , // Height
                                    Bitmap.Config.ARGB_8888 // Config
                            );

                            Canvas canvas = new Canvas(dstBitmap);
                            canvas.drawBitmap(
                                    croppedBmp, // Bitmap
                                    0, // Left
                                    -(bmpHeight * 0.3f), // Top
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

                            float imagRatio = (float)bmp.getHeight()/(float)bmp.getWidth();
                            int dispViewH = (int)(viewWidth*imagRatio);
                            mServerImageView.setImageBitmap(dstBitmap);//Bitmap.createScaledBitmap(dstBitmap, viewWidth, dispViewH, false));
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

    @Override
    public void onClick(View view) {
        if (view == mServerImageView) {
            if (!mStream) {
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
                    //((Button) getActivity().findViewById(R.id.streamBtn)).setBackgroundResource(R.drawable.my_button_bg_2);
                    //((Button) getActivity().findViewById(R.id.streamBtn)).setTextColor(Color.rgb(0,0,255));
                    try {
                        mServerAddr = InetAddress.getByName(mServerExactAddress);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }else{
                    Toast toast =
                            Toast.makeText(
                                    MainActivity.this, "Cannot connect to ESP32 Camera", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            } else {
                mStream = false;
                mWebSocketClient.close();
                //((Button) getActivity().findViewById(R.id.streamBtn)).setBackgroundResource(R.drawable.my_button_bg);
                //((Button) getActivity().findViewById(R.id.streamBtn)).setTextColor(Color.rgb(255,255,255));
            }
        }
    }

    @Override
    public void onBluetoothTurningOn() {

    }

    @Override
    public void onBluetoothOn() {
        bluetooth.connectToNameWithPortTrick("ThermalESP");
    }

    @Override
    public void onBluetoothTurningOff() {

    }

    @Override
    public void onBluetoothOff() {

    }

    @Override
    public void onUserDeniedActivation() {

    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        Log.e("MainActivity", device.getName());
        bluetooth.send("{\"action\":1, \"ssid\":\"Spanbil #71\", \"pass\": \"3RedApples\"");
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device, String message) {

    }

    @Override
    public void onMessage(byte[] message) {

    }

    @Override
    public void onError(int errorCode) {

    }

    @Override
    public void onConnectError(BluetoothDevice device, String message) {
        Log.e("MainActivity", message);
    }
}