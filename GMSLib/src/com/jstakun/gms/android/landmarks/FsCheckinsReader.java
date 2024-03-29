/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jstakun.gms.android.landmarks;

import com.jstakun.gms.android.config.Commons;
import com.jstakun.gms.android.config.ConfigurationManager;
import com.jstakun.gms.android.social.ISocialUtils;
import com.jstakun.gms.android.social.OAuthServiceFactory;
import com.jstakun.gms.android.utils.GMSAsyncTask;
import java.util.List;
import java.util.Locale;

import org.apache.http.message.BasicNameValuePair;

/**
 *
 * @author jstakun
 */
public class FsCheckinsReader extends AbstractSerialReader {

    private boolean hasToken = false;
	
	@Override
	protected void init(double latitude, double longitude, int zoom, int width, int height) {
		super.init(latitude, longitude, zoom, width, height);
		params.add(new BasicNameValuePair("lang", Locale.getDefault().getLanguage()));
		if (ConfigurationManager.getInstance().isOn(ConfigurationManager.FS_AUTH_STATUS)) {
			ISocialUtils fsUtils = OAuthServiceFactory.getSocialUtils(Commons.FOURSQUARE);
			String token = fsUtils.getAccessToken().getToken();
			if (token != null) {
				params.add(new BasicNameValuePair("token", token));
				hasToken = true;
			}	
		}
	}
	
	@Override
	public String readRemoteLayer(List<ExtendedLandmark> landmarks, double latitude, double longitude, int zoom, int width, int height, String layer, GMSAsyncTask<?, ? ,?> task) {
		String errorMessage = null;
        
		if (ConfigurationManager.getInstance().isOn(ConfigurationManager.FS_AUTH_STATUS)) {
			init(latitude, longitude, zoom, width, height);
			if (hasToken) {
				errorMessage = parser.parse(getUrls(), 0, params, landmarks, task, true, Commons.FOURSQUARE);
			}
		}
		
        return errorMessage;
	}

	@Override
	protected String[] getUrls() {
		return new String[] { ConfigurationManager.getInstance().getSecuredServerUrl() + getUri() };
	}
	
	@Override
	protected String getUri() {
		return "fsCheckins";
	}
}
