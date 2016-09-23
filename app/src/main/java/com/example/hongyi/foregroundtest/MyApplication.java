package com.example.hongyi.foregroundtest;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

/**
 * Created by Hongyi on 9/19/2016.
 */

@ReportsCrashes(
        formUri = "http://u.arizona.edu/~zhuhy/acra.php",
        mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.crash,
        reportType = org.acra.sender.HttpSender.Type.JSON,
        customReportContent = {
                ReportField.DEVICE_FEATURES, ReportField.INSTALLATION_ID, ReportField.ANDROID_VERSION,
                ReportField.STACK_TRACE, ReportField.DEVICE_ID, ReportField.APP_VERSION_CODE, ReportField.APP_VERSION_NAME,
                ReportField.CRASH_CONFIGURATION, ReportField.USER_CRASH_DATE, ReportField.PACKAGE_NAME, ReportField.BUILD,
                ReportField.BUILD_CONFIG, ReportField.DISPLAY, ReportField.LOGCAT,
        }
)
public class MyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        ACRA.init(this);
    }
}
