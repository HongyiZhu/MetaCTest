package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 11/9/2015.
 */
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v7.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Bmi160Accelerometer;
import com.mbientlab.metawear.module.Logging;
import com.mbientlab.metawear.module.MultiChannelTemperature;
import com.mbientlab.metawear.module.Timer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.lang.Math;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.TimerTask;
import java.util.UUID;

public class ForegroundService extends Service implements ServiceConnection{
    private static final String LOG_TAG = "ForegroundService", LOG_ERR = "http_err";
    private static final String BROADCAST_TAG = Constants.NOTIFICATION_ID.BROADCAST_TAG;
    private MetaWearBleService.LocalBinder ServiceBinder;
    private final static ArrayList<String> SENSOR_MAC = new ArrayList<>();
    private final ArrayList<Board> boards = new ArrayList<>();
    private BluetoothAdapter btAdapter;
    private boolean isScanning= false;
    private HashSet<UUID> filterServiceUuids;
    private ArrayList<ScanFilter> api21ScanFilters;
    private final static UUID[] serviceUuids;

    public static boolean IS_SERVICE_RUNNING = false;

    static {
        serviceUuids= new UUID[] {
                MetaWearBoard.METAWEAR_SERVICE_UUID,
                MetaWearBoard.METABOOT_SERVICE_UUID
        };
    }


    private void initParams() {
//        5 Sensors for demo
        SENSOR_MAC.add("D2:02:B3:1C:D2:C3"); //C Body
        SENSOR_MAC.add("EB:0B:E2:6E:8C:52"); //C Front door
        SENSOR_MAC.add("F7:FC:FF:D2:F1:66"); //C Bath door
        SENSOR_MAC.add("EE:9F:61:85:DA:6C"); //C Fridge
        SENSOR_MAC.add("E8:BD:10:7D:58:B4"); //C Custom
    }

    public static String getSensors(int i) {
        String MAC;
        try {
            MAC = SENSOR_MAC.get(i);
        } catch (Exception e) {
            MAC = String.valueOf(i);
        }

        return MAC;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);
        String phoneID = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
        Log.i("phoneID", phoneID);
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
    //clear the boards
    public void onDestroy() {
        boards.get(0).accel_module.stop();
        boards.get(0).accel_module.disableAxisSampling();
        boards.get(0).ActiveDisconnect = true;
        boards.get(0).board.disconnect();

        for (int i = 1; i < SENSOR_MAC.size(); i++) {
            startBleScan();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            stopBleScan();

            ObjectBoard mwBoard = (ObjectBoard) boards.get(i);
            mwBoard.first = -1;
            mwBoard.board.connect();
            while (!mwBoard.destroyed) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
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

    private BluetoothAdapter.LeScanCallback deprecatedScanCallback= null;
    private ScanCallback api21ScallCallback= null;

    @TargetApi(22)
    public void startBleScan() {
        isScanning= true;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            filterServiceUuids = new HashSet<>();
        } else {
            api21ScanFilters= new ArrayList<>();
        }

        if (serviceUuids != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                filterServiceUuids.addAll(Arrays.asList(serviceUuids));
            } else {
                for (UUID uuid : serviceUuids) {
                    api21ScanFilters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(uuid)).build());
                }
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            deprecatedScanCallback= new BluetoothAdapter.LeScanCallback() {
                private void foundDevice(final BluetoothDevice btDevice, final int rssi) {

                }
                @Override
                public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
                    ///< Service UUID parsing code taking from stack overflow= http://stackoverflow.com/a/24539704

                    ByteBuffer buffer= ByteBuffer.wrap(scanRecord).order(ByteOrder.LITTLE_ENDIAN);
                    boolean stop= false;
                    while (!stop && buffer.remaining() > 2) {
                        byte length = buffer.get();
                        if (length == 0) break;

                        byte type = buffer.get();
                        switch (type) {
                            case 0x02: // Partial list of 16-bit UUIDs
                            case 0x03: // Complete list of 16-bit UUIDs
                                while (length >= 2) {
                                    UUID serviceUUID= UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.getShort()));
                                    stop= filterServiceUuids.isEmpty() || filterServiceUuids.contains(serviceUUID);
                                    if (stop) {
                                        foundDevice(bluetoothDevice, rssi);
                                    }

                                    length -= 2;
                                }
                                break;

                            case 0x06: // Partial list of 128-bit UUIDs
                            case 0x07: // Complete list of 128-bit UUIDs
                                while (!stop && length >= 16) {
                                    long lsb= buffer.getLong(), msb= buffer.getLong();
                                    stop= filterServiceUuids.isEmpty() || filterServiceUuids.contains(new UUID(msb, lsb));
                                    if (stop) {
                                        foundDevice(bluetoothDevice, rssi);
                                    }
                                    length -= 16;
                                }
                                break;

                            default:
                                buffer.position(buffer.position() + length - 1);
                                break;
                        }
                    }

                    if (!stop && filterServiceUuids.isEmpty()) {
                        foundDevice(bluetoothDevice, rssi);
                    }
                }
            };
            btAdapter.startLeScan(deprecatedScanCallback);
        } else {
            api21ScallCallback= new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {


                    super.onScanResult(callbackType, result);
                }
            };
            btAdapter.getBluetoothLeScanner().startScan(api21ScanFilters, new ScanSettings.Builder().build(), api21ScallCallback);
        }
    }

    public void stopBleScan() {
        if (isScanning) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                btAdapter.stopLeScan(deprecatedScanCallback);
            } else {
                btAdapter.getBluetoothLeScanner().stopScan(api21ScallCallback);
            }
            isScanning= false;
        }
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ServiceBinder = (MetaWearBleService.LocalBinder) service;

        btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

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

        startBleScan();

        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        stopBleScan();

        // Add body sensor
        BluetoothDevice btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(0));
        boards.add(new BodyBoard(ServiceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(0), 12.5f));
        Log.i(LOG_TAG, "Board added");

        // Add four object sensors
        for (int i = 1; i < SENSOR_MAC.size(); i++){
            btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(i));
            boards.add(new ObjectBoard(ServiceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(i), 1.5625f));
            Log.i(LOG_TAG, "Board added");
        }

        BodyBoard body = (BodyBoard) boards.get(0);
        body.sensor_status = body.CONNECTING;
        body.broadcastStatus();
        body.board.connect();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Change to Rotation
        java.util.Timer launch_queue = new java.util.Timer();
        launch_queue.schedule(new TimerTask() {
            @Override
            public void run() {
                ObjectBoard b = (ObjectBoard) boards.get(1);
                b.sensor_status = b.CONNECTING;
                b.broadcastStatus();
                b.connectionAttemptTS = System.currentTimeMillis();
                b.board.connect();
            }
        }, 0);
        launch_queue.schedule(new TimerTask() {
            @Override
            public void run() {
                ObjectBoard b = (ObjectBoard) boards.get(2);
                b.sensor_status = b.CONNECTING;
                b.broadcastStatus();
                b.connectionAttemptTS = System.currentTimeMillis();
                b.board.connect();
            }
        }, 60000);
        launch_queue.schedule(new TimerTask() {
            @Override
            public void run() {
                ObjectBoard b = (ObjectBoard) boards.get(3);
                b.sensor_status = b.CONNECTING;
                b.broadcastStatus();
                b.connectionAttemptTS = System.currentTimeMillis();
                b.board.connect();
            }
        }, 120000);
        launch_queue.schedule(new TimerTask() {
            @Override
            public void run() {
                ObjectBoard b = (ObjectBoard) boards.get(4);
                b.sensor_status = b.CONNECTING;
                b.broadcastStatus();
                b.connectionAttemptTS = System.currentTimeMillis();
                b.board.connect();
            }
        }, 180000);

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

    public String getJSON (String name, String ts, int battery) {
        JSONObject jsonstring = new JSONObject();
        try {
            jsonstring.put("s", name);
            jsonstring.put("t", Double.valueOf(ts));
            jsonstring.put("b", Float.valueOf(battery));
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
                    try {
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
                    } finally {
                        conn.disconnect();
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
                    try {
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
                    } finally {
                        conn.disconnect();
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

    public class postBatteryAsync extends AsyncTask<String, Boolean, String> {
        String urlbase = "http://io.silverlink247.com/battery";
        @Override
        protected String doInBackground(String... params) {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            if (netInfo != null && netInfo.isConnected()) {
                try {
                    URL url = new URL(urlbase);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    try {
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
                    } finally {
                        conn.disconnect();
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

    public class Board {
        public MetaWearBoard board;
        public Bmi160Accelerometer accel_module;
        public String sensor_status;
        public boolean ActiveDisconnect = false;
        public String MAC_ADDRESS;
        public String temperature = "-99999";
        public String battery;
        public final String CONNECTED = "Connected.\nStreaming Data",
                DISCONNECTED_BODY = "Lost connection.\nReconnecting",
                DISCONNECTED_OBJ = "Download finished.",
                FAILURE = "Connection error.\nReconnecting",
                CONFIGURED = "Board configured.",
                CONNECTING = "Connecting",
                INITIATED = "Board reset",
                LOG_TAG = "Board_Log";
        public Board() {}
        public ArrayList<String> filtering(ArrayList<String> dataCache, int thres, int interval) {
            ArrayList<String> filteredCache = new ArrayList<> ();
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

        public void broadcastStatus() {
            Intent intent = new Intent(BROADCAST_TAG);
            intent.putExtra("name", this.MAC_ADDRESS);
            intent.putExtra("status", this.sensor_status);
            intent.putExtra("temperature", this.temperature);
            intent.putExtra("timestamp", System.currentTimeMillis());
            Log.i("Intent", intent.getStringExtra("name"));
            sendBroadcast(intent);
        }

//        public void broadcastTemperature(String temp, long time) {
//            Intent intent = new Intent(BROADCAST_TAG);
//            intent.putExtra("name", this.MAC_ADDRESS);
//            intent.putExtra("status", this.sensor_status);
//            intent.putExtra("timestamp", time);
//            intent.putExtra("temperature", temp);
//            sendBroadcast(intent);
//        }
    }

    public class ObjectBoard extends Board{
        private final float sampleFreq;
        private String devicename;
        private final String SENSOR_DATA_LOG;
        private int first = 0;
        private byte[] state = null;
        private List<CartesianFloat> datalist = null;
        private int routeID = 0;
        boolean configured = false;
        private long connectionAttemptTS;
        private int connectionFailureCount;
        java.util.Timer timer;
        private long infoTS = 0;
        boolean destroyed = false;
        boolean started = false;

        private final RouteManager.MessageHandler loggingMessageHandler = new RouteManager.MessageHandler() {
            @Override
            public void process(Message message) {
                datalist.add(message.getData(CartesianFloat.class));
            }
        };

        private final AsyncOperation.CompletionHandler<RouteManager> accelHandler = new AsyncOperation.CompletionHandler<RouteManager>() {
            @Override
            public void success(RouteManager result) {
                state = board.serializeState();
                routeID = result.id();
                Log.i("info", String.format("RouteID: %d", routeID));
                result.setLogMessageHandler(SENSOR_DATA_LOG, loggingMessageHandler);
            }
        };

        //generate timestamps
        private ArrayList<String> getFilteredDataCache (ArrayList<CartesianFloat> data_list, long last_timestamp) {
            ArrayList<String> temp = new ArrayList<>();
            ArrayList<String> dataCache = new ArrayList<>();
            int count = data_list.size();
            double record_ts = last_timestamp / 1000.0 - (count - 1) * 0.64;
            for (CartesianFloat result: data_list) {
                float x = result.x();
                int x_int = (int) (x * 1000);
                float y = result.y();
                int y_int = (int) (y * 1000);
                float z = result.z();
                int z_int = (int) (z * 1000);
                temp.add(String.format("%.3f", record_ts) + "," + String.valueOf(x_int) +
                        "," + String.valueOf(y_int) + "," + String.valueOf(z_int));
                record_ts += 0.64;
            }
            dataCache = filtering(temp, 32, 3);

            return dataCache;
        }

        public void destroy() {
            try {
                Logging logger = board.getModule(Logging.class);
                logger.stopLogging();
                logger.clearEntries();
                this.accel_module = board.getModule(Bmi160Accelerometer.class);
                this.accel_module.disableAxisSampling();
                this.accel_module.stop();
                this.timer.cancel();
                this.board.removeRoutes();
                board.disconnect();
            } catch (UnsupportedModuleException e) {
                e.printStackTrace();
            }
        }

        public ObjectBoard(MetaWearBoard mxBoard, final String MAC, float freq) {
            this.board = mxBoard;
            this.MAC_ADDRESS = MAC;
            this.sampleFreq = freq;
            this.devicename = MAC_ADDRESS.replace(":", "");
            this.SENSOR_DATA_LOG = "Data:Sensor:" + MAC_ADDRESS;
            this.timer = new java.util.Timer();

            this.board.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
                @Override
                public void connected() {
                    connectionFailureCount = 0;
                    if (first == 0) {
                        first = 1;
                        board.removeRoutes();
                        try {
                            Logging logger = board.getModule(Logging.class);
                            logger.stopLogging();
                            accel_module = board.getModule(Bmi160Accelerometer.class);
                            accel_module.stop();
                            accel_module.disableAxisSampling();
                            logger.clearEntries();
                        } catch (UnsupportedModuleException e) {
                            e.printStackTrace();
                        }
                        sensor_status = INITIATED;
                        broadcastStatus();
                        board.disconnect();
                    } else if (first == 1) {
                        Log.i("sensor", "Connected");
                        first = 2;
                        try {
                            Logging logger = board.getModule(Logging.class);
                            logger.startLogging(true);
                            accel_module = board.getModule(Bmi160Accelerometer.class);
                            accel_module.configureAxisSampling()
                                    .setOutputDataRate(Bmi160Accelerometer.OutputDataRate.ODR_1_5625_HZ)
                                    .enableUndersampling((byte) 4)
                                    .commit();
                            accel_module.routeData().fromAxes().log(SENSOR_DATA_LOG).commit().onComplete(accelHandler);
                            accel_module.enableAxisSampling();
                            accel_module.startLowPower();
                            // Get Temperature and Battery
                            MultiChannelTemperature mcTempModule;
                            try {
                                board.readBatteryLevel().onComplete(new AsyncOperation.CompletionHandler<Byte>() {
                                    @Override
                                    public void success(Byte result) {
                                        //Send Battery Info
                                        battery = result.toString();
                                        Log.i("battery", battery);
                                        String jsonstr = getJSON(devicename,String.format("%.3f", System.currentTimeMillis()/1000.0), Integer.valueOf(battery));
                                        postBatteryAsync task = new postBatteryAsync();
                                        task.execute(jsonstr);
                                    }
                                });
                                mcTempModule = board.getModule(MultiChannelTemperature.class);
                                final List<MultiChannelTemperature.Source> tempSources= mcTempModule.getSources();
                                mcTempModule.routeData()
                                        .fromSource(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR)).stream("temp_"+devicename)
                                        .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                    @Override
                                    public void success(RouteManager result) {
                                        result.subscribe("temp_"+devicename, new RouteManager.MessageHandler() {
                                            @Override
                                            public void process(Message message) {
                                                long timestamp = System.currentTimeMillis();
                                                if (timestamp - infoTS >= 210000) {
                                                    infoTS = timestamp;
                                                    double ts_in_sec = timestamp / 1000.0;
                                                    String jsonstr = getJSON(devicename, String.format("%.3f", ts_in_sec), String.format("%.3f", message.getData(Float.class)));
                                                    postTempAsync task = new postTempAsync();
                                                    task.execute(jsonstr);
                                                    temperature =  String.format("%.3f", message.getData(Float.class));
                                                    Log.i(devicename, jsonstr);
                                                    broadcastStatus();
                                                }
                                            }
                                        });
                                    }
                                });
                                mcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR));
                            } catch (UnsupportedModuleException e) {
                                e.printStackTrace();
                            }

                            configured = true;
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    try {
                                        board.getModule(Logging.class).clearEntries();
                                    } catch (UnsupportedModuleException e) {
                                        e.printStackTrace();
                                    }
                                    board.disconnect();
                                    sensor_status = CONFIGURED;
                                    broadcastStatus();
                                }
                            }, 20000);
//                        board.disconnect();
                        } catch (UnsupportedModuleException e) {
                            e.printStackTrace();
                        }
                    } else if (first == 2) {
                        try {
//                            board.deserializeState(state);
                            final Logging logger = board.getModule(Logging.class);
                            datalist = new ArrayList<>();
//                            RouteManager route = board.getRouteManager(routeID);
//                            route.setLogMessageHandler(SENSOR_DATA_LOG, loggingMessageHandler);
                            final long dl_TS = System.currentTimeMillis();

                            logger.downloadLog(0.1f, new Logging.DownloadHandler() {
                                @Override
                                public void onProgressUpdate(int nEntriesLeft, int totalEntries) {
                                    Log.i("data", String.format("Progress: %d/%d", totalEntries - nEntriesLeft, totalEntries));
                                    if (nEntriesLeft == 0) {
                                        Log.i("data", "Download Completed");
                                        Log.i("data", "Generated " + datalist.size() + " data points");
                                        //process listed data
                                        // send to server
                                        ArrayList<String> data = getFilteredDataCache((ArrayList<CartesianFloat>)datalist, dl_TS);
                                        if (data.size() > 0) {
                                            String json = getJSON(devicename, data);
                                            Log.i(devicename, json);
                                            postDataAsync task = new postDataAsync();
                                            task.execute(json);
                                        }
                                        datalist.clear();

                                        // retrieve temperature and battery info
                                        if (System.currentTimeMillis() - infoTS >= 210000) {
                                            final MultiChannelTemperature mcTempModule;
                                            try {
                                                board.readBatteryLevel().onComplete(new AsyncOperation.CompletionHandler<Byte>() {
                                                    @Override
                                                    public void success(Byte result) {
                                                        // Send Battery Info
                                                        battery = result.toString();
                                                        Log.i("battery_"+devicename, battery);
                                                        String jsonstr = getJSON(devicename,String.format("%.3f", System.currentTimeMillis()/1000.0), Integer.valueOf(battery));
                                                        postBatteryAsync task = new postBatteryAsync();
                                                        task.execute(jsonstr);
                                                    }
                                                });
                                                mcTempModule = board.getModule(MultiChannelTemperature.class);
                                                final List<MultiChannelTemperature.Source> tempSources = mcTempModule.getSources();
//                                                mcTempModule.routeData()
//                                                        .fromSource(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR)).stream("temp_"+devicename)
//                                                        .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
//                                                    @Override
//                                                    public void success(RouteManager result) {
//                                                        result.subscribe("temp_"+devicename, new RouteManager.MessageHandler() {
//                                                            @Override
//                                                            public void process(Message message) {
//                                                                long timestamp = System.currentTimeMillis();
//                                                                if (timestamp - infoTS >= 210000) {
//                                                                    infoTS = timestamp;
//                                                                    double ts_in_sec = timestamp / 1000.0;
//                                                                    String jsonstr = getJSON(devicename, String.format("%.3f", ts_in_sec), String.format("%.3f", message.getData(Float.class)));
////                                                                    postTempAsync task = new postTempAsync();
////                                                                    task.execute(jsonstr);
//                                                                    Log.i(devicename, jsonstr);
//                                                                    broadcastTemperature(String.format("%.3f", message.getData(Float.class)), timestamp);
//                                                                }
//                                                            }
//                                                        });
//                                                    }
//                                                });
                                                mcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR));

                                            } catch (UnsupportedModuleException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        timer.schedule(new TimerTask() {
                                            @Override
                                            public void run() {
                                                board.disconnect();
                                                sensor_status = DISCONNECTED_OBJ;
                                                broadcastStatus();
                                            }
                                        }, 5000);
                                    }
                                }

                                @Override
                                public void receivedUnknownLogEntry(byte logId, Calendar timestamp, byte[] data) {
                                }

                                @Override
                                public void receivedUnhandledLogEntry(Message logMessage) {
                                    Log.i("dataUnhandled", logMessage.toString());
                                }
                            });
                        } catch (UnsupportedModuleException e) {
                            e.printStackTrace();
                        }
                    } else if (first == -1) {
                        destroy();
                        destroyed = true;
                    }
                }

                @Override
                public void disconnected() {
                    if (first != -1) {
                        long interval = 235000 - (System.currentTimeMillis() - connectionAttemptTS);
                        TimerTask reconnect = new TimerTask() {
                            @Override
                            synchronized public void run() {
                                startBleScan();
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                stopBleScan();
                                connectionAttemptTS = System.currentTimeMillis();
                                board.connect();
                            }
                        };
                        timer.schedule(reconnect, interval);
                    }
                }

                @Override
                public void failure(int status, Throwable error) {
                    if (first != -1) {
                        error.printStackTrace();
                        sensor_status = FAILURE;
                        broadcastStatus();
                        connectionFailureCount += 1;
                        TimerTask reconnect = new TimerTask() {
                            @Override
                            synchronized public void run() {
                                startBleScan();
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                stopBleScan();
//                                connectionAttemptTS = System.currentTimeMillis();
                                board.connect();
                            }
                        };
                        if (connectionFailureCount < 2) {
                            timer.schedule(reconnect, 0);
                        } else {
                            connectionFailureCount = 0;
                            long interval = 235000 - (System.currentTimeMillis() - connectionAttemptTS);
                            timer.schedule(reconnect, interval);
                        }
                    } else {
                        error.printStackTrace();
                        sensor_status = FAILURE;
                        broadcastStatus();
                        TimerTask reconnect = new TimerTask() {
                            @Override
                            synchronized public void run() {
                                startBleScan();
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                stopBleScan();
                                connectionAttemptTS = System.currentTimeMillis();
                                board.connect();
                            }
                        };
                        timer.schedule(reconnect, 0);
                    }
                }
            });
        }
    }

    public class BodyBoard extends Board{
        public long[] startTimestamp;
        public ArrayList<String> dataCache;
        public int dataCount;
        private float sampleFreq;
        private int uploadCount;
        private float sampleInterval;
        private final String devicename;
        private long temperature_timestamp;

        public BodyBoard(MetaWearBoard mxBoard, final String MAC, float freq) {
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
            this.temperature_timestamp = 0;
            final String SENSOR_DATA_LOG = "Data:Sensor:" + MAC_ADDRESS;

            this.board.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
                @Override
                public void connected() {
//                    changeText(MAC_ADDRESS, CONNECTED);
                    sensor_status = CONNECTED;
                    broadcastStatus();
                    final MultiChannelTemperature mcTempModule;
                    try {
                        startTimestamp[0] = System.currentTimeMillis();
                        accel_module = board.getModule(Bmi160Accelerometer.class);
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
//                                                Log.i(SENSOR_DATA_LOG, String.valueOf(dataCount));
                                                if (dataCache.size() >= uploadCount) {
                                                    ArrayList<String> temp = new ArrayList<>(dataCache);
                                                    dataCache.clear();
                                                    startTimestamp[0] = System.currentTimeMillis();
                                                    dataCount = 0;
                                                    ArrayList<String> filteredCache = filtering(temp, 32, 3);
                                                    if (filteredCache.size() > 0) {
                                                        String jsonstr = getJSON(devicename, filteredCache);
                                                        //switch to send
                                                        postDataAsync task = new postDataAsync();
                                                        task.execute(jsonstr);
                                                        Log.i(devicename, jsonstr);
                                                    }
//                                                    Log.i(LOG_ERR, getJSON(devicename, filtering(temp, 10, 3)));
                                                }
                                            }
                                        });
                                    }
                                });
                        mcTempModule = board.getModule(MultiChannelTemperature.class);
                        final List<MultiChannelTemperature.Source> tempSources= mcTempModule.getSources();
//                        final MultiChannelTemperature finalMcTempModule = mcTempModule;
                        mcTempModule.routeData()
                                .fromSource(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR)).stream("thermistor_stream")
                                .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                    @Override
                                    public void success(RouteManager result) {
                                        result.subscribe("thermistor_stream", new RouteManager.MessageHandler() {
                                            @Override
                                            public void process(Message message) {
                                                long timestamp = System.currentTimeMillis();
                                                if (timestamp - temperature_timestamp >= 220000) {
                                                    temperature_timestamp = timestamp;
                                                    double ts_in_sec = timestamp / 1000.0;
                                                    String jsonstr = getJSON(devicename, String.format("%.3f", ts_in_sec), String.format("%.3f", message.getData(Float.class)));
                                                    //send to server
                                                    postTempAsync task = new postTempAsync();
                                                    task.execute(jsonstr);
                                                    temperature = String.format("%.3f", message.getData(Float.class));
                                                    Log.i(devicename, jsonstr);
                                                    // get body sensor battery info
                                                    board.readBatteryLevel().onComplete(new AsyncOperation.CompletionHandler<Byte>() {
                                                        @Override
                                                        public void success(Byte result) {
                                                            // Send Battery Info
                                                            battery = result.toString();
                                                            Log.i("battery_Body", result.toString());
                                                            String jsonstr = getJSON(devicename,String.format("%.3f", System.currentTimeMillis()/1000.0), Integer.valueOf(result.toString()));
                                                            postBatteryAsync task = new postBatteryAsync();
                                                            task.execute(jsonstr);
                                                        }
                                                    });
                                                    broadcastStatus();
                                                }
                                            }
                                        });

                                        java.util.Timer timer = new java.util.Timer();
                                        timer.schedule(new TimerTask() {
                                            @Override
                                            public void run() {
                                                mcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR));
                                            }
                                        },0,240000);
//                                        try {
//                                            AsyncOperation<Timer.Controller> taskResult= board.getModule(Timer.class)
//                                                    .scheduleTask(new Timer.Task() {
//                                                        @Override
//                                                        public void commands() {
//                                                            finalMcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR));
//                                                        }
//                                                    }, 240000, false);
//                                            taskResult.onComplete(new AsyncOperation.CompletionHandler<Timer.Controller>() {
//                                                @Override
//                                                public void success(Timer.Controller result) {
//                                                    result.start();
//                                                }
//                                            });
//                                        } catch (UnsupportedModuleException e) {
//                                            e.printStackTrace();
//                                        }
                                    }
                                });
                    } catch (UnsupportedModuleException e) {
                        Log.i(LOG_TAG, "Cannot find sensor:" + MAC_ADDRESS, e);
                    }
                    accel_module.enableAxisSampling();
                    startTimestamp[0] = System.currentTimeMillis();
                    dataCount = 0;
                    accel_module.startLowPower();
                }

                @Override
                public void disconnected() {
                    if (dataCache.size() != 0) {
                        ArrayList<String> temp = new ArrayList<> (dataCache);
                        dataCache.clear();
                        startTimestamp[0] = System.currentTimeMillis();
                        dataCount = 0;
                        ArrayList<String> filteredCache = filtering(temp, 32, 3);
                        if (filteredCache.size() != 0) {
                            String jsonstr = getJSON(devicename, filteredCache);
//                            postDataAsync task = new postDataAsync();
//                            task.execute(jsonstr);
                            Log.i(devicename, jsonstr);
                        }
                    }
                    if (!ActiveDisconnect) {
                        sensor_status = DISCONNECTED_BODY;
                        broadcastStatus();
                        board.connect();
                    }
                }

                @Override
                public void failure(int status, Throwable error) {
                    error.printStackTrace();
                    sensor_status = FAILURE;
                    broadcastStatus();
                    if (dataCache.size() != 0) {
                        ArrayList<String> temp = new ArrayList<> (dataCache);
                        dataCache.clear();
                        startTimestamp[0] = System.currentTimeMillis();
                        dataCount = 0;
                        ArrayList<String> filteredCache = filtering(temp, 32, 3);
                        if (filteredCache.size() != 0) {
                            String jsonstr = getJSON(devicename, filteredCache);
//                            postDataAsync task = new postDataAsync();
//                            task.execute(jsonstr);
                            Log.i(devicename, jsonstr);
                        }
                    }
                    board.connect();
                }
            });
        }
    }
}
