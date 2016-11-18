package com.example.hongyi.foregroundtest;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by Hongyi on 4/14/2016.
 */
public class MyAsyncTask extends AsyncTask<String, Boolean, String> {
    ForegroundService service;
    String urlbase;
    String request;

    MyAsyncTask(ForegroundService service, String request) {
        super();
        this.service = service;
        this.request = request.split("/")[1];
        urlbase = service.send_url_base + request;
    }

    private boolean need_to_drop(String message) {
        if (message.contains("DNS") || message.contains("unreachable") || message.contains("resolve")) {
            if (service.wifiReset_report == 0) {
                service.wifiReset_report = 1;
            }
            return true;
        }
        return false;
    }

    @Override
    protected String doInBackground(String... params) {
        ConnectivityManager connMgr = (ConnectivityManager) service.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
        service.sendJobSet.add(params[0]);
        if (netInfo != null && netInfo.isConnected()) {
            try {
                URL url = new URL(urlbase);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("content-type", "application/json");
                    if (!service.keepAlive) {
                        conn.setRequestProperty("connection", "close");
                    }
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setUseCaches(false);
                    conn.setRequestProperty("Accept-Encoding", "");
//                        conn.setChunkedStreamingMode(1024);
                    conn.setInstanceFollowRedirects(true);

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(params[0]);
                    writer.flush();
                    writer.close();
                    os.flush();
                    os.close();
                    service.writeSensorLog(params[0].length()>80?params[0].substring(0,80):params[0], ForegroundService._info, "Send Attempt");

                    int response = conn.getResponseCode();
                    if (response == HttpURLConnection.HTTP_OK) {
                        Log.i(ForegroundService.LOG_TAG, "Post succeed: " + params[0]);
                        InputStream is = conn.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        String line = br.readLine();
                        while(line!=null){
                            Log.i("Http_response", line);
                            service.writeSensorLog("HTTP Response: " + line.trim(), ForegroundService._success, request);
                            line = br.readLine();
                        }
                        service.sendJobSet.remove(params[0]);
                    } else if (response == 409) {
                        // Drop data if duplicated entries found
                        service.sendJobSet.remove(params[0]);
                        service.writeSensorLog("Database error: dropped:  " + params[0], ForegroundService._error);
//                    } else if (response >= 500) {
//                        // Drop data to reduce server load
//                        service.sendJobSet.remove(params[0]);
//                        service.writeSensorLog("Server error: dropped:  " + params[0], ForegroundService._error);
                    } else {
                        Log.e(ForegroundService.LOG_ERR, "Post error code: " + response + " " + params[0]);
                        switch (request) {
                            case "heartbeat":
                                service.resendHeartbeatQueue.offer(params[0]);
                                break;
                            case "logs":
                                service.resendDataQueue.offer(params[0]);
                                break;
                            case "temperature":
                                service.resendTempQueue.offer(params[0]);
                                break;
                            case "battery":
                                service.resendBatteryQueue.offer(params[0]);
                                break;
                            case "version":
                                service.resendVersionQueue.offer(params[0]);
                                break;
                            case "g/version":
                                service.resendGatewayVersionQueue.offer(params[0]);
                                break;
                        }
                        service.writeSensorLog("Post err code: " + response + " " + params[0], ForegroundService._error);
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (MalformedURLException e) {
                Log.e(ForegroundService.LOG_ERR, "Illegal URL");
            } catch (IOException e) {
                // Catch Wifi error
                Log.e(ForegroundService.LOG_ERR, "Connection error " + e.getMessage() + " " + params[0]);
                if (e.getMessage() != null && need_to_drop(e.getMessage())) {
                    service.sendJobSet.remove(params[0]);
                    service.writeSensorLog("Connection error: " + e.getMessage() + ", dropped:  " + params[0], ForegroundService._error);
//                service.writeSensorLog("Connection error: " + e.getMessage() + " " + params[0], ForegroundService._error);
                } else {
                    service.writeSensorLog("Connection error: " + e.getMessage() + " " + params[0], ForegroundService._error);
                    switch (request) {
                        case "heartbeat":
                            service.resendHeartbeatQueue.offer(params[0]);
                            break;
                        case "logs":
                            service.resendDataQueue.offer(params[0]);
                            break;
                        case "temperature":
                            service.resendTempQueue.offer(params[0]);
                            break;
                        case "battery":
                            service.resendBatteryQueue.offer(params[0]);
                            break;
                        case "version":
                            service.resendVersionQueue.offer(params[0]);
                            break;
                        case "g/version":
                            service.resendGatewayVersionQueue.offer(params[0]);
                            break;
                    }
                }
            }
        } else {
            // Catch Wifi error
            Log.e(ForegroundService.LOG_ERR, "No active connection");
            if (service.wifiReset_report == 0) {
                service.wifiReset_report = 1;
            }
//            switch (request) {
//                case "heartbeat":
//                    service.resendHeartbeatQueue.offer(params[0]);
//                    break;
//                case "logs":
//                    service.resendDataQueue.offer(params[0]);
//                    break;
//                case "temperature":
//                    service.resendTempQueue.offer(params[0]);
//                    break;
//                case "battery":
//                    service.resendBatteryQueue.offer(params[0]);
//                    break;
//            }
            service.writeSensorLog("No active connection, dropped: " + params[0], ForegroundService._error);
//            service.writeSensorLog("No active connection, add to wait queue: " + params[0], ForegroundService._error);
            service.sendJobSet.remove(params[0]);
        }
        return null;
    }
}
