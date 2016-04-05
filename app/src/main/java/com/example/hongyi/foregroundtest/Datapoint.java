package com.example.hongyi.foregroundtest;

import com.mbientlab.metawear.data.Cartesian;
import com.mbientlab.metawear.data.CartesianFloat;

/**
 * Created by Hongyi on 4/5/2016.
 */
public class Datapoint {
    public CartesianFloat data;
    public double timestamp;

    Datapoint (CartesianFloat data, double timestamp) {
        this.data = data;
        this.timestamp = timestamp;
    }
}
