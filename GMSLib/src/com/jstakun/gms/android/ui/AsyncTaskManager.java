/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jstakun.gms.android.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;

import android.app.Activity;
import android.location.Location;
import android.os.Handler;
import android.os.Message;

import com.jstakun.gms.android.config.Commons;
import com.jstakun.gms.android.config.ConfigurationManager;
import com.jstakun.gms.android.data.FileManager;
import com.jstakun.gms.android.data.PersistenceManagerFactory;
import com.jstakun.gms.android.deals.CategoriesManager;
import com.jstakun.gms.android.deals.CategoryJsonParser;
import com.jstakun.gms.android.landmarks.ExtendedLandmark;
import com.jstakun.gms.android.landmarks.KMLParser;
import com.jstakun.gms.android.landmarks.LandmarkManager;
import com.jstakun.gms.android.routes.RouteRecorder;
import com.jstakun.gms.android.routes.RoutesManager;
import com.jstakun.gms.android.social.GMSUtils;
import com.jstakun.gms.android.social.ISocialUtils;
import com.jstakun.gms.android.social.OAuthServiceFactory;
import com.jstakun.gms.android.ui.lib.R;
import com.jstakun.gms.android.utils.DateTimeUtils;
import com.jstakun.gms.android.utils.GMSAsyncTask;
import com.jstakun.gms.android.utils.HttpUtils;
import com.jstakun.gms.android.utils.Locale;
import com.jstakun.gms.android.utils.LoggerUtils;
import com.jstakun.gms.android.utils.MercatorUtils;
import com.jstakun.gms.android.utils.OsUtil;

/**
 *
 * @author jstakun
 */
public class AsyncTaskManager {

    public static final int SHOW_ROUTE_MESSAGE = 30;
    private Map<Integer, GMSAsyncTask<?,?,?>> tasksInProgress;
    private GMSNotificationManager notificationManager;
    private LandmarkManager landmarkManager;
    private IntentsHelper intents;
    private Activity activity;

    public AsyncTaskManager(Activity context, LandmarkManager lm) {
        tasksInProgress = new ConcurrentHashMap<Integer, GMSAsyncTask<?,?,?>>();
        landmarkManager = lm;
        if (context != null) {
            notificationManager = new GMSNotificationManager(context);
            setActivity(context);
            intents = new IntentsHelper(context, landmarkManager, null);
        }
    }

    public final void setActivity(Activity context) {
        this.activity = context;
    }

    public void cancelTask(Integer taskId, boolean notify) {
        String message;
        if (tasksInProgress.containsKey(taskId)) {
            //System.out.println("cancelTask: " + taskId + "-----------------------------------");
            GMSAsyncTask<?,?,?> task = tasksInProgress.get(taskId);
            task.cancel(true);
            tasksInProgress.remove(taskId);
            notificationManager.cancelNotification(taskId);
            message = Locale.getMessage(R.string.Task_stopped);
        } else {
            message = Locale.getMessage(R.string.Task_finished);
        }

        if (notify) {
            intents.showInfoToast(message);
        }
    }

    public void cancelAll() {
        //cancel all tasksinprogress & notifications

        for (Integer taskId : tasksInProgress.keySet()) {
            cancelTask(taskId, false);
        }

        tasksInProgress.clear();
        notificationManager.cancelAll();
    }

    public String createNotification(int icon, String ticker, String title, boolean delete) {
        int notificationId = notificationManager.createNotification(icon, ticker, title, delete);
        return Integer.toString(notificationId);
    }

    public void cancelNotification(int num) {
    	notificationManager.cancelNotification(num);
    }

    private String sendSocialNotification(String auth_status, String send_status, String service, ExtendedLandmark landmark, int type) {
        String response = null;
    	if (ConfigurationManager.getInstance().isOn(auth_status) && ConfigurationManager.getInstance().isOn(send_status)) {
            response = OAuthServiceFactory.getSocialUtils(service).sendPost(landmark, type);
        }
    	return response;
    }

    private String sendSocialNotification(ExtendedLandmark landmark, int type) {
    	List<String> errors = new ArrayList<String>();
    	if (landmark != null) {
            String res = null;
        	res = sendSocialNotification(ConfigurationManager.FB_AUTH_STATUS, ConfigurationManager.FB_SEND_STATUS, Commons.FACEBOOK, landmark, type);          
        	if (res != null) {
        		errors.add(res);
        	}
        	res = sendSocialNotification(ConfigurationManager.TWEET_AUTH_STATUS, ConfigurationManager.TWEET_SEND_STATUS, Commons.TWITTER, landmark, type);
        	if (res != null) {
        		errors.add(res);
            }
       	    res = sendSocialNotification(ConfigurationManager.LN_AUTH_STATUS, ConfigurationManager.LN_SEND_STATUS, Commons.LINKEDIN, landmark, type);
       	    if (res != null) {
       	    	errors.add(res);
            }
       	    res = sendSocialNotification(ConfigurationManager.GL_AUTH_STATUS, ConfigurationManager.GL_SEND_STATUS, Commons.GOOGLE, landmark, type);
       	    if (res != null) {
        	   errors.add(res);
            }
        }
    	if (errors.isEmpty()) {
    		return null;
    	} else {
    		return StringUtils.join(errors, ",");
    	}
    }

    public String sendMyPos() {
        Location location = ConfigurationManager.getInstance().getLocation();
        String msg = null;
        ExtendedLandmark myPos = null;

        if (location != null) {
        	//if (!ConfigurationManager.getUserManager().isUserLoggedIn()) {
        	//	ConfigurationManager.getInstance().putObject(Commons.MY_POS_CODE, Commons.MY_POS_CODE);
        	//}
        	myPos = landmarkManager.createLandmark(location.getLatitude(), location.getLongitude(), (float) location.getAltitude(), Commons.MY_POSITION_LAYER, null, null);
        	String username = ConfigurationManager.getUserManager().getLoggedInUsername();
            if (username == null) {
            	username = Commons.MY_POS_USER;
            }
        	msg = landmarkManager.persistToServer(myPos, Commons.MY_POS_CODE, null, username);
        } else {
        	LoggerUtils.debug("Can't send my location !!!");
        }

        if (myPos != null && msg == null) {
            String tmp = sendSocialNotification(myPos, Commons.MY_POS);
            msg = Locale.getMessage(R.string.Location_sent);
            if (tmp != null) {
            	msg = msg + ",\n" + tmp;
            }
        } else {
            msg = Locale.getMessage(R.string.Location_send_error, msg);
        }
        return msg;
    }

    private abstract class GenericTask extends GMSAsyncTask<String, Void, String> {

        protected String filename;
        protected int notificationId;

        public GenericTask() {
            super(5);
        }

        @Override
        protected void onPreExecute() {
        	if (notificationId >= 0) {
        		tasksInProgress.put(notificationId, this);
        	}
        }

        @Override
        protected String doInBackground(String... fileData) {
            filename = fileData[0];
            notificationId = Integer.parseInt(fileData[1]);
            return null;
        }

        @Override
        protected void onPostExecute(String res) {
            clear();
        }

        protected void clear() {
        	if (notificationId >= 0) {
            	notificationManager.cancelNotification(notificationId);
            	tasksInProgress.remove(notificationId);
        	}	
        }
    }

    public void executeRouteLoadingTask(String filename, Handler showRouteHandler) {
        String route_loading_msg = Locale.getMessage(R.string.Routes_Background_task_loading);
        intents.showInfoToast(Locale.getMessage(R.string.Task_started, route_loading_msg));
        LoadRouteTask routeLoading = new LoadRouteTask(showRouteHandler);
        String notificationId = createNotification(R.drawable.route_24, route_loading_msg, route_loading_msg, true);
        routeLoading.execute(filename, notificationId);
        //if (!AsyncTaskExecutor.execute(routeLoading, activity, filename, Integer.toString(notificationId))) {
        //    routeLoading.clear();
        //}
    }

    private class LoadRouteTask extends GenericTask {

        private Handler showRouteHandler;

        public LoadRouteTask(Handler showRouteHandler) {
            super();
            this.showRouteHandler = showRouteHandler;
        }

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            if (res.equals("0")) {
                intents.showInfoToast(Locale.getMessage(R.string.Routes_Failed));
            } else {
                Message msg = showRouteHandler.obtainMessage(SHOW_ROUTE_MESSAGE, filename);
                showRouteHandler.handleMessage(msg);
                intents.showInfoToast(Locale.getMessage(R.string.Routes_Loaded));
            }
        }

        @Override
        protected String doInBackground(String... fileData) {
            super.doInBackground(fileData);
            List<ExtendedLandmark> routePoints = new ArrayList<ExtendedLandmark>();
            KMLParser parser = new KMLParser();

            try {
                parser.parse(FileManager.getRoutesFolderPath(), filename, routePoints, false, Commons.ROUTES_LAYER, this);
                if (!isCancelled()) {
                    RoutesManager routesManager = ConfigurationManager.getInstance().getRoutesManager();
                    if (routesManager != null) {
                        String description = parser.getDescription();
                        routesManager.addRoute(filename, routePoints, description);
                    }
                }
            } catch (Exception ex) {
                LoggerUtils.error("LoadRouteTask.callDoInBackground exception", ex);
            }

            return Integer.toString(routePoints.size());
        }
    }

    public void executeRouteServerLoadingTask(Handler showRouteHandler, boolean saveRoute, ExtendedLandmark end) {
        List<ExtendedLandmark> myPosV = landmarkManager.getUnmodifableLayer(Commons.MY_POSITION_LAYER);

        if (!myPosV.isEmpty()) {

            String type = "car/fastest";
            int routeType = ConfigurationManager.getInstance().getInt(ConfigurationManager.ROUTE_TYPE);

            if (routeType == 1) {
                type = "car/shortest";
            } else if (routeType == 2) {
                type = "foot";
            }

            ExtendedLandmark start = myPosV.get(0);
            String route_loading_msg = Locale.getMessage(R.string.Routes_Background_task_loading);
            intents.showInfoToast(Locale.getMessage(R.string.Task_started, route_loading_msg));
            LoadServerRouteTask routeLoading = new LoadServerRouteTask(showRouteHandler, saveRoute);
            String notificationId = createNotification(R.drawable.route_24, route_loading_msg, route_loading_msg, true);
            double[] start_coords = MercatorUtils.normalizeE6(new double[]{start.getQualifiedCoordinates().getLatitude(), start.getQualifiedCoordinates().getLongitude()});
            double[] end_coords = MercatorUtils.normalizeE6(new double[]{end.getQualifiedCoordinates().getLatitude(), end.getQualifiedCoordinates().getLongitude()});
            String routeName = StringUtils.replace(end.getName(), " ", "_") + "-My_Location-" + DateTimeUtils.getCurrentDateStamp();
            routeLoading.execute(routeName, notificationId, Double.toString(start_coords[0]), Double.toString(start_coords[1]), Double.toString(end_coords[0]), Double.toString(end_coords[1]), type);
            //if (!AsyncTaskExecutor.execute(routeLoading, activity, routeName, Integer.toString(notificationId), Double.toString(start_coords[0]), Double.toString(start_coords[1]), Double.toString(end_coords[0]), Double.toString(end_coords[1]), type)) {
            //    routeLoading.clear();
            //}
        } else {
            intents.showInfoToast(Locale.getMessage(R.string.GPS_location_missing_error));
        }
    }

    private class LoadServerRouteTask extends GenericTask {

        private Handler showRouteHandler = null;
        private boolean saveRoute;

        public LoadServerRouteTask(Handler showRouteHandler, boolean saveRoute) {
            super();
            this.showRouteHandler = showRouteHandler;
            this.saveRoute = saveRoute;
        }

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            Message msg = showRouteHandler.obtainMessage(SHOW_ROUTE_MESSAGE, filename);
            showRouteHandler.handleMessage(msg);
            intents.showInfoToast(res);
        }

        @Override
        protected String doInBackground(String... loadingData) {
            super.doInBackground(loadingData);
            String lat_start = loadingData[2];
            String lng_start = loadingData[3];
            String lat_end = loadingData[4];
            String lng_end = loadingData[5];
            String type = loadingData[6];
            RoutesManager routesManager = ConfigurationManager.getInstance().getRoutesManager();
            if (routesManager != null) {
                return routesManager.loadRouteFromServer(lat_start, lng_start, lat_end, lng_end, type, filename, saveRoute);
            } else {
                return null;
            }
        }
    }

    public void executePoiFileLoadingTask(String filename) {
        if (!landmarkManager.getLayerManager().layerExists(filename)) {
            String files_loading_msg = Locale.getMessage(R.string.Files_Background_task_loading);
            intents.showInfoToast(Locale.getMessage(R.string.Task_started, files_loading_msg));
            LoadPoiFileTask poiLoading = new LoadPoiFileTask();
            String notificationId = createNotification(R.drawable.star24, files_loading_msg, files_loading_msg, true);
            poiLoading.execute(filename, notificationId);
            //if (!AsyncTaskExecutor.execute(poiLoading, activity, filename, Integer.toString(notificationId))) {
            //  poiLoading.clear();
            //}
        } else {
            intents.showInfoToast(Locale.getMessage(R.string.Layer_exists));
        }
    }

    private class LoadPoiFileTask extends GenericTask {

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            if (res.equals("0")) {
                intents.showInfoToast(Locale.getMessage(R.string.Files_Failed));
            } else {
                intents.showInfoToast(Locale.getMessage(R.string.Files_Loaded, res));
            }
        }

        @Override
        protected String doInBackground(String... fileData) {
            super.doInBackground(fileData);
            int count = loadFileAction(filename, this);
            return Integer.toString(count);
        }
    }

    private int loadFileAction(String filename, GMSAsyncTask<?,?,?> caller) {
        String iconPath = filename.substring(0, filename.lastIndexOf('.')) + ".png";
        KMLParser parser = new KMLParser();
        List<ExtendedLandmark> landmarks = new ArrayList<ExtendedLandmark>();

        try {
            parser.parse(FileManager.getFileFolderPath(), filename, landmarks, false, filename, caller);
        } catch (Exception ex) {
            LoggerUtils.error("AsyncTaskManager.loadFileAction exception", ex);
        }

        if (!caller.isCancelled()) {
            if (!PersistenceManagerFactory.getFileManager().fileExists(FileManager.getIconsFolderPath(), iconPath)) {
                iconPath = null;
            }

            landmarkManager.addLayer(filename, false, true, true, false, true, iconPath, null, null, filename, landmarks);
        }

        return landmarks.size();
    }

    public void executeSaveRouteTask(String message) {
        //intents.showInfoToast(Locale.getMessage("Background.task.executed", new Object[]{message}));
        intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
        SaveRouteTask saveRoute = new SaveRouteTask();
        String notificationId = createNotification(R.drawable.route_24, message, message, true);
        saveRoute.execute("", notificationId);
        //if (!AsyncTaskExecutor.execute(saveRoute, activity, "", Integer.toString(notificationId))) {
        //    saveRoute.clear();
        //}
    }

    private class SaveRouteTask extends GenericTask {

        private String[] details;

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            String message;
            if (details != null) {
                message = Locale.getMessage(R.string.Routes_Recording_Saved, details[0], details[1]);
            } else {
                message = Locale.getMessage(R.string.Routes_NoRoute);
            }
            intents.showInfoToast(message);
        }

        @Override
        protected String doInBackground(String... fileData) {
            super.doInBackground(fileData);
            RouteRecorder routeRecorder = ConfigurationManager.getInstance().getRouteRecorder();
            if (routeRecorder != null) {
                details = routeRecorder.saveRoute();
            }
            return null;
        }
    }

    public void executeClearCacheTask(String message) {
        intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
        ClearCacheTask clearCache = new ClearCacheTask();
        String notificationId = createNotification(-1, message, message, true);
        clearCache.execute("", notificationId);
        //if (!AsyncTaskExecutor.execute(clearCache, activity, "", Integer.toString(notificationId))) {
        //    clearCache.clear();
        //}
    }

    private class ClearCacheTask extends GenericTask {

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            intents.showInfoToast(Locale.getMessage(R.string.Maps_CacheCleared, res));
        }

        @Override
        protected String doInBackground(String... fileData) {
            super.doInBackground(fileData);
            return Integer.toString(PersistenceManagerFactory.getPersistenceManagerInstance().deleteTilesCache());
        }
    }

    //CheckIn tasks
    public void executeSocialCheckInTask(String message, int icon, boolean silent, String layer, String venueid, String name) {
     String notificationId = "-1";
     if (!silent) {
            intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
            notificationId = createNotification(icon, message, message, true);
        }
        SocialCheckInTask checkInTask = new SocialCheckInTask();
        checkInTask.execute("", notificationId, Boolean.toString(silent), layer, venueid, name);
    }

    private class SocialCheckInTask extends GenericTask {

        private boolean silent = false;
                
        @Override
        protected String doInBackground(String... fileData) {
        	super.doInBackground(fileData);
            silent = Boolean.parseBoolean(fileData[2]);
            return socialCheckin(fileData[3], fileData[4], fileData[5]);
        }

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            if (! silent) {
             intents.showInfoToast(res);
            } else {
             LoggerUtils.debug(res);
            }
        }
    }
    
    private String socialCheckin(String layer, String venueid, String name) {
    	String msg;// = Locale.getMessage(R.string.Social_Checkin_error, selectedLandmark.getName());
		
		try {
			if (layer.equals(Commons.FOURSQUARE_LAYER) || layer.equals(Commons.FOURSQUARE_MERCHANT_LAYER)) {
				msg = OAuthServiceFactory.getSocialUtils(Commons.FOURSQUARE).checkin(venueid, name);
			} else if (layer.equals(Commons.FACEBOOK_LAYER)) {
				msg = OAuthServiceFactory.getSocialUtils(Commons.FACEBOOK).checkin(venueid, name);
			} else if (layer.equals(Commons.GOOGLE_PLACES_LAYER)) {
				msg = OAuthServiceFactory.getSocialUtils(Commons.GOOGLE).checkin(venueid, name);
			} else {
				msg = Locale.getMessage(R.string.Checkin_layer_error, layer);
			}
		} catch (Throwable t) {
			msg = Locale.getMessage(R.string.Checkin_layer_error, layer);
		}
		return msg;
    }

    public void executeQrCodeCheckInTask(String checkinLandmarkCode, String qrformat, String message) {
        //intents.showInfoToast(Locale.getMessage("Background.task.executed", new Object[]{message}));
        intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
        QrCodeCheckInTask checkInTask = new QrCodeCheckInTask();
        String notificationId = createNotification(-1, message, message, true);
        checkInTask.execute("", notificationId, checkinLandmarkCode, qrformat);
        //if (!AsyncTaskExecutor.execute(checkInTask, activity, "", Integer.toString(notificationId), checkinLandmarkCode, qrformat)) {
        //    checkInTask.clear();
        //}
    }

    private class QrCodeCheckInTask extends GenericTask {

        private String checkinLandmarkCode, qrformat;

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            intents.showInfoToast(res);
        }

        @Override
        protected String doInBackground(String... fileData) {
            super.doInBackground(fileData);
            checkinLandmarkCode = fileData[2];
            qrformat = fileData[3];
            if (checkinLandmarkCode.matches("[a-zA-z0-9]*")) {
                if (qrformat.equals("QR_CODE")) {
                    return GMSUtils.checkin(GMSUtils.QRCODE_CHECKIN, checkinLandmarkCode, null);
                } else {
                    return Locale.getMessage(R.string.Social_Checkin_wrong_key_1, qrformat);
                }
            } else {
                return Locale.getMessage(R.string.Social_Checkin_wrong_key_0);
            }
        }
    }

    public void executeLocationCheckInTask(int icon, String checkinLandmarkCode, String message, String name, boolean silent) {
        String notificationId = "-1";
        if (!silent) {
               intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
               notificationId = createNotification(icon, message, message, true);
           }
           LocationCheckInTask checkInTask = new LocationCheckInTask();
           checkInTask.execute("", notificationId, checkinLandmarkCode, name, Boolean.toString(silent));
     }

     private class LocationCheckInTask extends GenericTask {

           private boolean silent = false;
           
           @Override
           protected void onPostExecute(String res) {
               super.onPostExecute(res);
               if (! silent) {
                   intents.showInfoToast(res);
               }
           }

           @Override
           protected String doInBackground(String... checkinData) {
               super.doInBackground(checkinData);
               String checkinLandmarkCode = checkinData[2];
               String name = checkinData[3];
               silent = Boolean.parseBoolean(checkinData[4]);
               return GMSUtils.checkin(GMSUtils.LOCATION_CHECKIN, checkinLandmarkCode, name);
           }
    }

       

    public void executeSocialSendMyLocationTask(boolean executeInTask) {
        if (executeInTask) {
        	String message = Locale.getMessage(R.string.Task_Background_sendMyLoc);
        	//intents.showInfoToast(Locale.getMessage("Background.task.executed", new Object[]{message}));
        	intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
        	SocialSendMyLocationTask socialSendMyLocationTask = new SocialSendMyLocationTask();
        	String notificationId = createNotification(-1, message, message, true);
        	socialSendMyLocationTask.execute("", notificationId);
        } else {
        	sendMyPos();
        }
    }

    private class SocialSendMyLocationTask extends GenericTask {

    	@Override
        protected String doInBackground(String... data) {
            super.doInBackground(data);
            return sendMyPos();
        }

        @Override
        protected void onPostExecute(String msg) {
            super.onPostExecute(msg);
            intents.showInfoToast(msg);
        }
    }

    public void executeSendBlogeoPostTask(String name, String desc, String validityDate, String message) {
        //intents.showInfoToast(Locale.getMessage("Background.task.executed", new Object[]{message}));
        intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
        SendBlogeoPostTask sendBlogeoPostTask = new SendBlogeoPostTask();
        String notificationId = createNotification(-1, message, message, true);
        sendBlogeoPostTask.execute("", notificationId, name, desc, validityDate);
        //if (!AsyncTaskExecutor.execute(sendBlogeoPostTask, activity, "", Integer.toString(notificationId), name, desc, validityDate)) {
        //   sendBlogeoPostTask.clear();
        //}
    }

    private class SendBlogeoPostTask extends GenericTask {

        @Override
        protected String doInBackground(String... landmarkData) {
            super.doInBackground(landmarkData);
            String name = landmarkData[2];
            String desc = landmarkData[3];
            String validityDate = landmarkData[4];
            Location lastPosition = ConfigurationManager.getInstance().getLocation();
            ExtendedLandmark landmark = landmarkManager.createLandmark(lastPosition.getLatitude(), lastPosition.getLongitude(), (float) lastPosition.getAltitude(), name, desc, Commons.LM_SERVER_LAYER);
            String msg = landmarkManager.persistToServer(landmark, "Social", validityDate, ConfigurationManager.getUserManager().getLoggedInUsername());

            if (msg == null) {
                String tmp = sendSocialNotification(landmark, Commons.BLOGEO);
                msg = Locale.getMessage(R.string.Location_sent);
                if (tmp != null) {
                	msg = msg + ",\n" + tmp;
                }
            } else {
                msg = Locale.getMessage(R.string.Location_send_error, msg);
            }
            return msg;
        }

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            intents.showInfoToast(res);
        }
    }

    public void executeAddLandmarkTask(String name, String desc, String layer, String message, double lat, double lng, boolean addVenue, String fsCategory) {
        //System.out.println(name + " " + desc + " " + layer + " " + addVenue);
        intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
        AddLandmarkTask addLandmarkTask = new AddLandmarkTask(lat, lng);
        String notificationId = createNotification(-1, message, message, true);
        addLandmarkTask.execute("", notificationId, name, desc, layer);
        //if (!AsyncTaskExecutor.execute(addLandmarkTask, activity, "", Integer.toString(notificationId), name, desc, layer)) {
        //    addLandmarkTask.clear();
        //}

        if (addVenue) {
            AddVenueTask addVenueTask = new AddVenueTask(lat, lng);
            String msg = Locale.getMessage(R.string.addVenue);
            addVenueTask.execute("", createNotification(R.drawable.foursquare, msg, msg, true), name, desc, fsCategory);
            //if (!AsyncTaskExecutor.execute(addVenueTask, activity, "", Integer.toString(notificationVId), name, desc, fsCategory)) {
            //    addVenueTask.clear();
            //}
        }
    }

    private class AddLandmarkTask extends GenericTask {

        private double lat, lng;

        public AddLandmarkTask(double lat, double lng) {
            super();
            this.lat = lat;
            this.lng = lng;
        }

        @Override
        protected String doInBackground(String... landmarkData) {
            super.doInBackground(landmarkData);
            String name = landmarkData[2];
            String desc = landmarkData[3];
            String layer = landmarkData[4];
            int persist = ConfigurationManager.getInstance().getInt(ConfigurationManager.PERSIST_METHOD);

            if (persist == ConfigurationManager.PERSIST_LOCAL) {
                layer = Commons.LOCAL_LAYER;
            }

            ExtendedLandmark landmark = landmarkManager.createLandmark(lat, lng, 0.0f, name, desc, layer);
            String msg = landmarkManager.addLandmark(landmark);

            if (msg == null) {
            	String tmp = null;
                if (!StringUtils.equals(layer,Commons.LOCAL_LAYER)) {
                    tmp = sendSocialNotification(landmark, Commons.LANDMARK);
                }
                msg = Locale.getMessage(R.string.Location_sent);
                if (tmp != null) {
                	msg = msg + ",\n" + tmp;
                }
            } else {
                msg = Locale.getMessage(R.string.Location_send_error, msg);
            }
            return msg;
        }

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            intents.showInfoToast(res);
        }
    }

    //Add venue to FS
    private class AddVenueTask extends GenericTask {

        private double lat, lng;

        public AddVenueTask(double lat, double lng) {
            super();
            this.lat = lat;
            this.lng = lng;
        }

        @Override
        protected String doInBackground(String... landmarkData) {
            super.doInBackground(landmarkData);
            String name = landmarkData[2];
            String desc = landmarkData[3];
            String layer = landmarkData[4];
            ISocialUtils fsUtils = OAuthServiceFactory.getSocialUtils(Commons.FOURSQUARE);
            String message = fsUtils.addPlace(name, desc, layer, lat, lng);
            return message;
        }

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            intents.showInfoToast(res);
        }
    }

    public void executeSendCommentTask(String service, String placeId, String commentText, String name) {
        //intents.showInfoToast(Locale.getMessage("Background.task.executed", new Object[]{message}));
        String message = Locale.getMessage(R.string.Task_Background_send_comment);
        intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
        SendCommentTask sendCommentTask = new SendCommentTask();
        String notificationId = createNotification(-1, message, message, true);
        sendCommentTask.execute("", notificationId, service, placeId, commentText, name);
        //if (!AsyncTaskExecutor.execute(sendCommentTask, activity, "", Integer.toString(notificationId), service, placeId, commentText, name)) {
        //    sendCommentTask.clear();
        //}
    }

    private class SendCommentTask extends GenericTask {

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            intents.showInfoToast(res);
        }

        @Override
        protected String doInBackground(String... commentData) {
            super.doInBackground(commentData);
            String service = commentData[2];
            String placeId = commentData[3];
            String commentText = commentData[4];
            String name = commentData[5];
            if (service.equals(Commons.FACEBOOK) || service.equals(Commons.FOURSQUARE)) {
                return OAuthServiceFactory.getSocialUtils(service).sendComment(placeId, commentText, name);
            } else if (service.equals(Commons.GMS_WORLD)) {
                return GMSUtils.sendComment(placeId, commentText);
            } else {
                return null;
            }
        }
    }

    public void executeLoginTask(String login, String password) {
        String message = Locale.getMessage(R.string.Task_Background_login);
        intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
        LoginTask loginTask = new LoginTask();
        String notificationId = createNotification(-1, message, message, true);
        loginTask.execute("", notificationId, login, password);
    }

    private class LoginTask extends GenericTask {

        @Override
        protected void onPostExecute(String message) {
            super.onPostExecute(message);
            if (message != null) {
                intents.showInfoToast(message);
            } else {
                intents.showInfoToast(Locale.getMessage(R.string.Authn_success));
            }
        }

        @Override
        protected String doInBackground(String... loginData) {
            super.doInBackground(loginData);
            return GMSUtils.loginAction(loginData[2], loginData[3]);
        }
    }

    public void executeClearStatsTask(String message) {
        intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
        ClearStatsTask clearTasks = new ClearStatsTask();
        String notificationId = createNotification(-1, message, message, true);
        clearTasks.execute("", notificationId);
    }

    private class ClearStatsTask extends GenericTask {

        @Override
        protected void onPostExecute(String res) {
            super.onPostExecute(res);
            intents.showInfoToast(res);
        }

        @Override
        protected String doInBackground(String... checkinData) {
            super.doInBackground(checkinData);
            CategoriesManager cm = (CategoriesManager) ConfigurationManager.getInstance().getObject(ConfigurationManager.DEAL_CATEGORIES, CategoriesManager.class);
            if (cm != null) {
                cm.clearAllStats();
            }
            return Locale.getMessage(R.string.confirmDealPreferences);
        }
    }

    public void executeDealCategoryLoaderTask(CategoriesManager cm, boolean initStats) {
        new DealCategoryLoaderTask(cm, initStats).execute();
    }

    private class DealCategoryLoaderTask extends GMSAsyncTask<Void, Void, Void> {

        private CategoriesManager cm;
        private boolean initStats;

        public DealCategoryLoaderTask(CategoriesManager cm, boolean initStats) {
            super(1);
            this.cm = cm;
            this.initStats = initStats;
        }

        @Override
        public void onPreExecute() {
            tasksInProgress.put(-1, this);
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            FileManager fm = PersistenceManagerFactory.getFileManager();

            if (!isCancelled()) {
                String catjson = fm.readJsonFile(R.raw.category, ConfigurationManager.getInstance().getContext());
                cm.setCategories(CategoryJsonParser.parserCategoryJson(catjson, -1, this));
            }

            if (!isCancelled()) {
                String subcatjson = fm.readJsonFile(R.raw.subcategory, ConfigurationManager.getInstance().getContext());
                cm.setSubcategories(CategoryJsonParser.parserCategoryJson(subcatjson, -1, this), initStats);
            }

            if (!isCancelled()) {
                cm.setInitialized(true);
            }

            return null;
        }
    }

    public void executeGetTokenTask() {
        new GetTokenTask().execute();
    }

    private class GetTokenTask extends GMSAsyncTask<Void, Void, Boolean> {

    	public GetTokenTask() {
    		super(10);
    	}
    	
		@Override
		protected Boolean doInBackground(Void... params) {
			ConfigurationManager.getUserManager().verifyToken();
			return null;
		}
    	
    }
    
    public void executeNewVersionCheckTask() {
        new NewVersionCheckTask().execute();
    }
    
    private class NewVersionCheckTask extends GMSAsyncTask<Void, Void, Boolean> {

        public NewVersionCheckTask() {
            super(10);
        }

        @Override
        protected void onPostExecute(Boolean status) {
            if (status) {
                try {
                    DialogManager dialogManager = new DialogManager((Activity)activity, intents, null,
                            landmarkManager, null, null);
                    dialogManager.showAlertDialog(AlertDialogBuilder.NEW_VERSION_DIALOG, null, null);
                } catch (Throwable e) {
                    LoggerUtils.error("NewVersionCheckTask.onPostExceute() exception:", e);
                }
            }
        }

        @Override
        protected Boolean doInBackground(Void... arg0) {
            return intents.isNewerVersionAvailable();
        }
    }

    public void executeUploadImageTask(double lat, double lng, byte[] file, boolean notify) {
    	if (notify) {
    		String message = Locale.getMessage(R.string.shareScreenshot);
    		intents.showInfoToast(Locale.getMessage(R.string.Task_started, message));
    	}
    	if (file != null && ConfigurationManager.getInstance().isNetworkModeAccepted()) {
            //loading time & sdk version & number of landmarks
            long loadingTime = 0; 
            Long l = (Long) ConfigurationManager.getInstance().removeObject("LAYERS_LOADING_TIME_SEC", Long.class);
            if (l != null) {
                 loadingTime = l.longValue();
            }
            int version = OsUtil.getSdkVersion();
            int numOfLandmarks = landmarkManager.getAllLayersSize();
            int limit = ConfigurationManager.getInstance().getInt(ConfigurationManager.LANDMARKS_PER_LAYER, 30);
            String filename = "screenshot_time_" + loadingTime + "sec_sdk_v" + version
                    + "_num_" + numOfLandmarks + "_l_" + limit + ".jpg";
            new UploadImageTask(file, filename).execute(lat, lng);
        } else {
    		LoggerUtils.debug("Skipping image upload due to lack of wi-fi...");
    	}
    }

    private class UploadImageTask extends GMSAsyncTask<Double, Void, Void> {

        private byte[] file;
        private String filename;

        public UploadImageTask(byte[] file, String filename) {
            super(10);
            this.file = file;
            this.filename = filename;
        }

        @Override
        protected Void doInBackground(Double... coords) {
            HttpUtils utils = new HttpUtils();
            try {
                String url = ConfigurationManager.getInstance().getServerUrl() + "imageUpload";
                utils.uploadFile(url, true, coords[0], coords[1], file, filename);
            } catch (Exception e) {
                LoggerUtils.error("Intents.takeScreenshot() exception: ", e);
            } finally {
                try {
                    if (utils != null) {
                        utils.close();
                    }
                } catch (Exception e) {
                }
            }
            return null;
        }
    }
}
