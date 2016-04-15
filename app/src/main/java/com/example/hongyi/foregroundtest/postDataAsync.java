package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class postDataAsync extends MyAsyncTask {
    postDataAsync (ForegroundService service) {
        super(service, "/logs");
    }

    @Override
    protected String doInBackground(String... params) {
        return super.doInBackground(params);
    }
}

