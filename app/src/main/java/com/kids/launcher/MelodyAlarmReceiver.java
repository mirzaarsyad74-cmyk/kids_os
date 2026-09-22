package com.kids.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class MelodyAlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_TRIGGER_ALARM = "com.kids.launcher.ACTION_TRIGGER_ALARM";
    public static final String ACTION_TRIGGER_TIMER = "com.kids.launcher.ACTION_TRIGGER_TIMER";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "kids:melody_alarm_wakelock");
            wakeLock.acquire(10000); // 10 seconds
        }

        Intent alertIntent = new Intent(context, MelodyAlarmAlertActivity.class);
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (ACTION_TRIGGER_TIMER.equals(action)) {
            alertIntent.putExtra(MelodyAlarmAlertActivity.EXTRA_IS_TIMER, true);
            alertIntent.putExtra(MelodyAlarmAlertActivity.EXTRA_TITLE, "Timer Finished! 🎉");
            // Clear background timer status
            PreferencesManager prefs = new PreferencesManager(context);
            prefs.setTimerRunning(false);
            prefs.setTimerEndTime(0);
        } else if (ACTION_TRIGGER_ALARM.equals(action)) {
            alertIntent.putExtra(MelodyAlarmAlertActivity.EXTRA_IS_TIMER, false);
            alertIntent.putExtra(MelodyAlarmAlertActivity.EXTRA_TITLE, intent.getStringExtra(MelodyAlarmAlertActivity.EXTRA_TITLE));
            alertIntent.putExtra(MelodyAlarmAlertActivity.EXTRA_SOUND_TYPE, intent.getStringExtra(MelodyAlarmAlertActivity.EXTRA_SOUND_TYPE));
            alertIntent.putExtra(MelodyAlarmAlertActivity.EXTRA_ALARM_ID, intent.getIntExtra(MelodyAlarmAlertActivity.EXTRA_ALARM_ID, 0));
        }

        try {
            context.startActivity(alertIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
    }
}
