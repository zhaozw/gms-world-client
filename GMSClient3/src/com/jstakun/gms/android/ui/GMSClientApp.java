/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jstakun.gms.android.ui;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;

import com.jstakun.gms.android.config.ConfigurationManager;
import com.jstakun.gms.android.utils.LoggerUtils;
import com.jstakun.gms.android.utils.UserTracker;


/**
 *
 * @author jstakun
 */
@ReportsCrashes(formKey = "", 
formUri = ConfigurationManager.CRASH_REPORT_URL,
mode = ReportingInteractionMode.TOAST,
resToastText = R.string.Crash_error,
socketTimeout = 30000)
public class GMSClientApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LoggerUtils.debug("GMSClientApp.onCreate...");
        ACRA.init(this);  
        ConfigurationManager.getAppUtils().initApp(this);  
        UserTracker.getInstance().initialize(getApplicationContext());
        //comment in production
        //UserTracker.getInstance().setDebug(true, this);
        //
        UserTracker.getInstance().setDryRun(false, this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        LoggerUtils.debug("GMSClientApp.onTerminate...");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        LoggerUtils.debug("GMSClientApp.onLowMemory...");
        System.gc();
    }
}
