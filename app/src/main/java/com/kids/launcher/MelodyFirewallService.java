package com.kids.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.Set;

/**
 * MelodyFirewallService
 * On-device local VPN firewall for Kids OS.
 * Operates 100% locally with zero external network routing.
 * When apps are marked as blocked by parents, their sockets are blackholed into
 * this dummy local loopback tunnel, completely cutting off Wi-Fi/Internet access,
 * preventing online tracking, and blocking in-game ads for a clean offline experience.
 */
public class MelodyFirewallService extends VpnService {
    private static final String TAG = "MelodyFirewall";
    private static final String CHANNEL_ID = "melody_firewall_channel";
    private static final int NOTIF_ID = 8889;

    private ParcelFileDescriptor vpnInterface = null;
    private static volatile boolean isRunning = false;

    public static boolean isRunning() {
        return isRunning;
    }

    public static void startOrUpdate(Context context) {
        if (context == null) return;
        PreferencesManager prefs = PreferencesManager.getInstance(context);
        if (!prefs.isFirewallEnabled()) {
            stop(context);
            return;
        }

        Set<String> blocked = prefs.getFirewallBlockedPackages();
        if (blocked.isEmpty()) {
            stop(context);
            return;
        }

        Intent intent = new Intent(context, MelodyFirewallService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot start firewall service: " + e.getMessage());
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, MelodyFirewallService.class);
            intent.setAction("ACTION_STOP");
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "ACTION_STOP".equals(intent.getAction())) {
            stopVpn();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, createNotification());
        setupFirewall();
        return START_STICKY;
    }

    private synchronized void setupFirewall() {
        PreferencesManager prefs = PreferencesManager.getInstance(this);
        Set<String> blocked = prefs.getFirewallBlockedPackages();

        if (blocked.isEmpty() || !prefs.isFirewallEnabled()) {
            stopVpn();
            stopForeground(true);
            stopSelf();
            return;
        }

        try {
            stopVpn();

            Builder builder = new Builder();
            builder.setSession("Kids OS Firewall 🛡️");
            // Non-routable loopback dummy subnet
            builder.addAddress("10.120.0.1", 32);
            builder.addRoute("0.0.0.0", 0);

            int count = 0;
            for (String pkg : blocked) {
                try {
                    builder.addAllowedApplication(pkg);
                    count++;
                } catch (Exception e) {
                    Log.w(TAG, "Cannot route package into blackhole: " + pkg + ": " + e.getMessage());
                }
            }

            if (count > 0) {
                vpnInterface = builder.establish();
                isRunning = true;
                Log.i(TAG, "Kids OS Firewall active! Blackholing network for " + count + " apps.");
            } else {
                stopVpn();
                stopForeground(true);
                stopSelf();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish firewall interface: " + e.getMessage(), e);
        }
    }

    private synchronized void stopVpn() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException ignored) {}
            vpnInterface = null;
        }
        isRunning = false;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "Kids OS Firewall",
                    NotificationManager.IMPORTANCE_LOW
            );
            chan.setDescription("App Internet Protection");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(chan);
            }
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🛡️ Kids OS Internet Firewall")
                .setContentText("Protecting internet access for selected apps 🌸")
                .setSmallIcon(R.drawable.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
