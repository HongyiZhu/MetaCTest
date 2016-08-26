package com.example.hongyi.foregroundtest;

import android.os.AsyncTask;
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
 * Created by Hongyi on 3/16/2016.
 */
public class BodyBoard extends Board{
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

    private final RouteManager.MessageHandler streamingMessageHandler = new RouteManager.MessageHandler() {
        @Override
        public void process(Message message) {
            double time = message.getTimestamp().getTimeInMillis() / 1000.0;
            CartesianFloat data = message.getData(CartesianFloat.class);
            datalist.add(new Datapoint(data, time));
        }
    };

    private final AsyncOperation.CompletionHandler<RouteManager> accelHandler = new AsyncOperation.CompletionHandler<RouteManager>() {
        @Override
        public void success(RouteManager result) {
            routeID = result.id();
            data_ID = routeID;
            Log.i("info", String.format("RouteID: %d", routeID));
            service.writeSensorLog("Data routes established", ForegroundService._info, devicename);
            result.subscribe(SENSOR_DATA_LOG, streamingMessageHandler);
        }

        @Override
        public void failure(Throwable error) {
            error.printStackTrace();
            connectionStage = Constants.STAGE.INIT;
        }
    };

    public void destroy() {
        try {
            board.getModule(Settings.class)
                    .configureConnectionParameters()
                    .setMaxConnectionInterval(40.f)
                    .setSlaveLatency((short) 1)
                    .commit();
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

    public BodyBoard(ForegroundService service, MetaWearBoard mxBoard, final String MAC, float freq) {
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
                if (connectionStage == Constants.STAGE.INIT || connectionStage == Constants.STAGE.BACK_IN_RANGE) {
                    data_ID = -1;
                    temperature_ID = -1;
                    anymotion_ID = -1;
                    trigger_mode_timer_ID = -1;
                    connectionStage = Constants.STAGE.STREAMING;
                    board.removeRoutes();
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
                } else if (connectionStage == Constants.STAGE.STREAMING) {
                    try {
                        // Configure the any motion detection (aka. trigger mode)
                        accel_module = board.getModule(Bmi160Accelerometer.class);
                        timerModule = board.getModule(com.mbientlab.metawear.module.Timer.class);
                        TimerTask uploadData = new TimerTask(){
                            @Override
                            public void run() {
                                List<Datapoint> temp = new ArrayList<Datapoint>(datalist);
                                datalist.clear();
                                if (!temp.isEmpty()) {
                                    ArrayList<String> data = getFilteredDataCache((ArrayList<Datapoint>) temp);
                                    if (data.size() > 0) {
                                        ArrayList<String> data_array = getJSONList(devicename, data);
                                        for (String s : data_array) {
                                            service.resendDataQueue.offer(s);
                                        }
                                    }
                                }
                                broadcastStatus();
                            }
                        };

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
                                        accel_module.routeData().fromAxes().stream(SENSOR_DATA_LOG).commit().onComplete(accelHandler);
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

                        timer.schedule(uploadData, 10*1000, 10*1000);

                        // Configure temperature sampling and read battery level
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
                                        destroy();
                                    }
                                }
                            });
                        } catch (UnsupportedModuleException e) {
                            e.printStackTrace();
                        }

                    } catch (UnsupportedModuleException e) {
                        e.printStackTrace();
                    }
                    service.writeSensorLog("Board configured", ForegroundService._info, devicename);

                    sensor_status = CONNECTED;
                    broadcastStatus();

                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
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
                        }
                    }, 15*60*1000, 15*60*1000);

                } else if (connectionStage == Constants.STAGE.DESTROY) {
                    destroy();
                    destroyed = true;
                }
            }

            @Override
            public void disconnected() {
                if (connectionStage == Constants.STAGE.STREAMING || connectionStage == Constants.STAGE.INIT) {
                    TimerTask reconnect_after_reset = new TimerTask() {
                        @Override
                        synchronized public void run() {
                            connectionAttemptTS = System.currentTimeMillis();
                            service.writeSensorLog("Try to connect", ForegroundService._info, devicename);
                            board.connect();
                        }
                    };
                    // 30s between Reset and Configuration
                    timer.schedule(reconnect_after_reset, 30000);
                    service.writeSensorLog("Disconnected from the sensor and scheduled next connection in " + 30000 + " ms", ForegroundService._info, devicename);
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

