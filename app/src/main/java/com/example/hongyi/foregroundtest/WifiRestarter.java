package com.example.hongyi.foregroundtest;

import android.content.Context;
import android.net.wifi.WifiManager;

/**
 * Created by Hongyi on 4/12/2016.
 */
public class WifiRestarter {
    ForegroundService service;

    WifiRestarter(ForegroundService service) {
        this.service = service;
    }

    public void restartWifi() {
        WifiManager wm = (WifiManager) service.getSystemService(Context.WIFI_SERVICE);
        wm.disconnect();
        wm.reconnect();
        service.wifiReset_report = false;
    }
}
