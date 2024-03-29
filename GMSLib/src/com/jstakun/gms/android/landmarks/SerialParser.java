package com.jstakun.gms.android.landmarks;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.jstakun.gms.android.config.ConfigurationManager;
import com.jstakun.gms.android.social.ISocialUtils;
import com.jstakun.gms.android.social.OAuthServiceFactory;
import com.jstakun.gms.android.utils.GMSAsyncTask;
import com.jstakun.gms.android.utils.HttpUtils;
import com.jstakun.gms.android.utils.LoggerUtils;

public class SerialParser {

	private HttpUtils utils;
	
	protected SerialParser() {
		utils = new HttpUtils();
	}
	
	protected void close() {
		try {
            if (utils != null) {
                utils.close();
            }
        } catch (IOException ex) {
            LoggerUtils.debug("SerialParser.close() exception: ", ex);
        }
    }
	
    protected String parse(String[] urls, int urlIndex, List<NameValuePair> params, List<ExtendedLandmark> landmarks, GMSAsyncTask<?,?,?> task, boolean close, String socialService) { 	
        String errorMessage = null;
        LandmarkManager landmarkManager = ConfigurationManager.getInstance().getLandmarkManager();
        
        try {
        	URI uri = new URI(urls[urlIndex]);
        	if (!task.isCancelled()) {
        		List<ExtendedLandmark> received = utils.loadLandmarkList(uri, params, true, "deflate");
        		if (!received.isEmpty()) {
        			if (landmarks.isEmpty()) {
        				//System.out.println(url + " addAll --------------------------------------");
        				landmarks.addAll(received);
        				if (landmarkManager != null) {
        					landmarkManager.addLandmarkListToDynamicLayer(received);
        				}
        			} else {
        				synchronized (landmarks) {
        					//System.out.println(url + " " + landmarks.size() + " ---------------------------------------");
        					Collection<ExtendedLandmark> filtered = Collections2.filter(received, new ExistsPredicate(landmarks)); 
        					landmarks.addAll(filtered);
        					if (landmarkManager != null) {
        						landmarkManager.addLandmarkListToDynamicLayer(filtered);
        					}
        				}
        			}
        		}
        	}
        } catch (Exception ex) {
            LoggerUtils.error("SerialParser.parse() exception: ", ex);
        } finally {
        	int responseCode = utils.getResponseCode();
        	if (responseCode == HttpStatus.SC_UNAUTHORIZED && socialService != null) {
        		ISocialUtils service = OAuthServiceFactory.getSocialUtils(socialService);
        		if (service != null) {
        			service.logout();
        		}
        	} else if (responseCode == HttpStatus.SC_SERVICE_UNAVAILABLE && urlIndex+1 < urls.length) {
        		return parse(urls, urlIndex+1, params, landmarks, task, close, socialService);
        	}
        	errorMessage = utils.getResponseCodeErrorMessage();
            if (close) {
                close();
            }
        }
        
        return errorMessage;
    }  
    
    private class ExistsPredicate implements Predicate<ExtendedLandmark> {

    	private List<ExtendedLandmark> source;
    	
    	public ExistsPredicate(List<ExtendedLandmark> source) {
    		this.source = source;
    	}
    	
        public boolean apply(ExtendedLandmark landmark) {
        	return (landmark != null && !source.contains(landmark));
        }
    }
}   