package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class postTempAsync extends MyAsyncTask {

    postTempAsync(ForegroundService service) {
        super(service, "/temperature");
    }

    @Override
    protected String doInBackground(String... params) {
        return super.doInBackground(params);
    }
}

