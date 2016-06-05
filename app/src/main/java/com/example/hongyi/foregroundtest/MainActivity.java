package com.example.hongyi.foregroundtest;

import android.app.DownloadManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.hongyi.foregroundtest.ForegroundService.*;

public class MainActivity extends AppCompatActivity{
    private MyReceiver broadcastreceiver;
    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm:ss");
    long download_id;
    String phoneID;
    String versionName;

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
            if (intent.getAction().equals(Constants.NOTIFICATION_ID.BROADCAST_TAG)) {
                String timestring = "Updated on: " + sdf.format(intent.getLongExtra("timestamp", -1));
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
                    TextView TV_MAC = (TextView) findViewById(getResources().getIdentifier(lb_MAC + index, "id", "com.example.hongyi.foregroundtest"));
                    TV_MAC.setText(MAC);
                    TextView TV_temperature = (TextView) findViewById(getResources().getIdentifier(lb_temp + index, "id", "com.example.hongyi.foregroundtest"));
                    if (!temperature.equals("-99999")) {
                        TV_temperature.setText(temperature);
                    } else {
                        TV_temperature.setText("N/A");
                    }
                    TextView TV_Status = (TextView) findViewById(getResources().getIdentifier(lb_status + index, "id", "com.example.hongyi.foregroundtest"));
                    TV_Status.setText(status);
                    TextView TV_TS = (TextView) findViewById(getResources().getIdentifier(lb_TS + index, "id", "com.example.hongyi.foregroundtest"));
                    TV_TS.setText(timestring);
                }
            } else if (intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                Log.i("Download", "received");
                Log.i("Download", String.valueOf(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)));
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == download_id) {
//                    Log.i("Download", "In building intend");
//                    Uri uri = Uri.parse("file://" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/SilverLinkC.apk");
//                    Intent i = new Intent(Intent.ACTION_VIEW);
//                    i.setDataAndType(uri, "application/vnd.android.package-archive");
//                    startActivity(i);
                    Intent startHelper =  new Intent();
                    startHelper.setClassName("com.caduceusintel.silverlinkhelper", "com.caduceusintel.silverlinkhelper.MainActivity");
                    startActivity(startHelper);
                }
            }
        }

        @Override
        public IBinder peekService(Context myContext, Intent service) {
            return super.peekService(myContext, service);
        }
    }

    private long parse(String ver) {
        int verBuild, verMinor, verMajor;
        long vercode;
        verMajor = Integer.valueOf(ver.split("\\.")[0]);
        verMinor = Integer.valueOf(ver.split("\\.")[1]);
        verBuild = Integer.valueOf(ver.split("\\.")[2]);
        vercode = verBuild + verMinor * 10000 + verMajor * 1000000;

        return vercode;
    }

    private boolean needsUpgrade(String old, String res) {
        try {
            JSONObject js = new JSONObject(res);
            if (js.has("latest_version") && !js.isNull("latest_version")) {
                String latest = js.getString("latest_version");
                return (parse(old) < parse(latest));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.setProperty("http.keepAlive", "false");
        phoneID = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
        Log.i("phoneID", phoneID);
        // get version name
        PackageInfo pinfo;
        try {
            pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pinfo.versionName;
            Log.i("version", versionName);
        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
//        mQueue = Volley.newRequestQueue(this);
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
                    if (!needsUpgrade(versionName, res)) {
                        try {
                            JSONObject js = new JSONObject(res);
                            String send_url = js.getString("send_url");
                            service.putExtra("send_url", send_url);
                            JSONArray jsonarr = js.getJSONArray("sensors");
                            for (int i = 0; i < jsonarr.length(); i++) {
                                JSONObject jsobj = jsonarr.getJSONObject(i);
                                String id = ((String) jsobj.get("sensor_id")).replaceAll("..(?!$)", "$0:");
                                String sn = (String) jsobj.get("sensor_sn");
                                String lb = sn.substring(sn.length() - 1);
                                Log.i("sensor", "Label: " + lb + ".\tMAC: " + id);
                                service.putExtra(lb, id);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        IS_SERVICE_RUNNING = true;
                        startService(service);
                    } else {
                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        String url = "http://bit.ly/1LS4vrq";
                        try {
                            JSONObject js = new JSONObject(res);
                            if (js.has("download_address") && !js.isNull("download_address")){
                                url = js.getString("download_address");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        Uri uri = Uri.parse(url);
                        DownloadManager.Request request = new DownloadManager.Request(uri);
                        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
                        request.setVisibleInDownloadsUi(true);

                        File[] files = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()).listFiles();
                        for (File f : files) {
                            Log.i("filename", f.getName());
                            if (f.isFile() && f.getName().contains("SilverLinkC")) {
                                f.delete();
                            }
                        }

                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SilverLinkC.apk");
                        download_id = dm.enqueue(request);
                        Log.i("Download", String.valueOf(download_id));
                    }
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
                    phoneID = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
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
                            String s = "{\"gateway\":\"" + phoneID + "\",\"version\":\"" + versionName + "\"}";
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
        intentfilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        registerReceiver(broadcastreceiver, intentfilter);
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
        } else if (id == R.id.upgrade) {
            if (parse(versionName)<parse("0.0.0428")) {
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                String url = "http://bit.ly/1LS4vrq";
                Uri uri = Uri.parse(url);
                DownloadManager.Request request = new DownloadManager.Request(uri);
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
                request.setVisibleInDownloadsUi(true);

                File[] files = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()).listFiles();
                for (File f : files) {
                    Log.i("filename", f.getName());
                    if (f.isFile() && f.getName().contains("SilverLinkC")) {
                        f.delete();
                    }
                }

                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SilverLinkC.apk");
                download_id = dm.enqueue(request);
                Log.i("Download", String.valueOf(download_id));
            }
        }

        return id == R.id.upgrade || super.onOptionsItemSelected(item);
    }

}
