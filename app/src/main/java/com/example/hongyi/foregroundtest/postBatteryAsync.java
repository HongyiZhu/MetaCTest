package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class postBatteryAsync extends MyAsyncTask {

    postBatteryAsync (ForegroundService service) {
        super(service, "/battery");
    }

    @Override
    protected String doInBackground(String... params) {
        return super.doInBackground(params);
    }
}
