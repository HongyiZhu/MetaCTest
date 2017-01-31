package com.example.hongyi.foregroundtest;

import java.util.UUID;

/**
 * Created by Hongyi on 11/9/2015.
 */
public class Constants {

    public interface CONFIG {
        int BODY_MINUTE_INTERVAL = 2;
        int OBJECT_MINUTE_INTERVAL = 4;
        long SEARCH_BLE_DEVICE_INTERVAL = 1000 * 60 * 2;
        long ROTATION_MS = 1000 * 60 * 10;
        long DOWNLOAD_TIMEOUT = 1000 * 40;
        long BATTERY_INTERVAL = 1000 * 60 * 60;
        long TEMPERATURE_INTERVAL = 1000 * 60 * 15;
        long WAIT_AFTER_CONFIGURATION = 1000 * 10;
        long WAIT_AFTER_DOWNLOAD = 1000 * 2;
        long BODY_INTERVAL = 1000 * 60 * BODY_MINUTE_INTERVAL;
        long OBJECT_INTERVAL = 1000 * 60 * OBJECT_MINUTE_INTERVAL;
        int LOW_BATTERY_THRES = 3;
        long SCAN_DISCONNECT_MS = 1000 * 60 * 10;
    }

    public interface STAGE {
        int INIT = 0;
        int CONFIGURE = 1;
        int DOWNLOAD = 2;
        int BACK_IN_RANGE = 3;
        int STREAMING = 4;
        int DESTROY = -1;
        int OUT_OF_BATTERY = -2;
        int RESET = -255;
    }

    public interface ACTION {
        String MAIN_ACTION = "com.marothiatechs.foregroundservice.action.main";
        String INIT_ACTION = "com.marothiatechs.foregroundservice.action.init";
        String STARTFOREGROUND_ACTION = "com.marothiatechs.foregroundservice.action.startforeground";
        String STOPFOREGROUND_ACTION = "com.marothiatechs.foregroundservice.action.stopforeground";
        String SOS_RECEIVED = "com.marothiatechs.foregroundservice.action.sos_received";;
    }

    public interface NOTIFICATION_ID {
        int FOREGROUND_SERVICE = 101;
        String BROADCAST_TAG = "com.marothiatechs.foregroundservice.action.broadcast";
        UUID SOS = UUID.fromString("67A1586A-9AFC-11E6-9F33-A24FC0D9649C");
    }
}