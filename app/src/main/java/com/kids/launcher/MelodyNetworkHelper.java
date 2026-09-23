package com.kids.launcher;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Resilient Wi-Fi and Network Helper for Kids OS.
 * - Handles Wi-Fi toggling with automatic Airplane mode override.
 * - Multi-tiered fallback (WifiManager, IWifiManager reflection, Settings.Global).
 * - Real-time state detection (DISABLED, DISABLING, ENABLED, ENABLING).
 */
public class MelodyNetworkHelper {

    private static final String TAG = "MelodyNetworkHelper";

    public static int getWifiState(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                return wm.getWifiState();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking wifi state", e);
        }
        return WifiManager.WIFI_STATE_UNKNOWN;
    }

    public static boolean isWifiEnabled(Context context) {
        int state = getWifiState(context);
        return state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_ENABLING;
    }

    /**
     * Toggles or sets Wi-Fi state with full support for Android 8.1 MTK permissions.
     */
    public static boolean setWifiEnabled(Context context, boolean enable) {
        Context appCtx = context.getApplicationContext();
        Log.i(TAG, "setWifiEnabled called with enable=" + enable);

        // 1. Ensure Airplane mode is disabled in system server via ConnectivityManager system API
        try {
            ConnectivityManager cm = (ConnectivityManager) appCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Method setAirplane = cm.getClass().getMethod("setAirplaneMode", boolean.class);
                setAirplane.invoke(cm, false);
                Log.i(TAG, "Invoked ConnectivityManager.setAirplaneMode(false)");
            }
        } catch (Exception e) {
            Log.w(TAG, "ConnectivityManager.setAirplaneMode reflection failed", e);
        }
        try {
            Settings.Global.putInt(appCtx.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
        } catch (Exception ignored) {}

        // 2. Primary mechanism: WifiManager API
        boolean success = false;
        try {
            WifiManager wm = (WifiManager) appCtx.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                success = wm.setWifiEnabled(enable);
                Log.i(TAG, "WifiManager.setWifiEnabled(" + enable + ") returned: " + success);
            }
        } catch (Exception e) {
            Log.e(TAG, "WifiManager.setWifiEnabled failed", e);
        }

        // 3. Fallback: Direct IWifiManager reflection
        if (!success) {
            try {
                IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "wifi");
                if (b != null) {
                    Object iwm = Class.forName("android.net.wifi.IWifiManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
                    try {
                        Method m = iwm.getClass().getMethod("setWifiEnabled", String.class, boolean.class);
                        Object res = m.invoke(iwm, appCtx.getPackageName(), enable);
                        if (res instanceof Boolean) success = (Boolean) res;
                    } catch (NoSuchMethodException nsme) {
                        Method m = iwm.getClass().getMethod("setWifiEnabled", boolean.class);
                        Object res = m.invoke(iwm, enable);
                        if (res instanceof Boolean) success = (Boolean) res;
                    }
                    Log.i(TAG, "IWifiManager reflection setWifiEnabled(" + enable + ") result: " + success);
                }
            } catch (Exception e) {
                Log.w(TAG, "IWifiManager reflection fallback failed", e);
            }
        }

        // 4. Fallback: Settings.Global WIFI_ON synchronization
        try {
            Settings.Global.putInt(appCtx.getContentResolver(), Settings.Global.WIFI_ON, enable ? 1 : 0);
        } catch (Exception ignored) {}

        return success;
    }
}
