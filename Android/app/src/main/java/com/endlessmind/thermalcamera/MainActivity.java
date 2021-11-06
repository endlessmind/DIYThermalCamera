package com.endlessmind.thermalcamera;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.endlessmind.thermalcamera.network.UDPSocket;
import com.endlessmind.thermalcamera.views.OverlayView;
import com.endlessmind.thermalcamera.views.ThermalImageView;

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


public class MainActivity extends AppCompatActivity implements View.OnClickListener, ThermalImageView.LedButtonClickedListener {
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

    ThermalImageView thrmImageView;
    LinearLayout llRoot;
    Handler mHandler = new Handler();

    private WebSocketClient mWebSocketClient;
    private String mServerExactAddress;
    private boolean mInitStream = false;
    private boolean mInitTrackObj = false;
    private boolean mStream = false;
    private boolean mObjDet = false;
    private boolean connect = false;
    private boolean gotThermal = false;


    MotionEvent lastTap;
    private final Size CamResolution = new Size(640, 480);



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thrmImageView = findViewById(R.id.imageView);
        llRoot = findViewById(R.id.llRoot);


        mUdpClient = new UDPSocket(12345);
        mUdpClient.runUdpServer();

        try {
            mServerAddr = InetAddress.getByName(mServerAddressBroadCast);
        }catch (Exception e){
            e.printStackTrace();
        }
        //thrmImageView.setOnTouchListener(MainActivity.this);
        llRoot.setOnClickListener(MainActivity.this);
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
                            gotThermal = true;
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
                            Log.d("Websocket", message);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
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
                            //mServerImageView.setImageBitmap(DrawHelper.createFlippedBitmap(dstBitmap, true, false));
                            thrmImageView.setOverlayValues(voltage, soc, minTemp, maxTemp);
                            thrmImageView.updateBufferts(gotThermal ? thermalBytes : null,imageBytes);
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
            if (mWebSocketClient != null)
                mWebSocketClient.close();
        }
    }



    @Override
    public void onClick(View view) {
        if (view == llRoot) {
            connect = !connect;
            handleConnect();
        }
    }

    @Override
    public void onLedClicked(boolean status) {
        if (status) {
            mUdpClient.sendBytes(mServerAddr, mServerPort, mLedOff);
        } else {
            mUdpClient.sendBytes(mServerAddr, mServerPort, mLedOn);

        }
    }
}