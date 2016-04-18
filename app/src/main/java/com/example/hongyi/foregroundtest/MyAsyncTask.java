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
                    service.writeSensorLog(params.length>80?params[0].substring(0,80):params[0], ForegroundService._info, "Send Attempt");

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
                    } else {
                        Log.e(ForegroundService.LOG_ERR, "Post error code: " + response + " " + params[0]);
                        service.resendBatteryQueue.offer(params[0]);
                        service.writeSensorLog("Post err code: " + response + " " + params[0], ForegroundService._error);
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (MalformedURLException e) {
                Log.e(ForegroundService.LOG_ERR, "Illegal URL");
            } catch (IOException e) {
                Log.e(ForegroundService.LOG_ERR, "Connection error " + e.getMessage() + " " + params[0]);
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
                }
                service.writeSensorLog("Connection error: " + e.getMessage() + " " + params[0], ForegroundService._error);
                if (e.getMessage().contains("") && !service.wifiReset_report) {
                    service.wifiReset_report = true;
                }
            }
        } else {
            Log.e(ForegroundService.LOG_ERR, "No active connection");
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
            }
            service.writeSensorLog("No active connection, add to wait queue: " + params[0], ForegroundService._error);
        }
        return null;
    }
}
