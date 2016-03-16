package com.example.hongyi.foregroundtest;

import com.mbientlab.metawear.MetaWearBoard;

/**
 * Created by Hongyi on 3/16/2016.
 */
public class MyMetaWearBoardConnectionStateHandler extends MetaWearBoard.ConnectionStateHandler {
    ForegroundService service;

    MyMetaWearBoardConnectionStateHandler (ForegroundService service) {
        super();
        this.service = service;
    }

}
