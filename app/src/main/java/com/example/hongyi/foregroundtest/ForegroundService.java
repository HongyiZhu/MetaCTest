package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 11/9/2015.
 * Foreground Service handles all the BLE connections
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
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
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
    public Set<String> sendJobSet;
    private Timer servicetimer;
    public Queue<String> resendDataQueue = new ConcurrentLinkedQueue<>();
    public Queue<String> resendBatteryQueue = new ConcurrentLinkedQueue<>();
    public Queue<String> resendTempQueue = new ConcurrentLinkedQueue<>();
    public Queue<String> resendHeartbeatQueue = new ConcurrentLinkedQueue<>();
    private File log_file;
    public static final String LOG_TAG = "ForegroundService", LOG_ERR = "http_err", _info = "INF";
    public static final String _success = "SUC", _error = "ERR";
    private final static ArrayList<String> SENSOR_MAC = new ArrayList<>();
    private final ArrayList<Board> boards = new ArrayList<>();
    private BluetoothAdapter btAdapter;
    private HashSet<UUID> filterServiceUuids;
    private ArrayList<ScanFilter> api21ScanFilters;
    private final static UUID[] serviceUuids;
    private static ExecutorService dataPool;
    private static ExecutorService heartbeatPool;
    public boolean keepAlive = false;
    private Timer restartTM;
    private Set<String> nearByDevices;
    public String send_url_base;
    public boolean wifiReset_report = false;
    private WifiRestarter wr;
    private boolean wifiReboot = false;
    private BluetoothAdapter.LeScanCallback deprecatedScanCallback= null;
    private ScanCallback api21ScallCallback= null;
    private long next_3am;

    public static boolean IS_SERVICE_RUNNING = false;

    static {
        serviceUuids= new UUID[] {
                MetaWearBoard.METAWEAR_SERVICE_UUID,
                MetaWearBoard.METABOOT_SERVICE_UUID
        };
        dataPool = Executors.newCachedThreadPool();
        heartbeatPool = Executors.newCachedThreadPool();
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

    public void writeSensorLog(String s, String... flags) {
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
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) >= 3) {
            now.add(Calendar.DATE, 1);
        }
        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        next_3am = now.getTimeInMillis();

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
                    } else if (line.contains("\"logs\"")) {
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
        wr = new WifiRestarter(this);

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
        ((BodyLogBoard) boards.get(0)).connectionStage = Constants.STAGE.DESTROY;
        boards.get(0).board.connect();
        Log.i(LOG_TAG, "Body sensor destroyed");
        writeSensorLog("Body sensor destroyed", _info);

        getApplicationContext().unbindService(this);
        super.onDestroy();
        Log.i(LOG_TAG, "All destruction finished");
        writeSensorLog("All destruction finished", _info);
//        Toast.makeText(this, "Service Detroyed!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case if services are bound (Bound Services).
        return null;
    }

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

        //
        if (System.currentTimeMillis() > next_3am) {
            flag = true;
        }

        boolean gatt_restart = true;
        for (int i = 0; i < SENSOR_MAC.size(); i++){
            gatt_restart = boards.get(i).gatt_error && gatt_restart;
        }

        for (int i = 0; i < SENSOR_MAC.size();i++){
            flag = boards.get(i).needs_to_reboot || flag;
        }

        flag = flag || wifiReboot || gatt_restart;

        return flag;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder_service) {
        // Timer to resend the data in the Queue
        servicetimer.schedule(new MyTimerTask(this) {
            @Override
            public void run() {
                if (!resendDataQueue.isEmpty()) {
                    String data = resendDataQueue.poll();
                    postDataAsync task = new postDataAsync(service);
                    task.executeOnExecutor(dataPool, data);
                }
                if (!resendBatteryQueue.isEmpty()) {
                    String data = resendBatteryQueue.poll();
                    postBatteryAsync task = new postBatteryAsync(service);
                    task.executeOnExecutor(heartbeatPool, data);
                }
                if (!resendTempQueue.isEmpty()) {
                    String data = resendTempQueue.poll();
                    postTempAsync task = new postTempAsync(service);
                    task.executeOnExecutor(heartbeatPool, data);
                }
                if (!resendHeartbeatQueue.isEmpty()) {
                    String data = resendHeartbeatQueue.poll();
                    postHeartbeatAsync task = new postHeartbeatAsync(service);
                    task.executeOnExecutor(heartbeatPool, data);
                }
            }
        }, 0, 500);

        MetaWearBleService.LocalBinder serviceBinder = (MetaWearBleService.LocalBinder) binder_service;

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

        Set<String> nearMW = scanBle(8000);
        Log.i("BLE", String.valueOf(nearMW.size()));

        // Add body sensor
        BluetoothDevice btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(0));
        boards.add(new BodyLogBoard(this, serviceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(0), 12.5f));
        Log.i(LOG_TAG, "Body Board added");
        writeSensorLog("Body Board added", _info);

        // Add four object sensors
        for (int i = 1; i < SENSOR_MAC.size(); i++){
            btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(i));
            boards.add(new ObjectBoard(this, serviceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(i), 1.5625f));
            Log.i(LOG_TAG, "Object Board added");
            writeSensorLog("Object Board added", _info);
        }

        BodyLogBoard body = (BodyLogBoard) boards.get(0);
        body.sensor_status = body.CONNECTING;
        body.broadcastStatus();
        writeSensorLog("Try to connect", _info, body.devicename);
        body.connectionAttemptTS = System.currentTimeMillis();
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
                if (wifiReset_report) {
                    wr.restartWifi();
                }
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

        restartTM.schedule(scanForRestart, 600000, Constants.CONFIG.ROTATION_MS);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
}
