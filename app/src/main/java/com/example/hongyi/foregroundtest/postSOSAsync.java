package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class postSOSAsync extends MyAsyncTask {

    postSOSAsync(ForegroundService service) {
        super(service, "/sos");
    }

    @Override
    protected String doInBackground(String... params) {
        return super.doInBackground(params);
    }
}

