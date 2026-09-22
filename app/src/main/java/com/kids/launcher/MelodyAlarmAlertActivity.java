package com.kids.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MelodyAlarmAlertActivity extends AppCompatActivity {

    public static final String EXTRA_IS_TIMER = "is_timer";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SOUND_TYPE = "sound_type";
    public static final String EXTRA_ALARM_ID = "alarm_id";

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and show over lockscreen / keyguard
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_melody_alarm_alert);

        boolean isTimer = getIntent().getBooleanExtra(EXTRA_IS_TIMER, false);
        String customTitle = getIntent().getStringExtra(EXTRA_TITLE);
        String soundType = getIntent().getStringExtra(EXTRA_SOUND_TYPE);

        TextView tvTitle = findViewById(R.id.tv_alarm_alert_title);
        TextView tvSubtitle = findViewById(R.id.tv_alarm_alert_subtitle);
        TextView tvIcon = findViewById(R.id.tv_alarm_alert_icon);
        Button btnSnooze = findViewById(R.id.btn_alarm_snooze);
        Button btnDismiss = findViewById(R.id.btn_alarm_dismiss);

        if (isTimer) {
            tvIcon.setText("⏳✨");
            tvTitle.setText(customTitle != null ? customTitle : "Time's Up! 🎉");
            tvSubtitle.setText("Your countdown timer has finished! Great job! 🌸");
            btnSnooze.setVisibility(View.GONE);
        } else {
            tvIcon.setText("⏰🌸");
            tvTitle.setText(customTitle != null ? customTitle : "Wake Up, Sunshine! ☀️");
            tvSubtitle.setText("It's time to start your happy day! Have fun and shine! 💖");
            btnSnooze.setVisibility(View.VISIBLE);
        }

        btnDismiss.setOnClickListener(v -> {
            stopAlarm();
            finish();
        });

        btnSnooze.setOnClickListener(v -> {
            stopAlarm();
            // Snooze for 5 minutes
            MelodyClockActivity.scheduleAlarm(this, System.currentTimeMillis() + (5 * 60 * 1000L), false, "Snoozed Alarm 💤");
            finish();
        });

        startRinging(soundType);
    }

    private void startRinging(String soundType) {
        try {
            Uri soundUri = null;
            if ("✨ Sparkle Bell".equals(soundType)) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            } else if ("⏰ Classic Alarm".equals(soundType)) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            } else if ("🎵 Gentle Chime".equals(soundType)) {
                soundUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
            } else if ("☀️ Morning Chime".equals(soundType)) {
                soundUri = android.provider.Settings.System.DEFAULT_RINGTONE_URI;
            } else {
                // "🌸 Sweet Melody" / default
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                if (soundUri == null) {
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                }
            }

            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, soundUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 600, 400, 600, 400};
                vibrator.vibrate(pattern, 1);
            }
        } catch (Exception ignored) {}
    }

    private void stopAlarm() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {}

        try {
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlarm();
    }
}
