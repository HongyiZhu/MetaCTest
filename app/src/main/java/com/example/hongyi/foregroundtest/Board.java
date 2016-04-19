package com.example.hongyi.foregroundtest;

import android.content.Intent;
import android.util.Log;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Bmi160Accelerometer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class Board {
    public ForegroundService service;
    public MetaWearBoard board;
    public Bmi160Accelerometer accel_module;
    public com.mbientlab.metawear.module.Timer timerModule;
    public String sensor_status;
    public boolean ActiveDisconnect = false;
    public String MAC_ADDRESS;
    public String temperature = "-99999";
    public String battery;
    public int low_battery_thres = Constants.CONFIG.LOW_BATTERY_THRES;
    public boolean needs_to_reboot;
    public boolean gatt_error;
    public long tempTS;
    public long batteryTS;
    public final String CONNECTED = "Connected.\nStreaming Data",
            AWAY = "Sensor out of range",
            DISCONNECTED_BODY = "Lost connection.\nReconnecting",
            DISCONNECTED_OBJ = "Download finished.",
            FAILURE = "Connection error.\nReconnecting",
            CONFIGURED = "Board configured.",
            CONNECTING = "Connecting",
            INITIATED = "Board reset",
            LOG_TAG = "Board_Log",
            OUT_OF_BATTERY = "Out of Battery\nContact support team",
            DOWNLOAD_COMPLETED = "Data download completed";
    public Board(ForegroundService service) {
        this.service = service;
        tempTS = 0;
        batteryTS = 0;
    }

    public ArrayList<String> filtering(ArrayList<String> previousCache, ArrayList<String> dataCache, int thres, int interval) {
        ArrayList<String> filteredCache = new ArrayList<> ();
        if (dataCache.size() == 0) {
            return filteredCache;
        }
        float last_ts;
        int prev_x, prev_y, prev_z;
        if (previousCache.size() == 0) {
            String[] f0 = dataCache.get(0).split(",");
            last_ts = 0;
            prev_x = Integer.valueOf(f0[1]);
            prev_y = Integer.valueOf(f0[2]);
            prev_z = Integer.valueOf(f0[3]);
        } else {
            String[] f0 = previousCache.get(previousCache.size() - 1).split(",");
            last_ts = 0;
            prev_x = Integer.valueOf(f0[1]);
            prev_y = Integer.valueOf(f0[2]);
            prev_z = Integer.valueOf(f0[3]);
//                Log.i("filter","previousCache with last_ts " + last_ts + ", x " + prev_x + ", y " + prev_y + ", z " + prev_z);
        }
        for (int i = 1; i < dataCache.size(); i++) {
            String s = dataCache.get(i);
            String[] fields = s.split(",");
            float ts = Float.valueOf(fields[0]);
            int x = Integer.valueOf(fields[1]);
            int y = Integer.valueOf(fields[2]);
            int z = Integer.valueOf(fields[3]);
            if (Math.abs(ts - last_ts) <= interval || Math.abs(x - prev_x) >= thres || Math.abs(y - prev_y) >= thres || Math.abs(z - prev_z) >= thres) {
                filteredCache.add(s);
                if (Math.abs(x - prev_x) >= thres || Math.abs(y - prev_y) >= thres || Math.abs(z - prev_z) >= thres) {
//                        Log.i("filter","Last timestamp updated from " + last_ts + " to " + ts);
                    last_ts = ts;
                }
                prev_x = x;
                prev_y = y;
                prev_z = z;
            }
        }
        return filteredCache;
    }

    //generate timestamps
    public ArrayList<String> getFilteredDataCache (ArrayList<Datapoint> data_list) {
        ArrayList<String> temp = new ArrayList<>();
//        ArrayList<String> dataCache;
//        int count = data_list.size();
//        double record_ts = last_timestamp / 1000.0 - (count - 1) * 0.64;
        for (Datapoint dp: data_list) {
            CartesianFloat result = dp.data;
            float x = result.x();
            int x_int = (int) (x * 1000);
            float y = result.y();
            int y_int = (int) (y * 1000);
            float z = result.z();
            int z_int = (int) (z * 1000);
            double record_ts = dp.timestamp;
            temp.add(String.format("%.3f", record_ts) + "," + String.valueOf(x_int) +
                    "," + String.valueOf(y_int) + "," + String.valueOf(z_int));
//            record_ts += 0.64;
        }
//        dataCache = filtering(temp, 32, 3);

        return temp;
    }

    public ArrayList<String> filtering(ArrayList<String> dataCache, int thres, int interval) {
        return filtering(new ArrayList<String>(), dataCache, thres, interval);
    }

    public void broadcastStatus() {
        Intent intent = new Intent(Constants.NOTIFICATION_ID.BROADCAST_TAG);
        intent.putExtra("name", this.MAC_ADDRESS);
        intent.putExtra("status", this.sensor_status);
        intent.putExtra("temperature", this.temperature);
        intent.putExtra("timestamp", System.currentTimeMillis());
        Log.i("Intent", intent.getStringExtra("name"));
        service.sendBroadcast(intent);
    }

    public ArrayList<String> getJSONList (String name, ArrayList<String> data) {
        ArrayList<String> dataLists = new ArrayList<>();
        int start = 0;
        int trunk_size = 40;
        while (start + trunk_size <= data.size()) {
            JSONObject jsonstring = new JSONObject();
            JSONArray logs = new JSONArray();
            try {
                jsonstring.put("s", name);
                for (String s : data.subList(start, start + trunk_size)) {
                    JSONObject temp = new JSONObject();
                    temp.put("t", Double.valueOf(s.split(",")[0]));
                    temp.put("x", Integer.valueOf(s.split(",")[1]));
                    temp.put("y", Integer.valueOf(s.split(",")[2]));
                    temp.put("z", Integer.valueOf(s.split(",")[3]));
                    logs.put(temp);
                }
                jsonstring.put("logs", logs);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            dataLists.add(jsonstring.toString());
            start += trunk_size;
        }
        if (start < data.size()) {
            JSONObject jsonstring = new JSONObject();
            JSONArray logs = new JSONArray();
            try {
                jsonstring.put("s", name);
                for (String s : data.subList(start, data.size())) {
                    JSONObject temp = new JSONObject();
                    temp.put("t", Double.valueOf(s.split(",")[0]));
                    temp.put("x", Integer.valueOf(s.split(",")[1]));
                    temp.put("y", Integer.valueOf(s.split(",")[2]));
                    temp.put("z", Integer.valueOf(s.split(",")[3]));
                    logs.put(temp);
                }
                jsonstring.put("logs", logs);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            dataLists.add(jsonstring.toString());
        }
        return dataLists;
    }

    public String getJSON (String name, String ts) {
        JSONObject jsonstring = new JSONObject();
        try {
            jsonstring.put("s", name);
            jsonstring.put("t", Double.valueOf(ts));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonstring.toString();
    }

    public String getJSON (String name, String ts, String temperature) {
        JSONObject jsonstring = new JSONObject();
        try {
            jsonstring.put("s", name);
            jsonstring.put("t", Double.valueOf(ts));
            jsonstring.put("c", Float.valueOf(temperature));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonstring.toString();
    }

    public String getJSON (String name, String ts, int battery) {
        JSONObject jsonstring = new JSONObject();
        try {
            jsonstring.put("s", name);
            jsonstring.put("t", Double.valueOf(ts));
            jsonstring.put("b", Float.valueOf(battery));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonstring.toString();
    }

}
