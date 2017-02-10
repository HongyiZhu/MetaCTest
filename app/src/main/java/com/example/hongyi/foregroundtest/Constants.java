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

    public interface SOS_FLAGS {
        int NO_SOS_FOUND = 0;
        int SOS_FOUND = 1;
        int SOS_SIGNAL_SENDING = 2;
        int SOS_SIGNAL_SENT = 3;
        int SOS_CONFIRM_RECEIVED = 4;
        int SOS_CONFIRM_SENDING_TO_BOARD = 5;
        int SOS_CONFIRM_SENT_TO_BOARD = 6;
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
        String MAIN_ACTION = "com.silverlink.cic.action.main";
        String INIT_ACTION = "com.silverlink.cic.action.init";
        String STARTFOREGROUND_ACTION = "com.silverlink.cic.action.startforeground";
        String STOPFOREGROUND_ACTION = "com.silverlink.cic.action.stopforeground";
        String SOS_RECEIVED = "com.silverlink.cic.action.sos_received";;
    }

    public interface NOTIFICATION_ID {
        int FOREGROUND_SERVICE = 101;
        String BROADCAST_TAG = "com.silverlink.cic.notification.broadcast";
        String SOS_FOUND = "con.silverlink.cic.notification.sos_found";
        String SOS_CONFIRMED = "com.silverlink.cic.notification.sos_confirmed";
    }
}