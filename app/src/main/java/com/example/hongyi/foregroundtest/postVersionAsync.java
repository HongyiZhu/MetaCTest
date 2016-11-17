package com.example.hongyi.foregroundtest;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class postVersionAsync extends MyAsyncTask {

    postVersionAsync(ForegroundService service) {
        super(service, "/version");
    }

    @Override
    protected String doInBackground(String... params) {
        return super.doInBackground(params);
    }
}

