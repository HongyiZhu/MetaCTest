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
 * Created by Hongyi on 3/16/2016.
 */
public class postDataAsync extends AsyncTask<String, Boolean, String> {
    ForegroundService service;

    postDataAsync (ForegroundService service) {
        super();
        this.service = service;
    }

    String urlbase = service.send_url_base + "/logs";

    @Override
    protected String doInBackground(String... params) {
        ConnectivityManager connMgr = (ConnectivityManager) service.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
        HttpURLConnection conn = null;
        service.sendJobSet.add(params[0]);
        if (netInfo != null && netInfo.isConnected()) {
            try {
                URL url = new URL(urlbase);
                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("content-type", "application/json");
                if (!service.keepAlive) {
                    conn.setRequestProperty("connection", "close");
                }
                conn.setRequestProperty("Accept-Encoding", "");
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setUseCaches(false);
//                    conn.setChunkedStreamingMode(1024);
                conn.setInstanceFollowRedirects(true);

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(params[0]);
                writer.flush();
                writer.close();
                os.flush();
                os.close();
                service.writeSensorLog(params[0].length() > 80 ? params[0].substring(0, 80) : params[0], ForegroundService._info, "Send Attempt");

                int response = conn.getResponseCode();
                if (response == HttpURLConnection.HTTP_OK) {
                    Log.i(ForegroundService.LOG_TAG, "Post succeed: " + params[0]);
                    InputStream is = conn.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String line = br.readLine();
                    while(line!=null){
                        Log.i("Http_response", line);
                        service.writeSensorLog("HTTP Response: " + line.trim(), ForegroundService._success, "Data");
                        service.sendJobSet.remove(params[0]);
                        line = br.readLine();
                    }
                } else {
                    Log.e(ForegroundService.LOG_ERR, "Post error code: " + response + " " + params[0]);
                    service.resendDataQueue.offer(params[0]);
                    service.writeSensorLog("Post err code: " + response + " " + params[0].substring(0, 80), ForegroundService._error);
                }

            } catch (MalformedURLException e) {
                Log.e(ForegroundService.LOG_ERR, "Illegal URL");
            } catch (IOException e) {
                Log.e(ForegroundService.LOG_ERR, "Connection error " + ((e.getMessage() == null) ? e : e.getMessage()) + " " + params[0]);
                service.resendDataQueue.offer(params[0]);
                service.writeSensorLog("Connection error: " + e.getMessage() + " " + params[0].substring(0, 80), ForegroundService._error);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        } else {
            Log.e(ForegroundService.LOG_ERR, "No active connection, add request back to queue");
            service.resendDataQueue.offer(params[0]);
            service.writeSensorLog("No active connection, add to wait queue: " + params[0].substring(0, 80), ForegroundService._error);
        }
        return null;
    }
}

