/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jstakun.gms.android.google.maps;

import android.graphics.Point;
import android.graphics.Rect;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;
import com.jstakun.gms.android.utils.ProjectionInterface;

/**
 *
 * @author jstakun
 */
public class GoogleLandmarkProjection implements ProjectionInterface {

    private Projection proj;
    private final Point p = new Point();
    private final Rect currentMapBoundsRect = new Rect();
    
    public GoogleLandmarkProjection(MapView mapView) {
        proj = mapView.getProjection();
        mapView.getDrawingRect(currentMapBoundsRect);
    }
    
    public void toPixels(int latE6, int lonE6, Point point) {
        GeoPoint g = new GeoPoint(latE6, lonE6);
        proj.toPixels(g, point);
    }

    public int[] fromPixels(int x, int y) {
        GeoPoint g = proj.fromPixels(x, y);
        return new int[]{g.getLatitudeE6(), g.getLongitudeE6()};
    }

    public boolean isVisible(int latE6, int lonE6) {
        boolean isVisible = false;
        
        try {
            toPixels(latE6, lonE6, p);
            isVisible = isVisible(p);
        } catch (Throwable t) {
        } 

        return isVisible;
    }
    
    public boolean isVisible(Point p) {
        //return (p.x >= 0 && p.x <= width && p.y <= height && p.y >= 0);
        return currentMapBoundsRect.contains(p.x, p.y);
    }
}
