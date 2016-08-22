package com.example.hongyi.foregroundtest;

import android.util.Log;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.DataSignal;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Bmi160Accelerometer;
import com.mbientlab.metawear.module.DataProcessor;
import com.mbientlab.metawear.module.Debug;
import com.mbientlab.metawear.module.Logging;
import com.mbientlab.metawear.module.MultiChannelTemperature;
import com.mbientlab.metawear.module.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Hongyi on 3/17/2016.
 */
public class BodyLogBoard extends Board{
    private int minute_interval = 2;
    private final float sampleFreq;
    public String devicename;
    private final String SENSOR_DATA_LOG;
    public int connectionStage = Constants.STAGE.INIT;
    private List<Datapoint> datalist = null;
    private int routeID = 0;
    boolean configured = false;
    public long connectionAttemptTS;
    private int connectionFailureCount;
    private boolean away;
    private int scanCount;
    private java.util.Timer timer;
    private java.util.Timer searchTM;
    private boolean destroyed = false;
    private int total = 0;
    private long lastDownloadTS;
    private long dl_TS;
    private int period_expected;
    private double avg_samp_per_min;


    private final RouteManager.MessageHandler loggingMessageHandler = new RouteManager.MessageHandler() {
        @Override
        public void process(Message message) {
            //get timestamp
            if (System.currentTimeMillis() + 120000 >= message.getTimestamp().getTimeInMillis()) {
                double time = message.getTimestamp().getTimeInMillis() / 1000.0;
                CartesianFloat data = message.getData(CartesianFloat.class);
                datalist.add(new Datapoint(data, time));
            }
        }
    };

    private final AsyncOperation.CompletionHandler<RouteManager> accelHandler = new AsyncOperation.CompletionHandler<RouteManager>() {
        @Override
        public void success(RouteManager result) {
            routeID = result.id();
            data_ID = routeID;
            Log.i("info", String.format("RouteID: %d", routeID));
            service.writeSensorLog("Data routes established", ForegroundService._info, devicename);
            result.setLogMessageHandler(SENSOR_DATA_LOG, loggingMessageHandler);
        }

        @Override
        public void failure(Throwable error) {
            error.printStackTrace();
            connectionStage = Constants.STAGE.INIT;
        }
    };

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

    public BodyLogBoard(ForegroundService service, MetaWearBoard mxBoard, final String MAC, float freq) {
        super(service);
        this.board = mxBoard;
        this.MAC_ADDRESS = MAC;
        this.sampleFreq = freq;
        this.devicename = MAC_ADDRESS.replace(":", "");
        this.SENSOR_DATA_LOG = "Data:Sensor:" + MAC_ADDRESS;
        this.timer = new java.util.Timer();
        this.datalist = new ArrayList<>();
        this.needs_to_reboot = false;
        this.gatt_error = false;
        this.away = false;

        this.board.setConnectionStateHandler(new MyMetaWearBoardConnectionStateHandler(service) {
            @Override
            public void connected() {
                gatt_error = false;
                needs_to_reboot = false;
                scanCount = 0;
                connectionFailureCount = 0;
                away = false;
                if (searchTM != null) {
                    searchTM.cancel();
                    searchTM.purge();
                    searchTM = null;
                }
                service.resendHeartbeatQueue.offer(getJSON(devicename, String.format("%.3f", System.currentTimeMillis() / 1000.0)));
                if (connectionStage == Constants.STAGE.INIT) {
                    data_ID = -1;
                    temperature_ID = -1;
                    anymotion_ID = -1;
                    trigger_mode_timer_ID = -1;
                    connectionStage = Constants.STAGE.CONFIGURE;
                    board.removeRoutes();
                    try {
                        board.getModule(Settings.class)
                                .configureConnectionParameters()
                                .setMaxConnectionInterval(40.f)
                                .setSlaveLatency((short) 1)
                                .commit();
                    } catch (UnsupportedModuleException e) {
                        e.printStackTrace();
                    }
                    try {
                        Logging logger = board.getModule(Logging.class);
                        logger.stopLogging();
                        accel_module = board.getModule(Bmi160Accelerometer.class);
                        accel_module.disableAxisSampling();
                        accel_module.stop();
                        timerModule = board.getModule(com.mbientlab.metawear.module.Timer.class);
                        timerModule.removeTimers();
                        logger.clearEntries();
                        Debug debug = board.getModule(Debug.class);
                        debug.resetAfterGarbageCollect();
                    } catch (UnsupportedModuleException e) {
                        e.printStackTrace();
                    }
                    sensor_status = INITIATED;
                    broadcastStatus();
                    service.writeSensorLog("Board Initialized", ForegroundService._info, devicename);
                } else if (connectionStage == Constants.STAGE.CONFIGURE) {
                    connectionStage = Constants.STAGE.DOWNLOAD;
                    try {
                        Logging logger = board.getModule(Logging.class);
                        logger.startLogging(true);

                        accel_module = board.getModule(Bmi160Accelerometer.class);

                        timerModule = board.getModule(com.mbientlab.metawear.module.Timer.class);

                        timerModule.scheduleTask(new com.mbientlab.metawear.module.Timer.Task() {
                            @Override
                            public void commands() {
                                accel_module.disableAxisSampling();
                            }
                        }, 3000, true).onComplete(new AsyncOperation.CompletionHandler<com.mbientlab.metawear.module.Timer.Controller>() {
                            @Override
                            public void success(final com.mbientlab.metawear.module.Timer.Controller result) {
                                trigger_mode_timer_ID = result.id();
                                accel_module.routeData().fromMotion().monitor(new DataSignal.ActivityHandler() {
                                    @Override
                                    public void onSignalActive(Map<String, DataProcessor> map, DataSignal.DataToken dataToken) {
                                        result.start();
                                        accel_module.enableAxisSampling();
                                    }
                                }).commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                    @Override
                                    public void success(RouteManager result) {
                                        Log.i("Anymotion Route", String.valueOf(result.id()));
                                        anymotion_ID = result.id();
                                        accel_module.configureAxisSampling()
                                                .setOutputDataRate(Bmi160Accelerometer.OutputDataRate.ODR_12_5_HZ)
                                                .commit();
                                        accel_module.routeData().fromAxes().log(SENSOR_DATA_LOG).commit().onComplete(accelHandler);
                                        accel_module.configureAnyMotionDetection().setThreshold(0.032f).commit();
                                        accel_module.enableMotionDetection(Bmi160Accelerometer.MotionType.ANY_MOTION);
                                        accel_module.startLowPower();
                                    }

                                    @Override
                                    public void failure(Throwable error) {
                                        error.printStackTrace();
                                        connectionStage = Constants.STAGE.INIT;
                                    }
                                });
                            }

                            @Override
                            public void failure(Throwable error) {
                                error.printStackTrace();
                                connectionStage = Constants.STAGE.INIT;
                            }
                        });

                        lastDownloadTS = System.currentTimeMillis();
                        MultiChannelTemperature mcTempModule;
                        try {
                            mcTempModule = board.getModule(MultiChannelTemperature.class);
                            final List<MultiChannelTemperature.Source> tempSources= mcTempModule.getSources();
                            mcTempModule.routeData()
                                    .fromSource(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR)).stream("temp_"+devicename)
                                    .commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                @Override
                                public void success(RouteManager result) {
                                    Log.i("Temperature Route", String.valueOf(result.id()));
                                    temperature_ID = result.id();
                                    result.subscribe("temp_"+devicename, new RouteManager.MessageHandler() {
                                        @Override
                                        public void process(Message message) {
                                            long timestamp = System.currentTimeMillis();
                                            if (timestamp - tempTS >= Constants.CONFIG.TEMPERATURE_INTERVAL - 30 * 1000) {
                                                tempTS = timestamp;
                                                double ts_in_sec = timestamp / 1000.0;
                                                String jsonstr = getJSON(devicename, String.format("%.3f", ts_in_sec), String.format("%.3f", message.getData(Float.class)));
                                                service.resendTempQueue.offer(jsonstr);
                                                temperature =  String.format("%.3f", message.getData(Float.class));
                                                Log.i(devicename, jsonstr);
                                                broadcastStatus();
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void failure(Throwable error) {
                                    error.printStackTrace();
                                    connectionStage = Constants.STAGE.INIT;
                                }
                            });
                            board.readBatteryLevel().onComplete(new AsyncOperation.CompletionHandler<Byte>() {
                                @Override
                                public void success(Byte result) {
                                    //Send Battery Info
                                    battery = result.toString();
                                    Log.i("battery", battery);
                                    String jsonstr = getJSON(devicename, String.format("%.3f", System.currentTimeMillis() / 1000.0), Integer.valueOf(battery));
                                    service.resendBatteryQueue.offer(jsonstr);
                                    batteryTS = System.currentTimeMillis();
                                    if (Integer.valueOf(battery) <= low_battery_thres) {
                                        sensor_status = OUT_OF_BATTERY;
                                        connectionStage = Constants.STAGE.OUT_OF_BATTERY;
                                        broadcastStatus();
                                    }
                                }
                            });
                        } catch (UnsupportedModuleException e) {
                            e.printStackTrace();
                        }

                        // set board connection configure
                        try {
                            board.getModule(Settings.class)
                                    .configureConnectionParameters()
                                    .setMaxConnectionInterval(100.f)
                                    .setSlaveLatency((short) 20)
                                    .commit();
                        } catch (UnsupportedModuleException e) {
                            e.printStackTrace();
                        }

                        // Disconnect from the board
                        configured = true;
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                board.disconnect();
                                if (!sensor_status.equals(OUT_OF_BATTERY)) {
                                    sensor_status = CONFIGURED;
                                    broadcastStatus();
                                }
                            }
                        }, Constants.CONFIG.WAIT_AFTER_CONFIGURATION);
                    } catch (UnsupportedModuleException e) {
                        e.printStackTrace();
                    }
                    service.writeSensorLog("Board configured", ForegroundService._info, devicename);
                } else if (connectionStage == Constants.STAGE.DOWNLOAD || connectionStage == Constants.STAGE.BACK_IN_RANGE) {
                    try {
                        sensor_status = CONNECTED;
                        Logging logger = board.getModule(Logging.class);
                        dl_TS = System.currentTimeMillis();
                        long period = dl_TS - lastDownloadTS;
                        period_expected = (int) (avg_samp_per_min * (period / 60000.0));

                        // retrieve temperature and battery info

                        final MultiChannelTemperature mcTempModule;
                        try {
                            if (System.currentTimeMillis() - batteryTS >= Constants.CONFIG.BATTERY_INTERVAL - 30 * 1000) {
                                board.readBatteryLevel().onComplete(new AsyncOperation.CompletionHandler<Byte>() {
                                    @Override
                                    public void success(Byte result) {
                                        // Send Battery Info
                                        batteryTS = System.currentTimeMillis();
                                        battery = result.toString();
                                        Log.i("battery_" + devicename, battery);
                                        String jsonstr = getJSON(devicename, String.format("%.3f", System.currentTimeMillis() / 1000.0), Integer.valueOf(battery));
                                        service.resendBatteryQueue.offer(jsonstr);
                                        if (Integer.valueOf(battery) <= low_battery_thres) {
                                            sensor_status = OUT_OF_BATTERY;
                                            connectionStage = Constants.STAGE.OUT_OF_BATTERY;
                                            broadcastStatus();
                                        }
                                    }
                                });
                            }
                            if (System.currentTimeMillis() - tempTS >= Constants.CONFIG.TEMPERATURE_INTERVAL - 30 * 1000) {
                                mcTempModule = board.getModule(MultiChannelTemperature.class);
                                final List<MultiChannelTemperature.Source> tempSources = mcTempModule.getSources();
                                mcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR));
                            }
                        } catch (UnsupportedModuleException e) {
                            e.printStackTrace();
                        }

                        TimerTask interrupt = new TimerTask() {
                            @Override
                            public void run() {
                                if (sensor_status.equals(CONNECTED)) {
                                    service.writeSensorLog("Download timed out", ForegroundService._info, devicename);
                                    if (connectionStage == Constants.STAGE.BACK_IN_RANGE) {
                                        connectionStage = Constants.STAGE.INIT;
                                    }
                                    if (!datalist.isEmpty()) {
                                        ArrayList<String> data = getFilteredDataCache((ArrayList<Datapoint>) datalist);
                                        if (data.size() > 0) {
                                            ArrayList<String> data_array = getJSONList(devicename, data);
                                            for (String s : data_array) {
                                                service.resendDataQueue.offer(s);
                                            }
                                        }
                                        datalist.clear();
                                        total = 0;
                                        board.disconnect();
                                        if (!sensor_status.equals(OUT_OF_BATTERY)) {
                                            sensor_status = DISCONNECTED_OBJ;
                                            broadcastStatus();
                                        }
                                    } else {
                                        datalist.clear();
                                        board.disconnect();
                                        sensor_status = DISCONNECTED_OBJ;
                                        broadcastStatus();
                                    }
                                }
                            }
                        };
                        timer.schedule(interrupt, Constants.CONFIG.DOWNLOAD_TIMEOUT);

                        final long remaining_space = logger.getLogCapacity();
                        service.writeSensorLog(" Remaining space: " + remaining_space, ForegroundService._info, devicename);
                        if (remaining_space > 40) {
                            logger.downloadLog(0.1f, new Logging.DownloadHandler() {
                                @Override
                                public void onProgressUpdate(int nEntriesLeft, int totalEntries) {
                                    Log.i("data", String.format("Progress: %d/%d/%d", datalist.size(), totalEntries - nEntriesLeft, totalEntries));
                                    service.writeSensorLog(String.format("Download Progress: %d/%d", totalEntries - nEntriesLeft, totalEntries), ForegroundService._info, devicename);
                                    total = totalEntries;
                                    if (nEntriesLeft == 0) {
                                        Log.i("data", "Download Completed");
                                        lastDownloadTS = dl_TS;
//                                        expected_N = datalist.size();
                                        service.writeSensorLog("Download Completed", ForegroundService._success, devicename);
                                        Log.i("data", "Generated " + datalist.size() + " data points");
                                        service.writeSensorLog("Generated " + datalist.size() + " data points", ForegroundService._success, devicename);
                                        if (!sensor_status.equals(OUT_OF_BATTERY)) {
                                            sensor_status = DISCONNECTED_OBJ;
                                        }
                                        //process listed data
                                        // send to server
                                        if (!datalist.isEmpty()) {
                                            ArrayList<String> data = getFilteredDataCache((ArrayList<Datapoint>) datalist);
                                            if (data.size() > 0) {
                                                ArrayList<String> data_array = getJSONList(devicename, data);
                                                for (String s : data_array) {
                                                    service.resendDataQueue.offer(s);
                                                }
                                            }
                                        }
                                        datalist.clear();
                                        if (!sensor_status.equals(OUT_OF_BATTERY)) {
                                            sensor_status = DOWNLOAD_COMPLETED;
                                        }
                                        total = 0;

                                        if (connectionStage == Constants.STAGE.BACK_IN_RANGE) {
                                            connectionStage = Constants.STAGE.INIT;
                                        }

                                        timer.schedule(new TimerTask() {
                                            @Override
                                            public void run() {
                                                board.disconnect();
                                                broadcastStatus();
                                            }
                                        }, Constants.CONFIG.WAIT_AFTER_DOWNLOAD);
                                    }
                                }

                                @Override
                                public void receivedUnknownLogEntry(byte logId, Calendar timestamp, byte[] data) {
                                }

                                @Override
                                public void receivedUnhandledLogEntry(Message logMessage) {
                                    Log.i("dataUnhandled", logMessage.toString());
                                }
                            }).onComplete(new AsyncOperation.CompletionHandler<Integer>() {
                                @Override
                                public void success(Integer result) {
                                    Log.i("Data to download", String.valueOf(result));
                                    if (result == 0) {
                                        sensor_status = DISCONNECTED_OBJ;
                                        if (connectionStage == Constants.STAGE.BACK_IN_RANGE) {
                                            connectionStage = Constants.STAGE.INIT;
                                        }
                                        board.disconnect();
                                        broadcastStatus();
                                        service.writeSensorLog("No data generated", ForegroundService._success, devicename);
                                    }
                                }
                            });
                        } else {
                            service.writeSensorLog("No enough space on sensor, Reset", ForegroundService._error, devicename);
                            if (!sensor_status.equals(OUT_OF_BATTERY)) {
                                connectionStage = Constants.STAGE.CONFIGURE;
                                board.removeRoutes();
                                try {
                                    logger = board.getModule(Logging.class);
                                    logger.stopLogging();
                                    accel_module = board.getModule(Bmi160Accelerometer.class);
                                    accel_module.stop();
                                    accel_module.disableAxisSampling();
                                    logger.clearEntries();
                                    total = 0;
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
                            }, Constants.CONFIG.WAIT_AFTER_DOWNLOAD);
                        }
                    } catch (UnsupportedModuleException e) {
                        e.printStackTrace();
                    }
                } else if (connectionStage == Constants.STAGE.DESTROY) {
                    destroy();
                    destroyed = true;
                }
            }

            @Override
            public void disconnected() {
                if (connectionStage == Constants.STAGE.CONFIGURE || connectionStage == Constants.STAGE.INIT) {
                    TimerTask reconnect_after_reset = new TimerTask() {
                        @Override
                        synchronized public void run() {
                            connectionAttemptTS = System.currentTimeMillis();
                            service.writeSensorLog("Try to connect", ForegroundService._info, devicename);
                            board.connect();
                        }
                    };
                    // 30s between Reset and Configuration
                    timer.schedule(reconnect_after_reset, 120000);
                    service.writeSensorLog("Disconnected from the sensor and scheduled next connection in " + 120000 + " ms", ForegroundService._info, devicename);
                } else if (connectionStage == Constants.STAGE.DOWNLOAD) {
                    long interval = Constants.CONFIG.BODY_INTERVAL - (System.currentTimeMillis() - connectionAttemptTS) % (Constants.CONFIG.BODY_INTERVAL);
                    TimerTask reconnect = new TimerTask() {
                        @Override
                        synchronized public void run() {
                            connectionAttemptTS = System.currentTimeMillis();
                            service.writeSensorLog("Try to connect", ForegroundService._info, devicename);
                            board.connect();
                        }
                    };
                    // 2 mins' reconnection
                    timer.schedule(reconnect, interval);
                    service.writeSensorLog("Disconnected from the sensor and scheduled next connection in " + interval + " ms", ForegroundService._info, devicename);
                }
            }

            @Override
            public void failure(int status, Throwable error) {
                if (connectionStage != Constants.STAGE.OUT_OF_BATTERY) {
                    connectionFailureCount += 1;
                    if (!away && connectionFailureCount <= 5) {
                        if (searchTM != null) {
                            searchTM.cancel();
                            searchTM.purge();
                            searchTM = null;
                        }
                        error.printStackTrace();
                        service.writeSensorLog(error.getMessage(), ForegroundService._error, devicename);
                        sensor_status = FAILURE;
                        broadcastStatus();
                        gatt_error = error.getMessage().contains("Error connecting to gatt server");
                        if (error.getMessage().contains("status: 257")) {
                            needs_to_reboot = true;
                        }
                        if (scanCount >= 3) {
                            needs_to_reboot = true;
                        }
                        broadcastStatus();
                        service.writeSensorLog("Try to connect", ForegroundService._info, devicename);
                        board.connect();
                    } else {
                        searchTM = new Timer();
                        connectionFailureCount = 0;
                        searchTM.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                Set<String> nearMW = service.scanBle(8000);
                                if (nearMW.contains(service.getSensors(0))) {
                                    if (away) {
                                        away = false;
                                        scanCount = 0;
                                        connectionStage = Constants.STAGE.BACK_IN_RANGE;
                                        board.connect();
                                    } else {
                                        away = false;
                                        scanCount += 1;
                                        board.connect();
                                    }
                                } else {
                                    away = true;
                                    sensor_status = AWAY;
                                    broadcastStatus();
                                }
                            }
                        }, 0, Constants.CONFIG.SEARCH_BLE_DEVICE_INTERVAL);
                    }
                }
            }
        });
    }
}
