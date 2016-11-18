package com.example.hongyi.foregroundtest;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

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
        wm.setWifiEnabled(false);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.i("WifiState", String.valueOf(wm.getWifiState()));
        while (wm.getWifiState() == WifiManager.WIFI_STATE_DISABLING) {
            try {
                Thread.sleep(100);
                Log.i("Wifi", "In disabling");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        wm.setWifiEnabled(true);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.i("WifiState", String.valueOf(wm.getWifiState()));
        while (wm.getWifiState() == WifiManager.WIFI_STATE_ENABLING) {
            try {
                Thread.sleep(100);
                Log.i("Wifi", "In enabling");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        service.wifiReset_report = 0;
    }
}
