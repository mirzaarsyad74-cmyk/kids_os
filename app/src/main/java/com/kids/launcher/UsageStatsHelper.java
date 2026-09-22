package com.kids.launcher;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UsageStatsHelper {

    public static class AppUsageInfo implements Comparable<AppUsageInfo> {
        public String packageName;
        public String label;
        public Drawable icon;
        public long totalTimeMs;

        public int getTimeMinutes() {
            return (int) (totalTimeMs / (1000 * 60));
        }

        @Override
        public int compareTo(AppUsageInfo o) {
            return Long.compare(o.totalTimeMs, this.totalTimeMs); // descending
        }
    }

    public static boolean hasUsageStatsPermission(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName()
            );
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static void openUsageAccessSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {}
    }

    public static List<AppUsageInfo> getDailyUsageStats(Context context) {
        List<AppUsageInfo> result = new ArrayList<>();
        if (!hasUsageStatsPermission(context)) {
            return result;
        }

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return result;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        Map<String, UsageStats> stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
        if (stats == null || stats.isEmpty()) return result;

        PackageManager pm = context.getPackageManager();

        for (Map.Entry<String, UsageStats> entry : stats.entrySet()) {
            String pkg = entry.getKey();
            UsageStats us = entry.getValue();
            long totalMs = us.getTotalTimeInForeground();

            if (totalMs < 10000) continue; // ignore less than 10 seconds
            if ("com.android.systemui".equals(pkg) || context.getPackageName().equals(pkg)) continue;

            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                AppUsageInfo info = new AppUsageInfo();
                info.packageName = pkg;
                info.label = pm.getApplicationLabel(ai).toString();
                info.icon = pm.getApplicationIcon(ai);
                info.totalTimeMs = totalMs;
                result.add(info);
            } catch (Exception ignored) {}
        }

        Collections.sort(result);
        return result;
    }
}
