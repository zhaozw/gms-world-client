/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jstakun.gms.android.ui.deals;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.jstakun.gms.android.config.ConfigurationManager;
import com.jstakun.gms.android.ui.IntentsHelper;
import com.jstakun.gms.android.utils.DateTimeUtils;
import com.jstakun.gms.android.utils.LoggerUtils;
import com.jstakun.gms.android.utils.OsUtil;

import org.acra.ACRA;

/**
 *
 * @author jstakun
 */
public class DealMapDispatchActivity extends Activity {

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {

        IntentsHelper intents = new IntentsHelper(this, null, null);
        boolean abort = false;


        try {
            //Alcatel OT-980 issue java.lang.NoClassDefFoundError
            super.onCreate(icicle);
        } catch (Throwable t) {
            ACRA.getErrorReporter().handleSilentException(t);
            intents.showInfoToast("Sorry. Your device is currently unsupported :(");
            abort = true;
        }

        if (!abort) {
            final Intent intent = getIntent();
            final String action = intent.getAction();
            // If the intent is a request to create a shortcut, we'll do that and exit
            if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
                intents.setupShortcut();
                abort = true;
            } else {
            	long lastStartupTime = ConfigurationManager.getInstance().getLong(ConfigurationManager.LAST_STARTING_DATE);
                LoggerUtils.debug("Last startup time is: " + DateTimeUtils.getDefaultDateTimeString(lastStartupTime, ConfigurationManager.getInstance().getCurrentLocale()));
                ConfigurationManager.getInstance().putString(ConfigurationManager.LAST_STARTING_DATE, Long.toString(System.currentTimeMillis()));
                
                Intent mapActivity;
                if (OsUtil.isHoneycombOrHigher()) {
                   mapActivity = new Intent(this, DealMap2Activity.class); 
                } else {
                   mapActivity = new Intent(this, DealMapActivity.class);    
                }
                startActivity(mapActivity);
            }
        }
        
        if (abort) {
            finish();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        finish();
    }
}
