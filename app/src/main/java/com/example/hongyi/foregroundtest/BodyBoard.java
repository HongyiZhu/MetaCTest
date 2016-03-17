package com.example.hongyi.foregroundtest;

import android.os.AsyncTask;
import android.util.Log;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Bmi160Accelerometer;
import com.mbientlab.metawear.module.MultiChannelTemperature;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class BodyBoard extends Board{
    public long[] startTimestamp;
    public ArrayList<String> dataCache;
    private ArrayList<String> workCache;
    public ArrayList<String> previousCache;
    public int dataCount;
    private float sampleFreq;
    private int uploadCount;
    private int sampleInterval;
    public final String devicename;
    private long temperature_timestamp;
    private long battery_timestamp;
    private java.util.Timer searchTM;
    private boolean away;
    private int reconnect_count;

    public BodyBoard(ForegroundService service, MetaWearBoard mxBoard, final String MAC, float freq) {
        super(service);
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

        this.board.setConnectionStateHandler(new MyMetaWearBoardConnectionStateHandler(service) {
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
                service.writeSensorLog("Sensor connected", ForegroundService._info, devicename);
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
                                                        service.resendDataQueue.offer(s);
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
                                        postTempAsync task = new postTempAsync(service);
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
                            }, 0, 900000);

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
                                                postBatteryAsync task = new postBatteryAsync(service);
                                                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, jsonstr);
                                            }
                                        }
                                    });
                                }
                            }, 120000, 240000);
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
                            service.resendDataQueue.offer(s);
                        }
                    }
                }
                if (!ActiveDisconnect) {
                    sensor_status = DISCONNECTED_BODY;
                    broadcastStatus();
                    service.writeSensorLog("Connection dropped, try to reconnect", ForegroundService._info, devicename);
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
                    service.writeSensorLog(error.getMessage(), ForegroundService._error, devicename);
                    sensor_status = FAILURE;
                    broadcastStatus();
                    if (dataCache.size() != 0) {
                        ArrayList<String> temp = new ArrayList<>(dataCache);
                        dataCache.clear();
                        startTimestamp[0] = System.currentTimeMillis();
                        dataCount = 0;
                        ArrayList<String> filteredCache = filtering(previousCache, temp, 32, 3);
                        previousCache = new ArrayList<>(temp);
                        if (filteredCache.size() != 0) {
                            ArrayList<String> data_array = getJSONList(devicename, filteredCache);
                            for (String s : data_array) {
                                service.resendDataQueue.offer(s);
                            }
                        }
                    }
                    service.writeSensorLog("Try to connect", ForegroundService._info, devicename);
                    board.connect();
                } else {
                    searchTM = new Timer();
                    reconnect_count = 0;
                    searchTM.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Set<String> nearMW = service.scanBle(8000);
                            if (nearMW.contains(service.getSensors(0))) {
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
