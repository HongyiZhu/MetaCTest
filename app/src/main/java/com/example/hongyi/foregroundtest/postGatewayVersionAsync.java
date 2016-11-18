package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class postGatewayVersionAsync extends MyAsyncTask {

    postGatewayVersionAsync(ForegroundService service) {
        super(service, "/g/version");
    }

    @Override
    protected String doInBackground(String... params) {
        return super.doInBackground(params);
    }
}

