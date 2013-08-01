package com.jstakun.gms.android.service;

import java.util.Calendar;

import com.jstakun.gms.android.config.ConfigurationManager;
import com.jstakun.gms.android.utils.LoggerUtils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AutoCheckinScheduleReceiver extends BroadcastReceiver {

	 // Restart service every x seconds
	  private static final long DEFAULT_REPEAT_TIME = 1000 * 300;

	  @Override
	  public void onReceive(Context context, Intent intent) {
		  LoggerUtils.debug("AutoCheckinScheduleReceiver.onReceive() executed...");
		  if (ConfigurationManager.getInstance().isOn(ConfigurationManager.AUTO_CHECKIN)) {
			  AlarmManager service = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			  Intent i = new Intent(context, AutoCheckinStartServiceReceiver.class);
			  PendingIntent pending = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
			  Calendar cal = Calendar.getInstance();
			  // Start x seconds after boot completed
			  cal.add(Calendar.SECOND, 60);
			  // Fetch every x seconds
			  long repeat_time = ConfigurationManager.getInstance().getLong(ConfigurationManager.AUTO_CHECKIN_REPEAT_TIME) * 1000;
			  if (repeat_time == -1000) {
				  repeat_time = DEFAULT_REPEAT_TIME;
			  }
			  LoggerUtils.debug("AutoCheckinScheduleReceiver.onReceive() setting auto checkin interval " + repeat_time + " milliseconds...");
			  service.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), repeat_time, pending);
		  }
	  }
}
