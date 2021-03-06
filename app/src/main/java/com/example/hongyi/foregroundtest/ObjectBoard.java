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
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class ObjectBoard extends Board{
    private final float sampleFreq;
    private final String SENSOR_DATA_LOG;
    private List<Datapoint> datalist = null;
    private int routeID = 0;
    boolean configured = false;
    private int connectionFailureCount;
    private java.util.Timer timer;
    private boolean destroyed = false;
    private int total = 0;
    private long lastDownloadTS;
    private long dl_TS;
    private int period_expected;
    private double avg_samp_per_min;


    private final RouteManager.MessageHandler loggingMessageHandler = new RouteManager.MessageHandler() {
        @Override
        public void process(Message message) {
            if (System.currentTimeMillis() + 120000 >= message.getTimestamp().getTimeInMillis()) {
                CartesianFloat data = message.getData(CartesianFloat.class);
                double time = message.getTimestamp().getTimeInMillis() / 1000.0;
                datalist.add(new Datapoint(data, time));
            }
        }
    };

    private final AsyncOperation.CompletionHandler<RouteManager> accelHandler = new AsyncOperation.CompletionHandler<RouteManager>() {
        @Override
        public void success(RouteManager result) {
//                state = board.serializeState();
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

    public ObjectBoard(ForegroundService service, MetaWearBoard mxBoard, final String MAC, float freq, short setNo) {
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
        connectionStage = Constants.STAGE.INIT;
        this.rotationInterval = Constants.CONFIG.OBJECT_INTERVAL;
        this.setID = setNo;

        this.board.setConnectionStateHandler(new MyMetaWearBoardConnectionStateHandler(service) {
            @Override
            public void connected() {
                connectionFeedbackTS = System.currentTimeMillis();
                needs_to_reboot = false;
                gatt_error = false;
                connectionFailureCount = 0;
                service.resendHeartbeatQueue.offer(getJSON(devicename, String.format("%.3f", System.currentTimeMillis()/1000.0)));
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
                                .setMinConnectionInterval((float) 7.5)
                                .setMaxConnectionInterval(40.f)
                                .setSlaveLatency((short) 1)
                                .commit();
                        board.getModule(Settings.class)
                                .configure()
                                .setAdInterval((short) 417, (byte) 0)
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
//                        debug.resetDevice();
                        debug.resetAfterGarbageCollect();
//                        debug.disconnect();
                    } catch (UnsupportedModuleException e) {
                        e.printStackTrace();
                    }
                    sensor_status = INITIATED;
                    broadcastStatus();
                    service.writeSensorLog("Board Initialized", ForegroundService._info, devicename);
//                    board.disconnect();
                } else if (connectionStage == Constants.STAGE.CONFIGURE) {
                    connectionStage = Constants.STAGE.DOWNLOAD;
                    try {
                        Logging logger = board.getModule(Logging.class);
                        logger.startLogging(true);

                        board.readDeviceInformation().onComplete(new AsyncOperation.CompletionHandler<MetaWearBoard.DeviceInformation>() {
                            @Override
                            public void success(MetaWearBoard.DeviceInformation result) {
                                String version = result.firmwareRevision();
                                service.resendVersionQueue.offer(getVersionJSON(devicename, String.format("%.3f", System.currentTimeMillis() / 1000.0), version));
                            }

                            @Override
                            public void failure(Throwable error) {
                                super.failure(error);
                            }
                        });

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
                                                .setOutputDataRate(Bmi160Accelerometer.OutputDataRate.ODR_1_5625_HZ)
                                                .enableUndersampling((byte) 4)
                                                .commit();
                                        accel_module.routeData().fromAxes().log(SENSOR_DATA_LOG).commit().onComplete(accelHandler);
                                        accel_module.configureAnyMotionDetection().setThreshold(0.032f).commit();
                                        accel_module.enableMotionDetection(Bmi160Accelerometer.MotionType.ANY_MOTION);
                                        accel_module.startLowPower();
                                    }

                                    @Override
                                    public void failure(Throwable error) {
//                                        super.failure(error);
                                        error.printStackTrace();
                                        connectionStage = Constants.STAGE.INIT;
                                    }
                                });
                            }

                            @Override
                            public void failure(Throwable error) {
//                                super.failure(error);
                                error.printStackTrace();
                                connectionStage = Constants.STAGE.INIT;
                            }
                        });

                        lastDownloadTS = System.currentTimeMillis();
                        // Get Temperature and Battery
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
//                                                postTempAsync task = new postTempAsync(service);
//                                                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, jsonstr);
//                                                    task.execute(jsonstr);
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
//                            mcTempModule.readTemperature(tempSources.get(MultiChannelTemperature.MetaWearProChannel.ON_BOARD_THERMISTOR));
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
                            board.getModule(Settings.class)
                                    .configure()
                                    .setAdInterval((short) 1500, (byte) 0)
                                    .commit();
                        } catch (UnsupportedModuleException e) {
                            e.printStackTrace();
                        }

                        // Disconnect from the board
                        configured = true;
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
//                                try {
//                                    board.getModule(Logging.class).clearEntries();
//                                } catch (UnsupportedModuleException e) {
//                                    e.printStackTrace();
//                                }
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
                } else if (connectionStage == Constants.STAGE.DOWNLOAD) {
                    try {
                        sensor_status = CONNECTED;
                        final Logging logger = board.getModule(Logging.class);
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
                                    int totalApprox = (int) (total / (((int) (total / 375.0)) * 1.0));
                                    if (!datalist.isEmpty()) {
                                        List<Datapoint> temp = new ArrayList<>(datalist);
                                        datalist.clear();
                                        ArrayList<String> data = getFilteredDataCache((ArrayList<Datapoint>) temp);
                                        if (data.size() > 0) {
                                            ArrayList<String> data_array = getJSONList(devicename, data);
                                            for (String s : data_array) {
                                                service.resendDataQueue.offer(s);
                                            }
                                        }
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
                        logger.downloadLog(0.1f, new Logging.DownloadHandler() {
                            @Override
                            public void onProgressUpdate(int nEntriesLeft, int totalEntries) {
                                Log.i("data", String.format("Progress: %d/%d/%d", datalist.size(), totalEntries - nEntriesLeft, totalEntries));
//                                int expected_N = (int) (totalEntries * datalist.size() / ((totalEntries - nEntriesLeft) * 1.0));
                                service.writeSensorLog(String.format("Download Progress: %d/%d", totalEntries - nEntriesLeft, totalEntries), ForegroundService._info, devicename);
                                total = totalEntries;
                                if (nEntriesLeft == 0) {
                                    Log.i("data", "Download Completed");
                                    lastDownloadTS = dl_TS;
//                                    expected_N = datalist.size();
                                    service.writeSensorLog("Download Completed", ForegroundService._success, devicename);
                                    Log.i("data", "Generated " + datalist.size() + " data points");
                                    service.writeSensorLog("Generated " + datalist.size() + " data points", ForegroundService._success, devicename);
                                    if (!sensor_status.equals(OUT_OF_BATTERY)) {
                                        sensor_status = DISCONNECTED_OBJ;
                                    }
                                    //process listed data
                                    // send to server
                                    if (!datalist.isEmpty()) {
                                        ArrayList<Datapoint> temp = new ArrayList<>(datalist);
                                        datalist.clear();
                                        ArrayList<String> data = getFilteredDataCache((ArrayList<Datapoint>) temp);
                                        if (data.size() > 0) {
                                            ArrayList<String> data_array = getJSONList(devicename, data);
                                            for (String s : data_array) {
                                                service.resendDataQueue.offer(s);
                                            }
                                        }
                                    }
                                    if (!sensor_status.equals(OUT_OF_BATTERY)) {
                                        sensor_status = DOWNLOAD_COMPLETED;
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
                                    board.disconnect();
                                    broadcastStatus();
                                    service.writeSensorLog("No data generated", ForegroundService._success, devicename);
                                } else {

                                }
                            }
                        });
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
                if (connectionStage >= Constants.STAGE.INIT) {
                    long interval = rotationInterval - (System.currentTimeMillis() - rotationMarkTS) % rotationInterval;
                    futureConnectionTS = System.currentTimeMillis() + interval;
                    TimerTask reconnect = new TimerTask() {
                        @Override
                        synchronized public void run() {
                            service.writeSensorLog("Try to connect", ForegroundService._info, devicename);
                            try {
                                preConnectionTS = System.currentTimeMillis();
                                board.connect();
                            } catch (Exception e) {
                                Log.e("Reconnect Error", devicename);
                                e.printStackTrace();
                                service.writeSensorLog(e.getMessage(), ForegroundService._info, devicename);
                            }
                        }
                    };
                    reconnectTM.cancel();
                    reconnectTM.purge();
                    reconnectTM = new Timer();
                    reconnectTM.schedule(reconnect, interval);
                    service.writeSensorLog("Disconnected from the sensor and scheduled next connection in " + interval + " ms", ForegroundService._info, devicename);
                }
            }

            @Override
            public void failure(int status, Throwable error) {
                connectionFeedbackTS = System.currentTimeMillis();
                if (connectionStage == Constants.STAGE.OUT_OF_BATTERY) {

                } else {//if (connectionStage != Constants.STAGE.DESTROY) {
                    error.printStackTrace();
                    service.writeSensorLog(error.getMessage(), ForegroundService._error, devicename);
                    sensor_status = FAILURE;
                    broadcastStatus();
                    gatt_error = error.getMessage().contains("Error connecting to gatt server");
                    if (error.getMessage().contains("status: 257")) {
                        needs_to_reboot = true;
                    }
                    connectionFailureCount += 1;
                    TimerTask reconnect = new TimerTask() {
                        @Override
                        synchronized public void run() {
                            service.writeSensorLog("Try to connect", ForegroundService._info, devicename);
                            try {
                                preConnectionTS = System.currentTimeMillis();
                                board.connect();
                            } catch (Exception e) {
                                Log.e("Reconnect Error", devicename);
                                e.printStackTrace();
                                service.writeSensorLog(e.getMessage(), ForegroundService._info, devicename);
                            }
                        }
                    };
                    TimerTask reconnect_long = new TimerTask() {
                        @Override
                        synchronized public void run() {
                            service.writeSensorLog("Try to connect", ForegroundService._info, devicename);
                            try {
                                preConnectionTS = System.currentTimeMillis();
                                board.connect();
                            } catch (Exception e) {
                                Log.e("Reconnect Error", devicename);
                                e.printStackTrace();
                                service.writeSensorLog(e.getMessage(), ForegroundService._info, devicename);
                            }
                        }
                    };
                    if (connectionFailureCount < 2) {
                        futureConnectionTS = System.currentTimeMillis();
                        reconnectTM.cancel();
                        reconnectTM.purge();
                        reconnectTM = new Timer();
                        reconnectTM.schedule(reconnect, 0);
                        service.writeSensorLog("Reconnect attempt " + connectionFailureCount, ForegroundService._info, devicename);
                    } else {
                        connectionFailureCount = 0;
                        long interval = rotationInterval - (System.currentTimeMillis() - rotationMarkTS) % rotationInterval;
                        futureConnectionTS = System.currentTimeMillis() + interval;
                        reconnectTM.cancel();
                        reconnectTM.purge();
                        reconnectTM = new Timer();
                        reconnectTM.schedule(reconnect_long, interval);
                        service.writeSensorLog("Skip this round, schedule to reconnect in " + interval + " ms", ForegroundService._info, devicename);
                    }

                }
            }
        });
    }
}
