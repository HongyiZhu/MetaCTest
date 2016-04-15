package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 4/14/2016.
 */
public class postHeartbeatAsync extends MyAsyncTask {

    postHeartbeatAsync(ForegroundService service) {
        super(service, "/heartbeat");
    }

    @Override
    protected String doInBackground(String... params) {
        return super.doInBackground(params);
    }
}
