package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class postGatewayHeartbeatAsync extends MyAsyncTask {

    postGatewayHeartbeatAsync(ForegroundService service) {
        super(service, "/g/heartbeat");
    }

    @Override
    protected String doInBackground(String... params) {
        return super.doInBackground(params);
    }
}

