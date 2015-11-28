package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 11/9/2015.
 */
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.MultiChannelTemperature;
import com.mbientlab.metawear.module.Timer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.lang.Math;
import java.util.List;

public class ForegroundService extends Service implements ServiceConnection{
    private static final String LOG_TAG = "ForegroundService", LOG_ERR = "http_err";
    private MetaWearBleService.LocalBinder ServiceBinder;
    private final ArrayList<String> SENSOR_MAC = new ArrayList<>();
    private final ArrayList<BoardObject> boards = new ArrayList<>();

    public static boolean IS_SERVICE_RUNNING = false;

    private void initParams() {
//        At Joshua's place
//        SENSOR_MAC.add("C7:1C:99:0F:9D:00"); //RG

//        SENSOR_MAC.add("D7:06:C0:09:F7:7F"); //R
//        SENSOR_MAC.add("F6:E0:22:68:49:AF"); //R
        SENSOR_MAC.add("E1:B1:1A:7D:8C:35"); //RG
        SENSOR_MAC.add("F2:9F:9C:02:AF:65"); //RG
        SENSOR_MAC.add("F5:AB:48:BC:10:6B"); //RPro
        SENSOR_MAC.add("EA:B2:F1:47:04:E7"); //RPro

//        4 Sensors for demo
//        SENSOR_MAC.add("DB:D1:AD:E3:E9:C3"); //RG
//        SENSOR_MAC.add("F2:50:71:B0:AE:E1"); //RG
//        SENSOR_MAC.add("DF:1C:5C:3F:F2:39"); //RG
//        SENSOR_MAC.add("D4:CC:1A:AE:D4:FB"); //RG
    }


    @Override
    public void onCreate() {
        super.onCreate();
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);
        initParams();
        Log.i(LOG_TAG, "successfully on create");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Start Foreground Intent ");
            showNotification();
//            Toast.makeText(this, "Service Started!", Toast.LENGTH_SHORT).show();

        } else if (intent.getAction().equals(
                Constants.ACTION.STOPFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    private void showNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_launcher);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("SilverLink")
                .setTicker("SilverLink")
                .setContentText("SilverLink Gateway is Running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();
        Log.i(LOG_TAG, "successfully build a notification");
        startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                notification);
        Log.i(LOG_TAG, "started foreground");

    }

    @Override
    public void onDestroy() {
        for (int i = 0; i < SENSOR_MAC.size(); i++) {
            boards.get(i).accel_module.stop();
            boards.get(i).accel_module.disableAxisSampling();
            boards.get(i).ActiveDisconnect = true;
            boards.get(i).board.disconnect();
        }
        super.onDestroy();
        getApplicationContext().unbindService(this);
        Log.i(LOG_TAG, "In onDestroy");
//        Toast.makeText(this, "Service Detroyed!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case if services are bound (Bound Services).
        return null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ServiceBinder = (MetaWearBleService.LocalBinder) service;

        BluetoothAdapter btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (!btAdapter.isEnabled()) {
            btAdapter.enable();
        }
        while (!btAdapter.isEnabled()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Log.i(LOG_TAG, "BT enabled");

        for (int i = 0; i < SENSOR_MAC.size(); i++){
            BluetoothDevice btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(i));
            boards.add(new BoardObject(ServiceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(i), 12.5f));
            Log.i(LOG_TAG, "Board added");
        }

        for (int i = 0; i < SENSOR_MAC.size();i++){
            try {
                Thread.sleep(7000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            boards.get(i).sensor_status = boards.get(i).CONNECTING;
//            changeText(boards.get(i).MAC_ADDRESS, boards.get(i).CONNECTING);
            boards.get(i).board.connect();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }


    public String getJSON (String name, ArrayList<String> data) {
        JSONObject jsonstring = new JSONObject();
        JSONArray logs = new JSONArray();
        try {
            jsonstring.put("s", name);
            for (String s : data) {
                JSONObject temp = new JSONObject();
                temp.put("t", Double.valueOf(s.split(",")[0]));
                temp.put("x", Integer.valueOf(s.split(",")[1]));
                temp.put("y", Integer.valueOf(s.split(",")[2]));
                temp.put("z", Integer.valueOf(s.split(",")[3]));
                logs.put(temp);
            }
            jsonstring.put("logs", logs);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonstring.toString();
    }

    public String getJSON (String name, String ts, String temperature) {
        JSONObject jsonstring = new JSONObject();
        try {
            jsonstring.put("s", name);
            jsonstring.put("t", Double.valueOf(ts));
            jsonstring.put("c", Float.valueOf(temperature));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonstring.toString();
    }

    public class postDataAsync extends AsyncTask<String, Boolean, String> {
        String urlbase = "http://data.silverlink247.com/logs";
        @Override
        protected String doInBackground(String... params) {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            if (netInfo != null && netInfo.isConnected()) {
                try {
                    URL url = new URL(urlbase);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("content-type", "application/json");
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(params[0]);
                    writer.flush();
                    writer.close();
                    os.close();

                    conn.connect();

                    int response = conn.getResponseCode();
                    if (200 <= response && response < 300) {
                        Log.i(LOG_TAG, "Post succeed: " + params[0]);
                    } else {
                        Log.e(LOG_ERR, "Post error code: " + response + " " + params[0]);
                    }
                } catch (MalformedURLException e) {
                    Log.e(LOG_ERR, "Illegal URL");
                } catch (IOException e) {
                    Log.e(LOG_ERR, "Connection error " + params[0]);
                }
            } else {
                Log.e(LOG_ERR, "No active connection");
            }
            return null;
        }
    }

    public class postTempAsync extends AsyncTask<String, Boolean, String> {
        String urlbase = "http://io.silverlink247.com/temperature";
        @Override
        protected String doInBackground(String... params) {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            if (netInfo != null && netInfo.isConnected()) {
                try {
                    URL url = new URL(urlbase);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("content-type", "application/json");
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(params[0]);
                    writer.flush();
                    writer.close();
                    os.close();

                    conn.connect();

                    int response = conn.getResponseCode();
                    if (200 <= response && response < 300) {
                        Log.i(LOG_TAG, "Post succeed: " + params[0]);
                    } else {
                        Log.e(LOG_ERR, "Post error code: " + response + " " + params[0]);
                    }
                } catch (MalformedURLException e) {
                    Log.e(LOG_ERR, "Illegal URL");
                } catch (IOException e) {
                    Log.e(LOG_ERR, "Connection error " + params[0]);
                }
            } else {
                Log.e(LOG_ERR, "No active connection");
            }
            return null;
        }
    }

    public class BoardObject {
        private final String CONNECTED = "Connected.\nStreaming Data",
                DISCONNECTED = "Lost connection.\nReconnecting",
                FAILURE = "Connection error.\nReconnecting",
                CONNECTING = "Connecting",
                LOG_TAG = "Board_Log";
        public MetaWearBoard board;
        public Accelerometer accel_module;
        public long[] startTimestamp;
        public ArrayList<String> dataCache;
        public int dataCount;
        public String MAC_ADDRESS;
        private float sampleFreq;
        private int uploadCount;
        private float sampleInterval;
        public boolean ActiveDisconnect = false;
        private final String devicename;
        public String sensor_status;

        public ArrayList<String> filtering(ArrayList<String> dataCache, int thres, int interval) {
            ArrayList<String> filteredCache = new ArrayList<String> ();
            if (dataCache.size() == 0) {
                return filteredCache;
            }
            String[] f0 = dataCache.get(0).split(",");
            float last_ts = 0;
            int prev_x = Integer.valueOf(f0[1]);
            int prev_y = Integer.valueOf(f0[2]);
            int prev_z = Integer.valueOf(f0[3]);
            for (int i = 1; i < dataCache.size(); i++) {
                String s = dataCache.get(i);
                String[] fields = s.split(",");
                float ts = Float.valueOf(fields[0]);
                int x = Integer.valueOf(fields[1]);
                int y = Integer.valueOf(fields[2]);
                int z = Integer.valueOf(fields[3]);
                if (Math.abs(ts - last_ts) <= interval || Math.abs(x - prev_x) >= thres || Math.abs(y - prev_y) >= thres || Math.abs(z - prev_z) >= thres) {
                    filteredCache.add(s);
                    prev_x = x;
                    prev_y = y;
                    prev_z = z;
                    last_ts = ts;
                }
            }
            return filteredCache;
        }

        public BoardObject(MetaWearBoard mxBoard, final String MAC, float freq) {
            this.board = mxBoard;
            this.MAC_ADDRESS = MAC;
            this.dataCount = 0;
            this.dataCache = new ArrayList<>();
            this.startTimestamp = new long[1];
            this.sampleFreq = freq;
            this.uploadCount = (int) (8 * sampleFreq);
            this.sampleInterval = 1000 / sampleFreq;
            this.devicename = MAC_ADDRESS.replace(":", "");
            this.sensor_status = CONNECTING;
            final String SENSOR_DATA_LOG = "Data:Sensor:" + MAC_ADDRESS;

            this.board.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
                @Override
                public void connected() {
//                    changeText(MAC_ADDRESS, CONNECTED);
                    sensor_status = CONNECTED;
                    MultiChannelTemperature mcTempModule = null;
                    try {
                        startTimestamp[0] = System.currentTimeMillis();
                        accel_module = board.getModule(Accelerometer.class);
                        accel_module.setOutputDataRate(sampleFreq);
                        accel_module.routeData().fromAxes().stream(SENSOR_DATA_LOG).commit()
                                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                    @Override
                                    public void success(RouteManager result) {
                                        result.subscribe(SENSOR_DATA_LOG, new RouteManager.MessageHandler() {
                                            @Override
                                            public void process(Message message) {
                                                dataCount += 1;
                                                long timestamp = startTimestamp[0] + (long) (dataCount * sampleInterval);
                                                double timestamp_in_seconds = timestamp / 1000.0;
                                                CartesianFloat result = message.getData(CartesianFloat.class);
                                                float x = result.x();
                                                int x_int = (int) (x * 1000);
                                                float y = result.y();
                                                int y_int = (int) (y * 1000);
                                                float z = result.z();
                                                int z_int = (int) (z * 1000);
                                                dataCache.add(String.format("%.3f", timestamp_in_seconds) + "," + String.valueOf(x_int) +
                                                        "," + String.valueOf(y_int) + "," + String.valueOf(z_int));
                                                Log.i(SENSOR_DATA_LOG, String.valueOf(dataCount));
                                                if (dataCache.size() >= uploadCount) {
                                                    ArrayList<String> temp = new ArrayList<String>(dataCache);
                                                    dataCache.clear();
                                                    startTimestamp[0] = System.currentTimeMillis();
                                                    dataCount = 0;
                                                    ArrayList<String> filteredCache = filtering(temp, 32, 3);
                                                    if (filteredCache.size() > 0) {
                                                        String jsonstr = getJSON(devicename, filteredCache);
                                                        postDataAsync task = new postDataAsync();
                                                        task.execute(jsonstr);
                                                    }
//                                                    Log.i(LOG_ERR, getJSON(devicename, filtering(temp, 10, 3)));
                                                }
                                            }
                                        });
                                    }
                                });
                        mcTempModule = board.getModule(MultiChannelTemperature.class);
                        final List<MultiChannelTemperature.Source> tempSources= mcTempModule.getSources();
                        final MultiChannelTemperature finalMcTempModule = mcTempModule;
                        mcTempModule.routeData()
                                .fromSource(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR)).stream("thermistor_stream")
                                .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                    @Override
                                    public void success(RouteManager result) {
                                        result.subscribe("thermistor_stream", new RouteManager.MessageHandler() {
                                            @Override
                                            public void process(Message message) {
                                                long timestamp = System.currentTimeMillis();
                                                double ts_in_sec = timestamp / 1000.0;
                                                String jsonstr = getJSON(devicename, String.format("%.3f", ts_in_sec), String.format("%.3f", message.getData(Float.class)));
                                                postTempAsync task = new postTempAsync();
                                                task.execute(jsonstr);
                                            }
                                        });

                                        try {
                                            AsyncOperation<Timer.Controller> taskResult= board.getModule(Timer.class)
                                                    .scheduleTask(new Timer.Task() {
                                                        @Override
                                                        public void commands() {
                                                            finalMcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR));
                                                        }
                                                    }, 60000, false);
                                            taskResult.onComplete(new AsyncOperation.CompletionHandler<Timer.Controller>() {
                                                @Override
                                                public void success(Timer.Controller result) {
                                                    result.start();
                                                }
                                            });
                                        } catch (UnsupportedModuleException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                    } catch (UnsupportedModuleException e) {
                        Log.i(LOG_TAG, "Cannot find sensor:" + MAC_ADDRESS, e);
                    }
                    accel_module.enableAxisSampling();
                    startTimestamp[0] = System.currentTimeMillis();
                    dataCount = 0;
                    accel_module.start();
                }

                @Override
                public void disconnected() {
                    if (dataCache.size() != 0) {
                        ArrayList<String> temp = new ArrayList<String> (dataCache);
                        dataCache.clear();
                        startTimestamp[0] = System.currentTimeMillis();
                        dataCount = 0;
                        ArrayList<String> filteredCache = filtering(temp, 32, 3);
                        if (filteredCache.size() != 0) {
                            String jsonstr = getJSON(devicename, filteredCache);
                            postDataAsync task = new postDataAsync();
                            task.execute(jsonstr);
                        }
                    }
                    if (!ActiveDisconnect) {
//                        changeText(MAC_ADDRESS, DISCONNECTED);
                        sensor_status = DISCONNECTED;
                        board.connect();
                    }
                }

                @Override
                public void failure(int status, Throwable error) {
                    error.printStackTrace();
//                    changeText(MAC_ADDRESS, FAILURE);
                    sensor_status = FAILURE;
                    if (dataCache.size() != 0) {
                        ArrayList<String> temp = new ArrayList<String> (dataCache);
                        dataCache.clear();
                        startTimestamp[0] = System.currentTimeMillis();
                        dataCount = 0;
                        ArrayList<String> filteredCache = filtering(temp, 32, 3);
                        if (filteredCache.size() != 0) {
                            String jsonstr = getJSON(devicename, filteredCache);
                            postDataAsync task = new postDataAsync();
                            task.execute(jsonstr);
                        }
                    }
                    board.connect();
                }
            });
        }
    }
}
