package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 11/9/2015.
 */
public class Constants {

    public interface CONFIG {
        int BODY_MINUTE_INTERVAL = 2;
        int OBJECT_MINUTE_INTERVAL = 4;
        long SEARCH_BLE_DEVICE_INTERVAL = 1000 * 60 * 2;
        long ROTATION_MS = 1000 * 60 * 4;
        long DOWNLOAD_TIMEOUT = 1000 * 30;
        long BATTERY_INTERVAL = 1000 * 60 * 60;
        long TEMPERATURE_INTERVAL = 1000 * 60 * 15;
        long WAIT_AFTER_CONFIGURATION = 1000 * 5;
        long WAIT_AFTER_DOWNLOAD = 1000 * 2;
        long BODY_INTERVAL = 1000 * 60 * BODY_MINUTE_INTERVAL;
        long OBJECT_INTERVAL = 1000 * 60 * OBJECT_MINUTE_INTERVAL;
        int LOW_BATTERY_THRES = 3;
    }

    public interface ACTION {
        String MAIN_ACTION = "com.marothiatechs.foregroundservice.action.main";
        String INIT_ACTION = "com.marothiatechs.foregroundservice.action.init";
        String STARTFOREGROUND_ACTION = "com.marothiatechs.foregroundservice.action.startforeground";
        String STOPFOREGROUND_ACTION = "com.marothiatechs.foregroundservice.action.stopforeground";
    }

    public interface NOTIFICATION_ID {
        int FOREGROUND_SERVICE = 101;
        String BROADCAST_TAG = "com.marothiatechs.foregroundservice.action.broadcast";
    }
}