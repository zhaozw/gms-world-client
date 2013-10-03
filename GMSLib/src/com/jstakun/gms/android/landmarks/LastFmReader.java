/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.jstakun.gms.android.landmarks;

import com.jstakun.gms.android.config.ConfigurationManager;
import com.jstakun.gms.android.utils.GMSAsyncTask;

import java.util.List;

/**
 *
 * @author jstakun
 */
public class LastFmReader extends AbstractSerialReader {

    /*@Override
    public String readRemoteLayer(List<ExtendedLandmark> landmarks, double latitude, double longitude, int zoom, int width, int height, String layer, GMSAsyncTask<?, ? ,?> task) {
        init(latitude, longitude, zoom, width, height);
        String url = ConfigurationManager.getInstance().getServicesUrl() + "lastfmProvider?latitude=" + coords[0] + 
                "&longitude=" + coords[1] + "&radius=" + radius + "&version=4" + "&limit=" + limit + "&display=" + display;
        return parser.parse(url, landmarks, Commons.LASTFM_LAYER, null, -1, -1, task, true, limit);
    }*/

	@Override
	protected String readLayer(List<ExtendedLandmark> landmarks, double latitude, double longitude, int zoom, int width, int height, String layer, GMSAsyncTask<?, ?, ?> task) {
		String url = ConfigurationManager.getInstance().getServicesUrl() + "lastfmProvider?latitude=" + coords[0] + 
                "&longitude=" + coords[1] + "&radius=" + radius + "&version=" + SERIAL_VERSION + "&format=bin&limit=" + limit + "&display=" + display;
        
		return parser.parse(url, landmarks, task, true, null);
	}
}
