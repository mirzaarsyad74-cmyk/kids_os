package com.kids.launcher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

public class LocationHelper {

    public static class LocationInfo {
        public double latitude;
        public double longitude;
        public float accuracy;
        public long timestamp;

        public LocationInfo(double latitude, double longitude, float accuracy, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracy = accuracy;
            this.timestamp = timestamp;
        }
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static LocationInfo getLastKnownLocation(Context context) {
        if (!hasLocationPermission(context)) return null;

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;

        Location best = null;
        try {
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location passive = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

            if (gps != null) best = gps;
            if (net != null && (best == null || net.getTime() > best.getTime())) best = net;
            if (passive != null && (best == null || passive.getTime() > best.getTime())) best = passive;
        } catch (SecurityException ignored) {}

        if (best != null) {
            return new LocationInfo(best.getLatitude(), best.getLongitude(), best.getAccuracy(), best.getTime());
        }
        return null;
    }

    public static void openInMaps(Context context, double lat, double lon) {
        try {
            Uri uri = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(Melody+Kids+Tablet)");
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, uri);
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mapIntent);
        } catch (Exception e) {
            try {
                Uri webUri = Uri.parse("https://www.google.com/maps?q=" + lat + "," + lon);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
            } catch (Exception ex) {
                Toast.makeText(context, "Could not open map", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
