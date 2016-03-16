package com.example.hongyi.foregroundtest;

import java.util.TimerTask;

/**
 * Created by Hongyi on 3/16/2016.
 */
public abstract class MyTimerTask extends TimerTask {
    ForegroundService service;
    MyTimerTask (ForegroundService service) {
        super();
        this.service = service;
    }
}
