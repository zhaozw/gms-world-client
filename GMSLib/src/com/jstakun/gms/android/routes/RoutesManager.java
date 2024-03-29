/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jstakun.gms.android.routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.jstakun.gms.android.config.Commons;
import com.jstakun.gms.android.config.ConfigurationManager;
import com.jstakun.gms.android.landmarks.ExtendedLandmark;
import com.jstakun.gms.android.landmarks.LandmarkFactory;
import com.jstakun.gms.android.ui.lib.R;
import com.jstakun.gms.android.utils.DateTimeUtils;
import com.jstakun.gms.android.utils.DistanceUtils;
import com.jstakun.gms.android.utils.HttpUtils;
import com.jstakun.gms.android.utils.Locale;
import com.jstakun.gms.android.utils.LoggerUtils;
import com.jstakun.gms.android.utils.MathUtils;
import com.openlapi.QualifiedCoordinates;

/**
 *
 * @author jstakun
 */
public class RoutesManager {

    private static Map<String, List<ExtendedLandmark>> routes = new HashMap<String, List<ExtendedLandmark>>();
    private static Map<String, String> descs = new HashMap<String, String>();
    private double minLat, maxLat, minLon, maxLon;

    public RoutesManager() {
        routes.clear();
        descs.clear();
    }

    public void addRoute(String key, List<ExtendedLandmark> routePoints, String description) {
        routes.put(key, routePoints);
        if (description != null) {
            descs.put(key, description);
        }
    }

    public List<ExtendedLandmark> getRoute(String key) {
        if (routes.containsKey(key)) {
            return routes.get(key);
        } else {
            return Collections.emptyList();
        }
    }

    public void removeRoute(String key) {
        if (routes.containsKey(key)) {
            routes.remove(key);
        }
    }

    public int getCount() {
        return routes.size();
    }

    public Set<String> getRoutes() {
        return routes.keySet();
    }

    public double[] calculateRouteCenter(String routeKey) {
        double coords[] = new double[2];
        List<ExtendedLandmark> routePoints = routes.get(routeKey);
        ExtendedLandmark start = routePoints.get(0);
        minLat = start.getQualifiedCoordinates().getLatitude();
        maxLat = minLat;
        minLon = start.getQualifiedCoordinates().getLongitude();
        maxLon = minLon;

        for (int i = 0; i < routePoints.size(); i++) {
            ExtendedLandmark l = routePoints.get(i);
            double lat = l.getQualifiedCoordinates().getLatitude();
            if (lat > maxLat) {
                maxLat = lat;
            } else if (lat < minLat) {
                minLat = lat;
            }
            double lon = l.getQualifiedCoordinates().getLongitude();
            if (lon > maxLon) {
                maxLon = lon;
            } else if (lon < minLon) {
                minLon = lon;
            }
        }

        coords[0] = (maxLat + minLat) * 0.5;
        coords[1] = (maxLon + minLon) * 0.5;

        return coords;
    }

    public int calculateRouteZoom(Object routeKey) {
        double latDiff = Math.abs(maxLat - minLat);
        double lonDiff = Math.abs(maxLon - minLon);

        int zoom = 0;
        while (zoom < 21) {
            double s = 180 / MathUtils.pow(2, zoom);

            if (s > latDiff && s > lonDiff) {
                zoom++;
            } else {
                break;
            }
        }

        return zoom + 2;
    }

    public void clearRoutesStore() {
        //Set<Entry<String, List<ExtendedLandmark>>> entries = routes.entrySet();
        //Iterator<Entry<String, List<ExtendedLandmark>>> iter = entries.iterator();

        //while (iter.hasNext()) {
        //    iter.next();
        //    iter.remove();
        //}
        routes.clear();
    }

    public boolean containsRoute(String key) {
        return routes.containsKey(key);
    }

    public String loadRouteFromServer(String lat_start, String lng_start, String lat_end, String lng_end, String type, String routeName, boolean saveToFile) {
        List<ExtendedLandmark> routePoints = new ArrayList<ExtendedLandmark>();
        String message = null;
        HttpUtils utils = new HttpUtils();
        String[] desc = null;
        String url = ConfigurationManager.getInstance().getSecuredServicesUrl() + "routeProvider";
        
        try {
        	List<NameValuePair> params = new ArrayList<NameValuePair>();
        	params.add(new BasicNameValuePair("lat_start", lat_start));
        	params.add(new BasicNameValuePair("lng_start", lng_start));
        	params.add(new BasicNameValuePair("lat_end", lat_end));
        	params.add(new BasicNameValuePair("lng_end", lng_end));
        	params.add(new BasicNameValuePair("type", type));
        
        	String username = ConfigurationManager.getUserManager().getLoggedInUsername();
        	if (username == null) {
        		username = "anonymous";
        	}
        	params.add(new BasicNameValuePair("username", username));
        
        	utils.sendPostRequest(url, params, true);
        
        	String jsonResp = utils.getPostResponse();
        	int responseCode = utils.getResponseCode();
        	message = utils.getResponseCodeErrorMessage();
        
        	if (responseCode == HttpStatus.SC_OK && StringUtils.startsWith(jsonResp, "{")) {
        		JSONObject json = new JSONObject(jsonResp);
        		desc = parse(json, routePoints);
        		message = desc[0];
        	} else if (message != null) {
        		message = Locale.getMessage(R.string.Routes_loading_error_1, message);
        	} else {
        		message = Locale.getMessage(R.string.Routes_loading_error_0);
        	}    
        } catch (Exception ex) {
            LoggerUtils.error("RoutesManager.readRouteFromServer() exception", ex);
            message = utils.getResponseCodeErrorMessage();
        } finally {
        	try {
                if (utils != null) {
                    utils.close();
                }
            } catch (IOException ioe) {
            }
        }
        
        if (!routePoints.isEmpty()) {
            String descr = desc[0];
            if (saveToFile) {
            	String[] details = RouteRecorder.saveRoute(routePoints, routeName + ".kml", desc[3], desc[2], desc[1]);
                if (details != null && details.length > 1) {
                    descr = details[1];
                }
            }
            addRoute(routeName, routePoints, descr);
        } else {
            if (StringUtils.isEmpty(message)) {
                message = Locale.getMessage(R.string.Routes_loading_error_0);
            }
        }
        
        return message;
    }

    private String[] parse(JSONObject root, List<ExtendedLandmark> routePoints) throws JSONException {
        String[] response = new String[4];
        int status = root.getInt("status");

        if (status == 0) {
            JSONArray points = root.getJSONArray("route_geometry");

            for (int i = 0; i < points.length(); i++) {
                JSONArray point = points.getJSONArray(i);

                double lat = point.getDouble(0);
                double lon = point.getDouble(1);

                QualifiedCoordinates qc = new QualifiedCoordinates(lat, lon, Float.NaN, Float.NaN, Float.NaN);

                ExtendedLandmark lm = LandmarkFactory.getLandmark("", "", qc, Commons.ROUTES_LAYER, 0);

                routePoints.add(lm);
            }

            JSONObject summary = root.getJSONObject("route_summary");
            double total_dist_km = summary.getInt("total_distance") / 1000.0;
            int total_time = summary.getInt("total_time");

            response[1] = DistanceUtils.formatDistance(total_dist_km); //dist km

            response[2] = DateTimeUtils.convertSecondsToTimeStamp(total_time); //time

            double avgSpeed = (total_dist_km * 3600.0) / (double)total_time;

            response[3] = DistanceUtils.formatSpeed(avgSpeed); //avg speed

            response[0] = Locale.getMessage(R.string.Routes_Server_route_loaded, response[1], response[2]);
        } else {
            response[0] = Locale.getMessage(R.string.Routes_loading_error_1, root.getString("status_message"));
        }

        return response;
    }

    private String getRouteDesc(String key) {
        return descs.get(key);
    }

    public List<ExtendedLandmark> getBoundingRouteLandmarks() {
        List<ExtendedLandmark> routeLandmarks = new ArrayList<ExtendedLandmark>();
        for (Iterator<String> iter = routes.keySet().iterator(); iter.hasNext();) {
            String routeKey = iter.next();
            List<ExtendedLandmark> route = routes.get(routeKey);
            String desc = getRouteDesc(routeKey);
            if (route.size() > 0) {
                ExtendedLandmark el = route.get(0);
                if (desc != null) {
                    el.setDescription(desc);
                }
                el.setName(Locale.getMessage(R.string.Routes_starting_point));
                routeLandmarks.add(route.get(0));
            }
            if (route.size() > 1) {
                ExtendedLandmark el = route.get(route.size() - 1);
                if (desc != null) {
                    el.setDescription(desc);
                }
                el.setName(Locale.getMessage(R.string.Routes_end_point));
                routeLandmarks.add(el);
            }
        }
        return routeLandmarks;
    }
}
