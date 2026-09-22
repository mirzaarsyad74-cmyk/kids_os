package com.kids.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Intercepts new application installations on the device.
 * Newly installed apps are put into a pending approval list
 * and hidden from the kids launcher until approved in Parent Zone.
 */
public class AppInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            return;
        }

        boolean isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        if (isReplacing) {
            return; // Just an update to an existing app, ignore
        }

        Uri data = intent.getData();
        if (data == null) return;
        String packageName = data.getSchemeSpecificPart();
        if (packageName == null || packageName.equals(context.getPackageName())) {
            return;
        }

        PreferencesManager prefs = new PreferencesManager(context);
        prefs.addPendingApprovalPackage(packageName);

        // Notify with a toast
        Toast.makeText(context, "🛡️ New app installed: " + packageName + " (Requires Parent Approval in Parent Zone)", Toast.LENGTH_LONG).show();
    }
}
