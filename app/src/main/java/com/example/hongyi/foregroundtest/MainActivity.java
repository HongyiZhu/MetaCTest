package com.example.hongyi.foregroundtest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.text.SimpleDateFormat;

import static com.example.hongyi.foregroundtest.ForegroundService.*;

public class MainActivity extends AppCompatActivity{
    private MyReceiver broadcastreceiver;
    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm:ss");

    public class MyReceiver extends BroadcastReceiver {
        private String lb_MAC = "MAC";
        private String lb_temp = "Temperature";
        private String lb_status = "Status";
        private String lb_TS = "TS";
        public MyReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String timestring = "Updated on: " + sdf.format(intent.getLongExtra("timestamp",-1));
            String MAC = intent.getStringExtra("name");
            String status = intent.getStringExtra("status");
            String temperature = intent.getStringExtra("temperature");
            String index = "";
            Log.i("Activity", MAC);
            if (MAC.equals(ForegroundService.getSensors(0))) {
                index = "_1";
            } else if (MAC.equals(ForegroundService.getSensors(1))) {
                index = "_2";
            } else if (MAC.equals(ForegroundService.getSensors(2))) {
                index = "_3";
            } else if (MAC.equals(ForegroundService.getSensors(3))) {
                index = "_4";
            } else if (MAC.equals(ForegroundService.getSensors(4))) {
                index = "_5";
            }
            if (!index.equals("")) {
                TextView TV_MAC = (TextView) findViewById(getResources().getIdentifier(lb_MAC+index,"id","com.example.hongyi.foregroundtest"));
                TV_MAC.setText(MAC);
                TextView TV_temperature = (TextView) findViewById(getResources().getIdentifier(lb_temp+index,"id","com.example.hongyi.foregroundtest"));
                if (!temperature.equals("-99999")) {
                    TV_temperature.setText(temperature);
                } else {
                    TV_temperature.setText("N/A");
                }
                TextView TV_Status = (TextView) findViewById(getResources().getIdentifier(lb_status+index,"id","com.example.hongyi.foregroundtest"));
                TV_Status.setText(status);
                TextView TV_TS = (TextView) findViewById(getResources().getIdentifier(lb_TS+index,"id","com.example.hongyi.foregroundtest"));
                TV_TS.setText(timestring);
            }
        }

        @Override
        public IBinder peekService(Context myContext, Intent service) {
            return super.peekService(myContext, service);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.setProperty("http.keepAlive", "false");
        String phoneID = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
        Log.i("phoneID", phoneID);
        if (!IS_SERVICE_RUNNING) {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            while (netInfo == null || !netInfo.isConnected()) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                netInfo = connMgr.getActiveNetworkInfo();
            }

            final Handler mHandler = new Handler() {
                @Override
                public void handleMessage(android.os.Message msg) {
                    BufferedWriter bw = null;
                    String res = (String) msg.obj;
                    String address = (getApplicationContext().getExternalFilesDirs("")[0].getAbsolutePath()).contains("external_SD") ? getApplicationContext().getExternalFilesDirs("")[1].getAbsolutePath() : getApplicationContext().getExternalFilesDirs("")[0].getAbsolutePath();
                    File folder = new File(address);
                    if (!folder.exists()) {
                        folder.mkdirs();
                    }
                    Intent service = new Intent(MainActivity.this, ForegroundService.class);
                    service.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
                    try {
                        JSONObject js = new JSONObject(res);
                        String send_url = js.getString("send_url");
                        service.putExtra("send_url", send_url);
                        JSONArray jsonarr = js.getJSONArray("sensors");
                        for (int i = 0;i<jsonarr.length();i++) {
                            JSONObject jsobj = jsonarr.getJSONObject(i);
                            String id = ((String) jsobj.get("sensor_id")).replaceAll("..(?!$)", "$0:");
                            String sn = (String) jsobj.get("sensor_sn");
                            String lb = sn.substring(sn.length() - 1);
                            Log.i("sensor", "Label: " + lb + ".\tMAC: "+id);
                            service.putExtra(lb, id);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    IS_SERVICE_RUNNING = true;
                    startService(service);
                    File config_file = new File(folder, "config.ini");
                    if (!config_file.exists()) {
                        try {
                            config_file.createNewFile();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    config_file.setReadable(true);
                    config_file.setWritable(true);
                    try {
                        bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(config_file)));
                        bw.write(res);
                        bw.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            if (bw != null) {
                                bw.close();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            Thread getReq = new Thread() {
                @Override
                public void run() {
                    String phoneID = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
                    Log.i("phoneID", phoneID);
                    URL url;
                    HttpURLConnection connection = null;
                    try {
                        url = new URL("https://app.silverlink247.com/startup");
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("POST");
                        connection.setRequestProperty("Accept", "application/json");
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        connection.setDoOutput(true);
                        connection.setDoInput(true);
                        connection.setUseCaches(false);
                        connection.setInstanceFollowRedirects(true);
                        connection.setRequestProperty("connection", "close");

                        try {
                            OutputStream os = connection.getOutputStream();
                            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                            String s = "{\"gateway\":\"" + phoneID + "\"}";
                            writer.write(s);
                            writer.flush();
                            writer.close();
                            os.flush();
                            os.close();

                            int response = connection.getResponseCode();
                            Log.i("HTTP", String.valueOf(response));

                            InputStream in = new BufferedInputStream(connection.getInputStream());
                            StringBuilder sb = new StringBuilder();

                            String line;
                            BufferedReader br = new BufferedReader(new InputStreamReader(in));
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                            String jsonstr = sb.toString();
                            android.os.Message msg = android.os.Message.obtain();
                            msg.obj = jsonstr;
                            msg.setTarget(mHandler);
                            msg.sendToTarget();
                        } catch (IOException e) {
                            Log.e("http_err", "IOException with .connect()");
                            e.printStackTrace();
                        } finally {
                            connection.disconnect();
                        }
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (ProtocolException e) {
                        Log.e("http_err", "ProtocolException");
                        e.printStackTrace();
                    } catch (IOException e) {
                        Log.e("http_err", "IOException");
                        e.printStackTrace();
                    }
                }
            };

            getReq.start();

        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = (Button) findViewById(R.id.button1);
        button.setText("Stop Service");

        broadcastreceiver = new MyReceiver();
        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(Constants.NOTIFICATION_ID.BROADCAST_TAG);
        registerReceiver(broadcastreceiver, intentfilter);



//        if (!IS_SERVICE_RUNNING) {
//            service.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
//            IS_SERVICE_RUNNING = true;
//            startService(service);
//        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(broadcastreceiver);
        super.onDestroy();
    }

    public void buttonClicked(View v) {
        Button button = (Button) v;
        Intent service = new Intent(MainActivity.this, ForegroundService.class);
        if (IS_SERVICE_RUNNING) {
            service.setAction(Constants.ACTION.STOPFOREGROUND_ACTION);
            IS_SERVICE_RUNNING = false;
            button.setText("Service Stopped");
            startService(service);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.reboot) {
            try {
                Process proc = Runtime.getRuntime()
                        .exec(new String[]{ "su", "-c", "reboot" });
                proc.waitFor();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return id == R.id.action_settings || super.onOptionsItemSelected(item);
    }

}
