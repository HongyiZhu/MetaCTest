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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.lang.Math;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForegroundService extends Service implements ServiceConnection{
    private Set<String> sendJobSet;
    private Timer servicetimer;
    private Queue<String> resendDataQueue = new ConcurrentLinkedQueue<>();
    private Queue<String> resendBatteryQueue = new ConcurrentLinkedQueue<>();
    private Queue<String> resendTempQueue = new ConcurrentLinkedQueue<>();
    private File log_file;
    private static final String LOG_TAG = "ForegroundService", LOG_ERR = "http_err";
    private static final String _info = "INF", _success = "SUC", _error = "ERR";
    private static final String BROADCAST_TAG = Constants.NOTIFICATION_ID.BROADCAST_TAG;
    private final static ArrayList<String> SENSOR_MAC = new ArrayList<>();
    private final ArrayList<Board> boards = new ArrayList<>();
    private BluetoothAdapter btAdapter;
    private HashSet<UUID> filterServiceUuids;
    private ArrayList<ScanFilter> api21ScanFilters;
    private final static UUID[] serviceUuids;
    private static ExecutorService dataPool;
    private boolean keepAlive = false;
    private Timer restartTM;
    private Set<String> nearByDevices;
    private String send_url_base;

    public static boolean IS_SERVICE_RUNNING = false;

    static {
        serviceUuids= new UUID[] {
                MetaWearBoard.METAWEAR_SERVICE_UUID,
                MetaWearBoard.METABOOT_SERVICE_UUID
        };
        dataPool = Executors.newCachedThreadPool();
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
    }

    private String getFormattedTime() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    private void writeSensorLog(String s, String... flags) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(log_file, true)));
            bw.write(getFormattedTime() + "\t");
            for (String flag : flags) {
                bw.write(flag + "\t");
            }
            bw.write(s);
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sendJobSet = new HashSet<>();
        String address = (getApplicationContext().getExternalFilesDirs("")[0].getAbsolutePath()).contains("external_SD") ? getApplicationContext().getExternalFilesDirs("")[1].getAbsolutePath() : getApplicationContext().getExternalFilesDirs("")[0].getAbsolutePath();
        File folder = new File(address);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        if (intent != null) {
            if (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION)) {
                if (SENSOR_MAC.size() == 0) {
                    SENSOR_MAC.add(intent.getStringExtra("A"));
                    SENSOR_MAC.add(intent.getStringExtra("B"));
                    SENSOR_MAC.add(intent.getStringExtra("C"));
                    SENSOR_MAC.add(intent.getStringExtra("D"));
                    SENSOR_MAC.add(intent.getStringExtra("E"));
                }
                send_url_base = intent.getStringExtra("send_url");
                Log.i(LOG_TAG, "Received Start Foreground Intent");
//            writeSensorLog("Received Start Foreground Intent", _info);
                showNotification();
//            Toast.makeText(this, "Service Started!", Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(
                    Constants.ACTION.STOPFOREGROUND_ACTION)) {
                Log.i(LOG_TAG, "Received Stop Foreground Intent");
                writeSensorLog("Received Stop Foreground Intent", _info);
                stopForeground(true);
                stopSelf();
            }
        } else {
            File config_file = new File(address, "config.ini");
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(config_file)));
                String res = br.readLine();
                JSONObject js = new JSONObject(res);
                send_url_base = js.getString("send_url");
                JSONArray jsonarr = js.getJSONArray("sensors");
                Map<String, String> hm = new HashMap<>();
                for (int i = 0;i<jsonarr.length();i++) {
                    JSONObject jsobj = jsonarr.getJSONObject(i);
                    String id = ((String) jsobj.get("sensor_id")).replaceAll("..(?!$)", "$0:");
                    String sn = (String) jsobj.get("sensor_sn");
                    String lb = sn.substring(sn.length() - 1);
                    Log.i("sensor", "Label: " + lb + ".\tMAC: " + id);
                    hm.put(lb, id);
                }
                if (SENSOR_MAC.size() == 0) {
                    SENSOR_MAC.add(hm.get("A"));
                    SENSOR_MAC.add(hm.get("B"));
                    SENSOR_MAC.add(hm.get("C"));
                    SENSOR_MAC.add(hm.get("D"));
                    SENSOR_MAC.add(hm.get("E"));
                }
                IS_SERVICE_RUNNING = true;
                showNotification();
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }

        servicetimer = new Timer();

        // Change log files every hour
        servicetimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Calendar day_time = new GregorianCalendar();
                int year = day_time.get(GregorianCalendar.YEAR);
                int month = day_time.get(GregorianCalendar.MONTH) + 1;
                int day = day_time.get(GregorianCalendar.DAY_OF_MONTH);
                int hour = day_time.get(GregorianCalendar.HOUR_OF_DAY);
                int minute = day_time.get(GregorianCalendar.MINUTE);
                int second = day_time.get(GregorianCalendar.SECOND);
                String log_filename = year + "-" + month + "-" + day + "_" + hour + "_" + minute + "_" + second + ".log";
                String address = (getApplicationContext().getExternalFilesDirs("")[0].getAbsolutePath() + "/logs").contains("external_SD") ? getApplicationContext().getExternalFilesDirs("")[1].getAbsolutePath() + "/logs" : getApplicationContext().getExternalFilesDirs("")[0].getAbsolutePath() + "/logs";
                Log.i("paths", address);
                File folder = new File(address);
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                log_file = new File(folder, log_filename);
                log_file.setReadable(true);
                log_file.setWritable(true);
            }
        }, 0, 3600000);

        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);

        File saved_task = new File(address, "saved_task.log");
        if (saved_task.exists()) {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(saved_task)));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("\"c\"")) {
                        resendTempQueue.add(line);
                    } else if (line.contains("\"b\"")) {
                        resendBatteryQueue.add(line);
                    } else {
                        resendDataQueue.add(line);
                    }
                }
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            saved_task.delete();
        }
//        String phoneID = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
//        Log.i("phoneID", phoneID);
//        initParams();
        Log.i(LOG_TAG, "successfully on create");

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
//        writeSensorLog("successfully build a notification", _info);
        startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                notification);
        Log.i(LOG_TAG, "started foreground");
//        writeSensorLog("started foreground", _info);

    }

    @Override
    //clear the boards
    public void onDestroy() {
        Log.i(LOG_TAG, "In onDestroy");
        writeSensorLog("In onDestroy", _info);
        boards.get(0).accel_module.stop();
        boards.get(0).accel_module.disableAxisSampling();
        boards.get(0).ActiveDisconnect = true;
        boards.get(0).board.disconnect();
        Log.i(LOG_TAG, "Body sensor destroyed");
        writeSensorLog("Body sensor destroyed", _info);

        super.onDestroy();
        getApplicationContext().unbindService(this);
        Log.i(LOG_TAG, "All destruction finished");
        writeSensorLog("All destruction finished", _info);
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
    public Set<String> scanBle(long interval) {
        nearByDevices = new HashSet<>();
        long scanTS = System.currentTimeMillis();

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
                    nearByDevices.add(btDevice.getAddress());
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

        while ((System.currentTimeMillis()-scanTS) <= interval) {

        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            btAdapter.stopLeScan(deprecatedScanCallback);
        } else {
            btAdapter.getBluetoothLeScanner().stopScan(api21ScallCallback);
        }

        return nearByDevices;
    }

    private boolean need_reboot() {
        boolean flag = false;
        for (int i = 1; i < SENSOR_MAC.size();i++){
            flag = boards.get(i).needs_to_reboot || flag;
        }

        return flag;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Timer to resend the data in the Queue
        servicetimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!resendDataQueue.isEmpty()) {
                    String data = resendDataQueue.poll();
                    postDataAsync task = new postDataAsync();
                    task.executeOnExecutor(dataPool, data);
                }
                if (!resendBatteryQueue.isEmpty()) {
                    String data = resendBatteryQueue.poll();
                    postBatteryAsync task = new postBatteryAsync();
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, data);
                }
                if (!resendTempQueue.isEmpty()) {
                    String data = resendTempQueue.poll();
                    postTempAsync task = new postTempAsync();
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, data);
                }
            }
        }, 0, 500);

        MetaWearBleService.LocalBinder serviceBinder = (MetaWearBleService.LocalBinder) service;

        btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

//        BluetoothAdapter btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
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

        Set<String> nearMW = scanBle(5000);
        Log.i("BLE", String.valueOf(nearMW.size()));

        // Add body sensor
        BluetoothDevice btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(0));
        boards.add(new BodyBoard(serviceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(0), 12.5f));
        Log.i(LOG_TAG, "Body Board added");
        writeSensorLog("Body Board added", _info);

        // Add four object sensors
        for (int i = 1; i < SENSOR_MAC.size(); i++){
            btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(i));
            boards.add(new ObjectBoard(serviceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(i), 1.5625f));
            Log.i(LOG_TAG, "Object Board added");
            writeSensorLog("Object Board added", _info);
        }

        BodyBoard body = (BodyBoard) boards.get(0);
        body.sensor_status = body.CONNECTING;
        body.broadcastStatus();
        writeSensorLog("Try to connect", _info, body.devicename);
        body.board.connect();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Rotation connection
        java.util.Timer launch_queue = new java.util.Timer();
        launch_queue.schedule(new TimerTask() {
            @Override
            public void run() {
                ObjectBoard b = (ObjectBoard) boards.get(1);
                b.sensor_status = b.CONNECTING;
                b.broadcastStatus();
                b.connectionAttemptTS = System.currentTimeMillis();
                writeSensorLog("Try to connect", _info, b.devicename);
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
                writeSensorLog("Try to connect", _info, b.devicename);
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
                writeSensorLog("Try to connect", _info, b.devicename);
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
                writeSensorLog("Try to connect", _info, b.devicename);
                b.board.connect();
            }
        }, 180000);

        restartTM = new Timer();

        //Schedule Scan for Restart
        TimerTask scanForRestart = new TimerTask() {
            @Override
            public void run() {
                btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                if (!btAdapter.isEnabled()) {
                    btAdapter.enable();
                }

                Set<String> nearMW = scanBle(5000);
                Log.i("BLE", String.valueOf(nearMW.size()));

                if ((nearMW.isEmpty() && btAdapter.isEnabled()) || (need_reboot())) {
                    // Save unsent tasks
                    String address = (getApplicationContext().getExternalFilesDirs("")[0].getAbsolutePath()).contains("external_SD") ? getApplicationContext().getExternalFilesDirs("")[1].getAbsolutePath() : getApplicationContext().getExternalFilesDirs("")[0].getAbsolutePath();
                    File folder = new File(address);
                    if (!folder.exists()) {
                        folder.mkdirs();
                    }
                    File save_task = new File(address, "saved_task.log");
                    try {
                        save_task.createNewFile();
                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(save_task)));
                        for (String s : sendJobSet) {
                            bw.write(s);
                            bw.newLine();
                            bw.flush();
                        }
                        bw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        Process proc = Runtime.getRuntime()
                                .exec(new String[]{ "su", "-c", "reboot" });
                        proc.waitFor();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        };

        restartTM.schedule(scanForRestart, 600000, 240000);

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    public ArrayList<String> getJSONList (String name, ArrayList<String> data) {
        ArrayList<String> dataLists = new ArrayList<>();
        int start = 0;
        int trunk_size = 40;
        while (start + trunk_size <= data.size()) {
            JSONObject jsonstring = new JSONObject();
            JSONArray logs = new JSONArray();
            try {
                jsonstring.put("s", name);
                for (String s : data.subList(start, start + trunk_size)) {
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
            dataLists.add(jsonstring.toString());
            start += trunk_size;
        }
        if (start < data.size()) {
            JSONObject jsonstring = new JSONObject();
            JSONArray logs = new JSONArray();
            try {
                jsonstring.put("s", name);
                for (String s : data.subList(start, data.size())) {
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
            dataLists.add(jsonstring.toString());
        }
        return dataLists;
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
        String urlbase = send_url_base + "/logs";
        @Override
        protected String doInBackground(String... params) {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            HttpURLConnection conn = null;
            sendJobSet.add(params[0]);
            if (netInfo != null && netInfo.isConnected()) {
                try {
                    URL url = new URL(urlbase);
                    conn = (HttpURLConnection) url.openConnection();

                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("content-type", "application/json");
                    if (!keepAlive) {
                        conn.setRequestProperty("connection", "close");
                    }
                    conn.setRequestProperty("Accept-Encoding", "");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setUseCaches(false);
//                    conn.setChunkedStreamingMode(1024);
                    conn.setInstanceFollowRedirects(true);

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(params[0]);
                    writer.flush();
                    writer.close();
                    os.flush();
                    os.close();
                    writeSensorLog(params[0].length()>80 ? params[0].substring(0, 80) : params[0], _info, "Send Attempt");

                    int response = conn.getResponseCode();
                    if (response == HttpURLConnection.HTTP_OK) {
                        Log.i(LOG_TAG, "Post succeed: " + params[0]);
                        InputStream is = conn.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        String line = br.readLine();
                        while(line!=null){
                            Log.i("Http_response", line);
                            writeSensorLog("HTTP Response: " + line.trim(), _success, "Data");
                            sendJobSet.remove(params[0]);
                            line = br.readLine();
                        }
                    } else {
                        Log.e(LOG_ERR, "Post error code: " + response + " " + params[0]);
                        resendDataQueue.offer(params[0]);
                        writeSensorLog("Post err code: " + response + " " + params[0].substring(0, 80), _error);
                    }

                } catch (MalformedURLException e) {
                    Log.e(LOG_ERR, "Illegal URL");
                } catch (IOException e) {
                    Log.e(LOG_ERR, "Connection error " + ((e.getMessage() == null) ? e : e.getMessage()) + " " + params[0]);
                    resendDataQueue.offer(params[0]);
                    writeSensorLog("Connection error: " + e.getMessage() + " " + params[0].substring(0, 80), _error);
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            } else {
                Log.e(LOG_ERR, "No active connection, add request back to queue");
                resendDataQueue.offer(params[0]);
                writeSensorLog("No active connection, add to wait queue: " + params[0].substring(0, 80), _error);
            }
            return null;
        }
    }

    public class postTempAsync extends AsyncTask<String, Boolean, String> {
        String urlbase = send_url_base + "/temperature";
        @Override
        protected String doInBackground(String... params) {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            sendJobSet.add(params[0]);
            if (netInfo != null && netInfo.isConnected()) {
                try {
                    URL url = new URL(urlbase);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    try {
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("content-type", "application/json");
                        if (!keepAlive) {
                            conn.setRequestProperty("connection", "close");
                        }
                        conn.setDoOutput(true);
                        conn.setDoInput(true);
                        conn.setUseCaches(false);
                        conn.setRequestProperty("Accept-Encoding", "");
//                        conn.setChunkedStreamingMode(1024);
                        conn.setInstanceFollowRedirects(true);

                        OutputStream os = conn.getOutputStream();
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                        writer.write(params[0]);
                        writer.flush();
                        writer.close();
                        os.flush();
                        os.close();
                        writeSensorLog(params[0], _info, "Send Attempt");

                        int response = conn.getResponseCode();
                        if (response == HttpURLConnection.HTTP_OK) {
                            Log.i(LOG_TAG, "Post succeed: " + params[0]);
                            InputStream is = conn.getInputStream();
                            BufferedReader br = new BufferedReader(new InputStreamReader(is));
                            String line = br.readLine();
                            while(line!=null){
                                Log.i("Http_response", line);
                                writeSensorLog("HTTP Response: " + line.trim(), _success, "Data");
                                line = br.readLine();
                            }
                            sendJobSet.remove(params[0]);
                        } else {
                            Log.e(LOG_ERR, "Post error code: " + response + " " + params[0]);
                            resendTempQueue.offer(params[0]);
                            writeSensorLog("Post err code: " + response + " " + params[0], _error);
                        }
                    } finally {
                        conn.disconnect();
                    }
                } catch (MalformedURLException e) {
                    Log.e(LOG_ERR, "Illegal URL");
                } catch (IOException e) {
                    Log.e(LOG_ERR, "Connection error " + e.getMessage() + " " + params[0]);
                    resendTempQueue.offer(params[0]);
                    writeSensorLog("Connection error: " + e.getMessage() + " " + params[0], _error);
                }
            } else {
                Log.e(LOG_ERR, "No active connection");
                resendTempQueue.offer(params[0]);
                writeSensorLog("No active connection, add to wait queue: " + params[0], _error);
            }
            return null;
        }
    }

    public class postBatteryAsync extends AsyncTask<String, Boolean, String> {
        String urlbase = send_url_base + "/battery";
        @Override
        protected String doInBackground(String... params) {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            sendJobSet.add(params[0]);
            if (netInfo != null && netInfo.isConnected()) {
                try {
                    URL url = new URL(urlbase);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    try {
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("content-type", "application/json");
                        if (!keepAlive) {
                            conn.setRequestProperty("connection", "close");
                        }
                        conn.setDoOutput(true);
                        conn.setDoInput(true);
                        conn.setUseCaches(false);
                        conn.setRequestProperty("Accept-Encoding", "");
//                        conn.setChunkedStreamingMode(1024);
                        conn.setInstanceFollowRedirects(true);

                        OutputStream os = conn.getOutputStream();
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                        writer.write(params[0]);
                        writer.flush();
                        writer.close();
                        os.flush();
                        os.close();
                        writeSensorLog(params[0], _info, "Send Attempt");

                        int response = conn.getResponseCode();
                        if (response == HttpURLConnection.HTTP_OK) {
                            Log.i(LOG_TAG, "Post succeed: " + params[0]);
                            InputStream is = conn.getInputStream();
                            BufferedReader br = new BufferedReader(new InputStreamReader(is));
                            String line = br.readLine();
                            while(line!=null){
                                Log.i("Http_response", line);
                                writeSensorLog("HTTP Response: " + line.trim(), _success, "Data");
                                line = br.readLine();
                            }
                            sendJobSet.remove(params[0]);
                        } else {
                            Log.e(LOG_ERR, "Post error code: " + response + " " + params[0]);
                            resendBatteryQueue.offer(params[0]);
                            writeSensorLog("Post err code: " + response + " " + params[0], _error);
                        }
                    } finally {
                        conn.disconnect();
                    }
                } catch (MalformedURLException e) {
                    Log.e(LOG_ERR, "Illegal URL");
                } catch (IOException e) {
                    Log.e(LOG_ERR, "Connection error " + e.getMessage() + " " + params[0]);
                    resendBatteryQueue.offer(params[0]);
                    writeSensorLog("Connection error: " + e.getMessage() + " " + params[0], _error);
                }
            } else {
                Log.e(LOG_ERR, "No active connection");
                resendBatteryQueue.offer(params[0]);
                writeSensorLog("No active connection, add to wait queue: " + params[0], _error);
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
        public boolean needs_to_reboot;
        public final String CONNECTED = "Connected.\nStreaming Data",
                AWAY = "Sensor out of range",
                DISCONNECTED_BODY = "Lost connection.\nReconnecting",
                DISCONNECTED_OBJ = "Download finished.",
                FAILURE = "Connection error.\nReconnecting",
                CONFIGURED = "Board configured.",
                CONNECTING = "Connecting",
                INITIATED = "Board reset",
                LOG_TAG = "Board_Log",
                DOWNLOAD_COMPLETED = "Data download completed";
        public Board() {}

        public ArrayList<String> filtering(ArrayList<String> previousCache, ArrayList<String> dataCache, int thres, int interval) {
            ArrayList<String> filteredCache = new ArrayList<> ();
            if (dataCache.size() == 0) {
                return filteredCache;
            }
            float last_ts;
            int prev_x, prev_y, prev_z;
            if (previousCache.size() == 0) {
                String[] f0 = dataCache.get(0).split(",");
                last_ts = 0;
                prev_x = Integer.valueOf(f0[1]);
                prev_y = Integer.valueOf(f0[2]);
                prev_z = Integer.valueOf(f0[3]);
            } else {
                String[] f0 = previousCache.get(previousCache.size() - 1).split(",");
                last_ts = 0;
                prev_x = Integer.valueOf(f0[1]);
                prev_y = Integer.valueOf(f0[2]);
                prev_z = Integer.valueOf(f0[3]);
//                Log.i("filter","previousCache with last_ts " + last_ts + ", x " + prev_x + ", y " + prev_y + ", z " + prev_z);
            }
            for (int i = 1; i < dataCache.size(); i++) {
                String s = dataCache.get(i);
                String[] fields = s.split(",");
                float ts = Float.valueOf(fields[0]);
                int x = Integer.valueOf(fields[1]);
                int y = Integer.valueOf(fields[2]);
                int z = Integer.valueOf(fields[3]);
                if (Math.abs(ts - last_ts) <= interval || Math.abs(x - prev_x) >= thres || Math.abs(y - prev_y) >= thres || Math.abs(z - prev_z) >= thres) {
                    filteredCache.add(s);
                    if (Math.abs(x - prev_x) >= thres || Math.abs(y - prev_y) >= thres || Math.abs(z - prev_z) >= thres) {
//                        Log.i("filter","Last timestamp updated from " + last_ts + " to " + ts);
                        last_ts = ts;
                    }
                    prev_x = x;
                    prev_y = y;
                    prev_z = z;
                }
            }
            return filteredCache;
        }

        public ArrayList<String> filtering(ArrayList<String> dataCache, int thres, int interval) {
            return filtering(new ArrayList<String>(), dataCache, thres, interval);
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
        private List<CartesianFloat> datalist = null;
        private int routeID = 0;
        boolean configured = false;
        private long connectionAttemptTS;
        private int connectionFailureCount;
        private java.util.Timer timer;
        private long infoTS = 0;
        private boolean destroyed = false;
        private int total = 0;
        private long lastDownloadTS;
        private long dl_TS;


        private final RouteManager.MessageHandler loggingMessageHandler = new RouteManager.MessageHandler() {
            @Override
            public void process(Message message) {
                datalist.add(message.getData(CartesianFloat.class));
            }
        };

        private final AsyncOperation.CompletionHandler<RouteManager> accelHandler = new AsyncOperation.CompletionHandler<RouteManager>() {
            @Override
            public void success(RouteManager result) {
//                state = board.serializeState();
                routeID = result.id();
                Log.i("info", String.format("RouteID: %d", routeID));
                writeSensorLog("Data routes established", _info, devicename);
                result.setLogMessageHandler(SENSOR_DATA_LOG, loggingMessageHandler);
            }
        };

        //generate timestamps
        private ArrayList<String> getFilteredDataCache (ArrayList<CartesianFloat> data_list, long last_timestamp) {
            ArrayList<String> temp = new ArrayList<>();
            ArrayList<String> dataCache;
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

        private ArrayList<String> getFilteredDataCache (ArrayList<CartesianFloat> data_list, long last_timestamp, int size) {
            ArrayList<String> temp = new ArrayList<>();
            ArrayList<String> dataCache;
            double record_ts = last_timestamp / 1000.0 - (size - 1) * 0.64;
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
            this.datalist = new ArrayList<>();
            this.needs_to_reboot = false;

            this.board.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
                @Override
                public void connected() {
                    needs_to_reboot = false;
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
                        writeSensorLog("Board Initialized", _info, devicename);
                        board.disconnect();
                    } else if (first == 1) {
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
                            lastDownloadTS = System.currentTimeMillis();
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
                                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, jsonstr);
//                                        task.execute(jsonstr);
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
                                                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, jsonstr);
//                                                    task.execute(jsonstr);
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
                        writeSensorLog("Board configured", _info, devicename);
                    } else if (first == 2) {
                        try {
                            sensor_status = CONNECTED;
                            final Logging logger = board.getModule(Logging.class);
                            dl_TS = System.currentTimeMillis();

                            // retrieve temperature and battery info
                            if (System.currentTimeMillis() - infoTS >= 210000) {
                                final MultiChannelTemperature mcTempModule;
                                try {
                                    board.readBatteryLevel().onComplete(new AsyncOperation.CompletionHandler<Byte>() {
                                        @Override
                                        public void success(Byte result) {
                                            // Send Battery Info
                                            battery = result.toString();
                                            Log.i("battery_" + devicename, battery);
                                            String jsonstr = getJSON(devicename,String.format("%.3f", System.currentTimeMillis()/1000.0), Integer.valueOf(battery));
                                            postBatteryAsync task = new postBatteryAsync();
                                            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, jsonstr);
//                                            task.execute(jsonstr);
                                        }
                                    });
                                    mcTempModule = board.getModule(MultiChannelTemperature.class);
                                    final List<MultiChannelTemperature.Source> tempSources = mcTempModule.getSources();
                                    mcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR));
                                } catch (UnsupportedModuleException e) {
                                    e.printStackTrace();
                                }
                            }

                            TimerTask interrupt = new TimerTask() {
                                @Override
                                public void run() {
                                    if (sensor_status.equals(CONNECTED)) {
                                        writeSensorLog("Download timed out", _info, devicename);
                                        int totalApprox = (int) (total / (((int) (total / 375.0)) * 1.0));
                                        if (!datalist.isEmpty()) {
                                            ArrayList<String> data = getFilteredDataCache((ArrayList<CartesianFloat>) datalist, dl_TS, totalApprox);
                                            if (data.size() > 0) {
                                                ArrayList<String> data_array = getJSONList(devicename, data);
                                                for (String s : data_array) {
                                                    resendDataQueue.offer(s);
                                                }
                                            }
                                            datalist.clear();
                                            total = 0;
                                            board.disconnect();
                                            sensor_status = DISCONNECTED_OBJ;
                                            broadcastStatus();
                                        } else {
                                            writeSensorLog("Activity not logged, Reset", _error, devicename);
                                            first = 1;
                                            try {
                                                accel_module = board.getModule(Bmi160Accelerometer.class);
                                                accel_module.stop();
                                                accel_module.disableAxisSampling();
                                                Logging logger = board.getModule(Logging.class);
                                                logger.stopLogging();
                                                logger.clearEntries();
                                            } catch (UnsupportedModuleException e) {
                                                e.printStackTrace();
                                            }
                                            board.removeRoutes();
                                            board.disconnect();
                                            sensor_status = INITIATED;
                                            lastDownloadTS = dl_TS;
                                            broadcastStatus();
                                        }
                                    }
                                }
                            };
                            timer.schedule(interrupt, 30000);
                            final long remaining_space = logger.getLogCapacity();
                            logger.downloadLog(0.1f, new Logging.DownloadHandler() {
                                @Override
                                public void onProgressUpdate(int nEntriesLeft, int totalEntries) {
                                    Log.i("data", String.format("Progress: %d/%d/%d", datalist.size(), totalEntries - nEntriesLeft, totalEntries));
//                                    expected_useful_download = (int) (totalEntries * datalist.size() / ((totalEntries - nEntriesLeft) * 1.0));
                                    writeSensorLog(String.format("Download Progress: %d/%d", totalEntries - nEntriesLeft, totalEntries), _info, devicename);
                                    total = totalEntries;
                                    if (nEntriesLeft == 0) {
                                        Log.i("data", "Download Completed");
                                        lastDownloadTS = dl_TS;
                                        writeSensorLog("Download Completed", _success, devicename);
                                        Log.i("data", "Generated " + datalist.size() + " data points");
                                        writeSensorLog("Generated " + datalist.size() + " data points", _success, devicename);
                                        sensor_status = DISCONNECTED_OBJ;
                                        //process listed data
                                        // send to server
                                        ArrayList<String> data = getFilteredDataCache((ArrayList<CartesianFloat>)datalist, dl_TS);
                                        if (data.size() > 0) {
                                            ArrayList<String> data_array = getJSONList(devicename, data);
                                            for (String s : data_array) {
                                                resendDataQueue.offer(s);
                                            }
//                                            String json = getJSON(devicename, data);
//                                            Log.i(devicename, json);
//                                            postDataAsync task = new postDataAsync();
//                                            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, json);
//                                            task.execute(json);
                                        }
                                        datalist.clear();
                                        sensor_status = DOWNLOAD_COMPLETED;

                                        if (total < 300 || remaining_space < 400) {
                                            writeSensorLog("Available records: " + total + " Remaining space: " + remaining_space, _info, devicename);
                                            writeSensorLog("No enough space on sensor, Reset", _error, devicename);
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
                                        }

                                        total = 0;

                                        timer.schedule(new TimerTask() {
                                            @Override
                                            public void run() {
                                                board.disconnect();
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
                        long interval = 240000 - (System.currentTimeMillis() - connectionAttemptTS) % 240000;
                        TimerTask reconnect = new TimerTask() {
                            @Override
                            synchronized public void run() {
//                                Set<String> nearMW = scanBle(5000);
                                connectionAttemptTS = System.currentTimeMillis();
                                writeSensorLog("Try to connect", _info, devicename);
                                board.connect();
                            }
                        };
                        timer.schedule(reconnect, interval);
                        writeSensorLog("Disconnected from the sensor and scheduled next connection in " + interval + " ms", _info, devicename);
                    }
                }

                @Override
                public void failure(int status, Throwable error) {
                    if (first != -1) {
                        error.printStackTrace();
                        writeSensorLog(error.getMessage(), _error, devicename);
                        sensor_status = FAILURE;
                        broadcastStatus();
                        if (error.getMessage().contains("status: 257") || error.getMessage().contains("Error connecting to gatt server")) {
                            needs_to_reboot = true;
                        }
                        connectionFailureCount += 1;
                        TimerTask reconnect = new TimerTask() {
                            @Override
                            synchronized public void run() {
                                writeSensorLog("Try to connect", _info, devicename);
                                board.connect();
                            }
                        };
                        TimerTask reconnect_long = new TimerTask() {
                            @Override
                            synchronized public void run() {
                                connectionAttemptTS = System.currentTimeMillis();
                                writeSensorLog("Try to connect", _info, devicename);
                                board.connect();
                            }
                        };
                        if (connectionFailureCount < 2) {
                            timer.schedule(reconnect, 0);
                            writeSensorLog("Reconnect attempt " + connectionFailureCount, _info, devicename);
                        } else {
                            connectionFailureCount = 0;
                            long interval = 240000 - (System.currentTimeMillis() - connectionAttemptTS) % 240000;
                            timer.schedule(reconnect_long, interval);
                            writeSensorLog("Skip this round, schedule to reconnect in " + interval + " ms", _info, devicename);
                        }
                    } else {
                        error.printStackTrace();
                        sensor_status = FAILURE;
                        broadcastStatus();
                        TimerTask reconnect = new TimerTask() {
                            @Override
                            synchronized public void run() {
                                connectionAttemptTS = System.currentTimeMillis();
                                writeSensorLog("Try to connect", _info, devicename);
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
        private ArrayList<String> workCache;
        public ArrayList<String> previousCache;
        public int dataCount;
        private float sampleFreq;
        private int uploadCount;
        private int sampleInterval;
        private final String devicename;
        private long temperature_timestamp;
        private long battery_timestamp;
        private java.util.Timer searchTM;
        private boolean away;
        private int reconnect_count;

        public BodyBoard(MetaWearBoard mxBoard, final String MAC, float freq) {
            this.board = mxBoard;
            this.MAC_ADDRESS = MAC;
            this.dataCount = 0;
            this.dataCache = new ArrayList<>();
            this.previousCache = new ArrayList<>();
            this.startTimestamp = new long[1];
            this.sampleFreq = freq;
            this.uploadCount = (int) (8 * sampleFreq);
            this.sampleInterval = (int) (1000 / sampleFreq);
            this.devicename = MAC_ADDRESS.replace(":", "");
            this.sensor_status = CONNECTING;
            this.temperature_timestamp = 0;
            this.battery_timestamp = 0;
            this.away = false;
            final String SENSOR_DATA_LOG = "Data:Sensor:" + MAC_ADDRESS;

            this.board.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
                @Override
                public void connected() {
//                    changeText(MAC_ADDRESS, CONNECTED);
                    reconnect_count = 0;
                    if (searchTM != null) {
                        searchTM.cancel();
                        searchTM.purge();
                        searchTM = null;
                    }
                    away = false;
                    writeSensorLog("Sensor connected", _info, devicename);
                    sensor_status = CONNECTED;
                    broadcastStatus();
                    final MultiChannelTemperature mcTempModule;
                    try {
                        //TODO: Work on adjusting sample rate
                        accel_module = board.getModule(Bmi160Accelerometer.class);
                        accel_module.setOutputDataRate(sampleFreq);
                        accel_module.routeData().fromAxes().stream(SENSOR_DATA_LOG).commit()
                                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                    @Override
                                    public void success(RouteManager result) {
                                        result.subscribe(SENSOR_DATA_LOG, new RouteManager.MessageHandler() {
                                            @Override
                                            public void process(Message message) {
                                                if (dataCache.size() == uploadCount) {
                                                    startTimestamp[0] = System.currentTimeMillis();
                                                    workCache = dataCache;
                                                    dataCache = new ArrayList<>();
                                                    dataCount = 0;
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

                                                    ArrayList<String> filteredCache = filtering(previousCache, workCache, 32, 3);
                                                    previousCache = new ArrayList<>(workCache);
                                                    workCache.clear();
                                                    if (filteredCache.size() > 0) {
                                                        ArrayList<String> data_array = getJSONList(devicename, filteredCache);
                                                        for (String s : data_array) {
                                                            resendDataQueue.offer(s);
                                                        }
                                                    }
                                                } else {
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
                                                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, jsonstr);
//                                                    task.execute(jsonstr);
                                                    temperature = String.format("%.3f", message.getData(Float.class));
                                                    Log.i(devicename, jsonstr);
                                                    // get body sensor battery info
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
                                        }, 0, 240000);

                                        timer.schedule(new TimerTask() {
                                            @Override
                                            public void run() {
                                                board.readBatteryLevel().onComplete(new AsyncOperation.CompletionHandler<Byte>() {
                                                    @Override
                                                    public void success(Byte result) {
                                                        // Send Battery Info
                                                        long timestamp = System.currentTimeMillis();
                                                        if (timestamp - battery_timestamp >= 220000) {
                                                            battery_timestamp = timestamp;
                                                            battery = result.toString();
                                                            Log.i("battery_Body", result.toString());
                                                            String jsonstr = getJSON(devicename, String.format("%.3f", System.currentTimeMillis() / 1000.0), Integer.valueOf(result.toString()));
                                                            postBatteryAsync task = new postBatteryAsync();
                                                            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, jsonstr);
                                                        }
                                                    }
                                                });
                                            }
                                        }, 120000,240000);
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
                        ArrayList<String> filteredCache = filtering(previousCache, temp, 32, 3);
                        previousCache = new ArrayList<>(temp);
                        if (filteredCache.size() != 0) {
                            ArrayList<String> data_array = getJSONList(devicename, filteredCache);
                            for (String s : data_array) {
                                resendDataQueue.offer(s);
                            }
                        }
                    }
                    if (!ActiveDisconnect) {
                        sensor_status = DISCONNECTED_BODY;
                        broadcastStatus();
                        writeSensorLog("Connection dropped, try to reconnect", _info, devicename);
                        board.connect();
                    }
                }

                @Override
                public void failure(int status, Throwable error) {
                    reconnect_count += 1;
                    if (!away && reconnect_count <= 5) {
                        if (searchTM != null) {
                            searchTM.cancel();
                            searchTM.purge();
                            searchTM = null;
                        }
                        error.printStackTrace();
                        writeSensorLog(error.getMessage(), _error, devicename);
                        sensor_status = FAILURE;
                        broadcastStatus();
                        if (dataCache.size() != 0) {
                            ArrayList<String> temp = new ArrayList<>(dataCache);
                            dataCache.clear();
                            startTimestamp[0] = System.currentTimeMillis();
                            dataCount = 0;
                            ArrayList<String> filteredCache = filtering(previousCache, temp, 32, 3);
                            previousCache = new ArrayList<String>(temp);
                            if (filteredCache.size() != 0) {
                                ArrayList<String> data_array = getJSONList(devicename, filteredCache);
                                for (String s : data_array) {
                                    resendDataQueue.offer(s);
                                }
                            }
                        }
                        writeSensorLog("Try to connect", _info, devicename);
                        board.connect();
                    } else {
                        searchTM = new Timer();
                        reconnect_count = 0;
                        searchTM.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                Set<String> nearMW = scanBle(8000);
                                if (nearMW.contains(SENSOR_MAC.get(0))) {
                                    away = false;
                                    board.connect();
                                } else {
                                    away = true;
                                    sensor_status = AWAY;
                                    broadcastStatus();
                                }
                            }
                        }, 0, 120000);
                    }
                }
            });
        }
    }
}
