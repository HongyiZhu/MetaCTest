package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 11/9/2015.
 */
public class Constants {

    public interface CONFIG {
        long ROTATION_MS = 1000 * 60 * 4;
        long DOWNLOAD_TIMEOUT = 1000 * 30;
        long BATTERY_INTERVAL = 1000 * 60 * 60;
        long TEMPERATURE_INTERVAL = 1000 * 60 * 15;
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