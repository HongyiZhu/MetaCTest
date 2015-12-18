package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 11/9/2015.
 */
public class Constants {


    public interface ACTION {
        public static String MAIN_ACTION = "com.marothiatechs.foregroundservice.action.main";
        public static String INIT_ACTION = "com.marothiatechs.foregroundservice.action.init";
        public static String STARTFOREGROUND_ACTION = "com.marothiatechs.foregroundservice.action.startforeground";
        public static String STOPFOREGROUND_ACTION = "com.marothiatechs.foregroundservice.action.stopforeground";
    }

    public interface NOTIFICATION_ID {
        public static int FOREGROUND_SERVICE = 101;
        public static String BROADCAST_TAG = "com.marothiatechs.foregroundservice.action.broadcast";
    }

//    public interface SENSORS {
//        public static String SENSOR1 = "DB:D1:AD:E3:E9:C3";
//        public static String SENSOR2 = "F2:50:71:B0:AE:E1";
//        public static String SENSOR3 = "DF:1C:5C:3F:F2:39";
//        public static String SENSOR4 = "D4:CC:1A:AE:D4:FB";
//        public static String SENSOR5 = "aa;aa;aa;aa;aa;aa";
//    }
}