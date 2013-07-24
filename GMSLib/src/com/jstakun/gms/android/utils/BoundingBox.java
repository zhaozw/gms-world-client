/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.jstakun.gms.android.utils;

/**
 *
 * @author jstakun
 */
public class BoundingBox {
    public double north;
    public double south;
    public double east;
    public double west;

    public boolean equals(BoundingBox bbox) {
        return (north == bbox.north && south == bbox.south && east == bbox.east && west == bbox.west);
    }

    @Override
    public String toString() {
        return "north: " + north + ",south: " + south + ",east: " + east + ",west: " + west;
    }
}
