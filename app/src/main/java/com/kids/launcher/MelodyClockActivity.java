package com.kids.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated My Melody Clock for Kids.
 * Live Cute Digital Clock, Multi-Alarm with Custom Sounds, Study/Playtime Timer (with background support), and Stopwatch.
 */
public class MelodyClockActivity extends AppCompatActivity {

    private static final int TAB_CLOCK = 0;
    private static final int TAB_ALARM = 1;
    private static final int TAB_TIMER = 2;
    private static final int TAB_STOPWATCH = 3;
    private int currentTab = TAB_CLOCK;

    // Header Tabs
    private TextView tabClock, tabAlarm, tabTimer, tabStopwatch;
    private View viewClock, viewAlarm, viewTimer, viewStopwatch;

    // Clock Views
    private TextView tvClockTimeBig, tvClockAmPm, tvClockDate, tvClockGreeting;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;

    // Multi-Alarm Views & State
    private LinearLayout layoutAlarmsContainer;
    private TextView tvAlarmsCountBadge;
    private TextView tvNoAlarms;
    private final List<MelodyAlarmModel> alarmsList = new ArrayList<>();

    // New Alarm Creator Views & State
    private TextView tvNewAlarmHour, tvNewAlarmMinute, btnNewAlarmAmPm;
    private TextView btnSelectSound, btnPreviewSound, btnAddAlarmSubmit;
    private int newAlarmHour = 7;
    private int newAlarmMinute = 0;
    private String newAlarmLabel = "Wake Up ☀️";
    private int selectedSoundIndex = 0;

    public static final String[] ALARM_SOUNDS = {
            "🌸 Sweet Melody",
            "✨ Sparkle Bell",
            "⏰ Classic Alarm",
            "🎵 Gentle Chime",
            "☀️ Morning Chime"
    };

    private MediaPlayer previewPlayer;
    private final Handler soundPreviewHandler = new Handler(Looper.getMainLooper());

    // Timer Views & State
    private TextView tvTimerDisplay, tvTimerStatus, btnTimerStartPause;
    private int timerTotalSeconds = 300; // 5 min default
    private int timerRemainingSeconds = 300;
    private boolean isTimerRunning = false;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // Stopwatch Views & State
    private TextView tvStopwatchDisplay, btnStopwatchStartPause;
    private long stopwatchStartTime = 0;
    private long stopwatchElapsedBeforePause = 0;
    private boolean isStopwatchRunning = false;
    private final Handler stopwatchHandler = new Handler(Looper.getMainLooper());
    private Runnable stopwatchRunnable;

    private PreferencesManager prefs;
    private ToneGenerator toneGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setWindowUiFlags();
        setContentView(R.layout.activity_melody_clock);

        prefs = new PreferencesManager(this);

        try {
            toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
        } catch (Exception ignored) {}

        initViews();
        setupClockTab();
        setupAlarmTab();
        setupTimerTab();
        setupStopwatchTab();
        switchTab(TAB_CLOCK);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBackgroundTimerOnResume();
        renderAlarmsList();
    }

    private void setWindowUiFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void initViews() {
        findViewById(R.id.btn_clock_back).setOnClickListener(v -> finish());

        tabClock = findViewById(R.id.tab_clock);
        tabAlarm = findViewById(R.id.tab_alarm);
        tabTimer = findViewById(R.id.tab_timer);
        tabStopwatch = findViewById(R.id.tab_stopwatch);

        viewClock = findViewById(R.id.layout_view_clock);
        viewAlarm = findViewById(R.id.layout_view_alarm);
        viewTimer = findViewById(R.id.layout_view_timer);
        viewStopwatch = findViewById(R.id.layout_view_stopwatch);

        tabClock.setOnClickListener(v -> switchTab(TAB_CLOCK));
        tabAlarm.setOnClickListener(v -> switchTab(TAB_ALARM));
        tabTimer.setOnClickListener(v -> switchTab(TAB_TIMER));
        tabStopwatch.setOnClickListener(v -> switchTab(TAB_STOPWATCH));
    }

    private void switchTab(int tab) {
        currentTab = tab;

        viewClock.setVisibility(tab == TAB_CLOCK ? View.VISIBLE : View.GONE);
        viewAlarm.setVisibility(tab == TAB_ALARM ? View.VISIBLE : View.GONE);
        viewTimer.setVisibility(tab == TAB_TIMER ? View.VISIBLE : View.GONE);
        viewStopwatch.setVisibility(tab == TAB_STOPWATCH ? View.VISIBLE : View.GONE);

        TextView[] tabs = {tabClock, tabAlarm, tabTimer, tabStopwatch};
        for (int i = 0; i < tabs.length; i++) {
            if (i == tab) {
                tabs[i].setBackgroundResource(R.drawable.bg_melody_chip_selected);
                tabs[i].setTextColor(Color.WHITE);
            } else {
                tabs[i].setBackgroundResource(R.drawable.bg_melody_chip_unselected);
                tabs[i].setTextColor(Color.parseColor("#831843"));
            }
        }

        if (tab == TAB_ALARM) {
            renderAlarmsList();
        }
    }

    // ================= CLOCK TAB =================
    private void setupClockTab() {
        tvClockTimeBig = findViewById(R.id.tv_clock_time_big);
        tvClockAmPm = findViewById(R.id.tv_clock_ampm);
        tvClockDate = findViewById(R.id.tv_clock_date);
        tvClockGreeting = findViewById(R.id.tv_clock_greeting);

        clockRunnable = new Runnable() {
            @Override
            public void run() {
                updateClockUi();
                clockHandler.postDelayed(this, 1000);
            }
        };
        clockHandler.post(clockRunnable);
    }

    private void updateClockUi() {
        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();

        SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm:ss", Locale.getDefault());
        SimpleDateFormat ampmFmt = new SimpleDateFormat("a", Locale.getDefault());
        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());

        tvClockTimeBig.setText(timeFmt.format(now));
        tvClockAmPm.setText(ampmFmt.format(now));
        tvClockDate.setText(dateFmt.format(now));

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 12) {
            greeting = "🌸 Good morning, sunshine! Have a sparkly day! ☀️";
        } else if (hour >= 12 && hour < 17) {
            greeting = "🍓 Good afternoon! Time for fun and smiles! 🎀";
        } else if (hour >= 17 && hour < 21) {
            greeting = "🌙 Good evening! Relax and enjoy sweet tunes! ✨";
        } else {
            greeting = "🧸 Sweet dreams and cozy nights! Goodnight! 🌙";
        }
        tvClockGreeting.setText(greeting);
    }

    // ================= MULTI-ALARM & SOUND CHOOSER =================
    private void setupAlarmTab() {
        layoutAlarmsContainer = findViewById(R.id.layout_alarms_container);
        tvAlarmsCountBadge = findViewById(R.id.tv_alarms_count_badge);
        tvNoAlarms = findViewById(R.id.tv_no_alarms);

        tvNewAlarmHour = findViewById(R.id.tv_new_alarm_hour);
        tvNewAlarmMinute = findViewById(R.id.tv_new_alarm_minute);
        btnNewAlarmAmPm = findViewById(R.id.btn_new_alarm_ampm);
        btnSelectSound = findViewById(R.id.btn_select_sound);
        btnPreviewSound = findViewById(R.id.btn_preview_sound);
        btnAddAlarmSubmit = findViewById(R.id.btn_add_alarm_submit);

        // Hour +/-
        findViewById(R.id.btn_new_alarm_hour_plus).setOnClickListener(v -> {
            newAlarmHour = (newAlarmHour + 1) % 24;
            updateNewAlarmUi();
        });
        findViewById(R.id.btn_new_alarm_hour_minus).setOnClickListener(v -> {
            newAlarmHour = (newAlarmHour - 1 + 24) % 24;
            updateNewAlarmUi();
        });

        // Minute +/- (steps of 5)
        findViewById(R.id.btn_new_alarm_minute_plus).setOnClickListener(v -> {
            newAlarmMinute = (newAlarmMinute + 5) % 60;
            updateNewAlarmUi();
        });
        findViewById(R.id.btn_new_alarm_minute_minus).setOnClickListener(v -> {
            newAlarmMinute = (newAlarmMinute - 5 + 60) % 60;
            updateNewAlarmUi();
        });

        // AM/PM Toggle
        btnNewAlarmAmPm.setOnClickListener(v -> {
            newAlarmHour = (newAlarmHour + 12) % 24;
            updateNewAlarmUi();
        });

        // Sound Selection Dialog
        btnSelectSound.setOnClickListener(v -> showSoundPickerDialog());

        // Preview Sound
        btnPreviewSound.setOnClickListener(v -> previewSound(ALARM_SOUNDS[selectedSoundIndex]));

        // Quick Presets
        findViewById(R.id.btn_new_preset_wake).setOnClickListener(v -> {
            newAlarmHour = 7;
            newAlarmMinute = 0;
            newAlarmLabel = "Wake Up ☀️";
            updateNewAlarmUi();
        });
        findViewById(R.id.btn_new_preset_lunch).setOnClickListener(v -> {
            newAlarmHour = 12;
            newAlarmMinute = 30;
            newAlarmLabel = "Lunch Time 🍱";
            updateNewAlarmUi();
        });
        findViewById(R.id.btn_new_preset_sleep).setOnClickListener(v -> {
            newAlarmHour = 20;
            newAlarmMinute = 0;
            newAlarmLabel = "Bedtime 🌙";
            updateNewAlarmUi();
        });

        // Add Alarm Button
        btnAddAlarmSubmit.setOnClickListener(v -> addNewAlarm());

        updateNewAlarmUi();
        renderAlarmsList();
    }

    private void updateNewAlarmUi() {
        int h12 = newAlarmHour % 12;
        if (h12 == 0) h12 = 12;
        boolean isPm = newAlarmHour >= 12;

        tvNewAlarmHour.setText(String.format(Locale.US, "%02d", h12));
        tvNewAlarmMinute.setText(String.format(Locale.US, "%02d", newAlarmMinute));
        btnNewAlarmAmPm.setText(isPm ? "PM" : "AM");
        btnSelectSound.setText("🎵 " + ALARM_SOUNDS[selectedSoundIndex] + " ▾");
    }

    private void showSoundPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🌸 Choose Alarm Sound 🎵");
        builder.setSingleChoiceItems(ALARM_SOUNDS, selectedSoundIndex, (dialog, which) -> {
            selectedSoundIndex = which;
            btnSelectSound.setText("🎵 " + ALARM_SOUNDS[selectedSoundIndex] + " ▾");
            previewSound(ALARM_SOUNDS[selectedSoundIndex]);
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> stopSoundPreview());
        builder.show();
    }

    private void previewSound(String soundName) {
        stopSoundPreview();
        try {
            Uri soundUri = null;
            if ("✨ Sparkle Bell".equals(soundName)) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            } else if ("⏰ Classic Alarm".equals(soundName)) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            } else if ("🎵 Gentle Chime".equals(soundName)) {
                soundUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
            } else if ("☀️ Morning Chime".equals(soundName)) {
                soundUri = android.provider.Settings.System.DEFAULT_RINGTONE_URI;
            } else {
                // "🌸 Sweet Melody"
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                if (soundUri == null) {
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                }
            }

            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            previewPlayer = new MediaPlayer();
            previewPlayer.setDataSource(this, soundUri);
            previewPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            previewPlayer.prepare();
            previewPlayer.start();

            soundPreviewHandler.postDelayed(this::stopSoundPreview, 2500);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopSoundPreview() {
        soundPreviewHandler.removeCallbacksAndMessages(null);
        if (previewPlayer != null) {
            try {
                if (previewPlayer.isPlaying()) previewPlayer.stop();
                previewPlayer.release();
            } catch (Exception ignored) {}
            previewPlayer = null;
        }
    }

    private void addNewAlarm() {
        stopSoundPreview();
        int uniqueId = (int) (System.currentTimeMillis() % 90000) + 1000;
        String sound = ALARM_SOUNDS[selectedSoundIndex];

        MelodyAlarmModel alarm = new MelodyAlarmModel(
                uniqueId,
                newAlarmHour,
                newAlarmMinute,
                newAlarmLabel,
                sound,
                true
        );

        alarmsList.add(alarm);
        prefs.saveAlarms(alarmsList);
        scheduleAlarmModel(this, alarm);

        int h12 = newAlarmHour % 12;
        if (h12 == 0) h12 = 12;
        boolean isPm = newAlarmHour >= 12;
        String formatted = String.format(Locale.US, "%02d:%02d %s", h12, newAlarmMinute, isPm ? "PM" : "AM");
        Toast.makeText(this, "🌸 Alarm set for " + formatted + " with " + sound + "!", Toast.LENGTH_SHORT).show();

        renderAlarmsList();
    }

    private void renderAlarmsList() {
        alarmsList.clear();
        alarmsList.addAll(prefs.getAlarms());

        if (layoutAlarmsContainer == null) return;
        layoutAlarmsContainer.removeAllViews();

        int activeCount = 0;
        for (MelodyAlarmModel alarm : alarmsList) {
            if (alarm.isEnabled()) activeCount++;
        }

        if (tvAlarmsCountBadge != null) {
            tvAlarmsCountBadge.setText(activeCount + " Active");
        }

        if (alarmsList.isEmpty()) {
            if (tvNoAlarms != null) {
                layoutAlarmsContainer.addView(tvNoAlarms);
                tvNoAlarms.setVisibility(View.VISIBLE);
            }
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < alarmsList.size(); i++) {
            final MelodyAlarmModel alarm = alarmsList.get(i);
            View row = inflater.inflate(R.layout.item_melody_alarm_row, layoutAlarmsContainer, false);

            TextView tvTime = row.findViewById(R.id.tv_item_alarm_time);
            TextView tvLabel = row.findViewById(R.id.tv_item_alarm_label);
            TextView tvSound = row.findViewById(R.id.tv_item_alarm_sound);
            TextView btnToggle = row.findViewById(R.id.btn_item_alarm_toggle);
            TextView btnDelete = row.findViewById(R.id.btn_item_alarm_delete);

            int h12 = alarm.getHour() % 12;
            if (h12 == 0) h12 = 12;
            boolean isPm = alarm.getHour() >= 12;
            tvTime.setText(String.format(Locale.US, "%02d:%02d %s", h12, alarm.getMinute(), isPm ? "PM" : "AM"));

            tvLabel.setText(alarm.getLabel());
            tvSound.setText("🎵 " + alarm.getSound());

            updateAlarmRowToggleUi(btnToggle, alarm.isEnabled());

            btnToggle.setOnClickListener(v -> {
                boolean newState = !alarm.isEnabled();
                alarm.setEnabled(newState);
                prefs.saveAlarms(alarmsList);
                if (newState) {
                    scheduleAlarmModel(MelodyClockActivity.this, alarm);
                    Toast.makeText(MelodyClockActivity.this, "🔔 Alarm turned ON", Toast.LENGTH_SHORT).show();
                } else {
                    cancelAlarmById(MelodyClockActivity.this, alarm.getId());
                    Toast.makeText(MelodyClockActivity.this, "🔕 Alarm turned OFF", Toast.LENGTH_SHORT).show();
                }
                updateAlarmRowToggleUi(btnToggle, newState);
                renderAlarmsList();
            });

            btnDelete.setOnClickListener(v -> {
                cancelAlarmById(MelodyClockActivity.this, alarm.getId());
                alarmsList.remove(alarm);
                prefs.saveAlarms(alarmsList);
                Toast.makeText(MelodyClockActivity.this, "🗑️ Alarm removed", Toast.LENGTH_SHORT).show();
                renderAlarmsList();
            });

            layoutAlarmsContainer.addView(row);
        }
    }

    private void updateAlarmRowToggleUi(TextView btnToggle, boolean isEnabled) {
        if (isEnabled) {
            btnToggle.setText("ON");
            btnToggle.setBackgroundResource(R.drawable.bg_melody_chip_selected);
            btnToggle.setTextColor(Color.WHITE);
        } else {
            btnToggle.setText("OFF");
            btnToggle.setBackgroundResource(R.drawable.bg_melody_pill);
            btnToggle.setTextColor(Color.parseColor("#831843"));
        }
    }

    // ================= TIMER TAB =================
    private void setupTimerTab() {
        tvTimerDisplay = findViewById(R.id.tv_timer_display);
        tvTimerStatus = findViewById(R.id.tv_timer_status);
        btnTimerStartPause = findViewById(R.id.btn_timer_start_pause);

        findViewById(R.id.btn_timer_1m).setOnClickListener(v -> setTimerDuration(60, "1 min"));
        findViewById(R.id.btn_timer_3m).setOnClickListener(v -> setTimerDuration(180, "3 min"));
        findViewById(R.id.btn_timer_5m).setOnClickListener(v -> setTimerDuration(300, "5 min"));
        findViewById(R.id.btn_timer_10m).setOnClickListener(v -> setTimerDuration(600, "10 min"));
        findViewById(R.id.btn_timer_15m).setOnClickListener(v -> setTimerDuration(900, "15 min"));

        btnTimerStartPause.setOnClickListener(v -> toggleTimer());
        findViewById(R.id.btn_timer_reset).setOnClickListener(v -> resetTimer());

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTimerRunning) {
                    if (timerRemainingSeconds > 0) {
                        timerRemainingSeconds--;
                        updateTimerDisplay();
                        timerHandler.postDelayed(this, 1000);
                    } else {
                        // Timer completed!
                        isTimerRunning = false;
                        prefs.setTimerRunning(false);
                        prefs.setTimerEndTime(0);
                        cancelAlarm(MelodyClockActivity.this, true);

                        btnTimerStartPause.setText("▶ Start");
                        tvTimerStatus.setText("🎉 Yay! Time's up! Great job! 🌸✨");
                        playChimeTone();
                        Toast.makeText(MelodyClockActivity.this, "🌸 Time is up! Great work! 🎉", Toast.LENGTH_LONG).show();
                    }
                }
            }
        };

        updateTimerDisplay();
    }

    private void checkBackgroundTimerOnResume() {
        if (prefs.isTimerRunning()) {
            long now = System.currentTimeMillis();
            long endTime = prefs.getTimerEndTime();
            long diffSecs = (endTime - now) / 1000L;

            if (diffSecs > 0) {
                timerRemainingSeconds = (int) diffSecs;
                timerTotalSeconds = prefs.getTimerTotalSeconds();
                isTimerRunning = true;
                btnTimerStartPause.setText("⏸ Pause");
                tvTimerStatus.setText("⏳ Counting down... Keep going! ✨");
                updateTimerDisplay();
                timerHandler.removeCallbacks(timerRunnable);
                timerHandler.postDelayed(timerRunnable, 1000);
            } else {
                isTimerRunning = false;
                prefs.setTimerRunning(false);
                prefs.setTimerEndTime(0);
                timerRemainingSeconds = 0;
                updateTimerDisplay();
                btnTimerStartPause.setText("▶ Start");
                tvTimerStatus.setText("🎉 Time finished while you were away! 🌸");
            }
        }
    }

    private void setTimerDuration(int seconds, String label) {
        if (isTimerRunning) toggleTimer();
        timerTotalSeconds = seconds;
        timerRemainingSeconds = seconds;
        tvTimerStatus.setText("Set to " + label + " 🌸 Ready!");
        updateTimerDisplay();
    }

    private void toggleTimer() {
        if (timerRemainingSeconds <= 0) {
            timerRemainingSeconds = timerTotalSeconds;
        }

        isTimerRunning = !isTimerRunning;
        if (isTimerRunning) {
            long endTime = System.currentTimeMillis() + (timerRemainingSeconds * 1000L);
            prefs.setTimerRunning(true);
            prefs.setTimerEndTime(endTime);
            prefs.setTimerTotalSeconds(timerTotalSeconds);

            // Schedule background alarm so it rings if user exits the app
            scheduleAlarm(this, endTime, true, "Timer Finished! 🎉");

            btnTimerStartPause.setText("⏸ Pause");
            tvTimerStatus.setText("⏳ Counting down in background... Keep going! ✨");
            timerHandler.postDelayed(timerRunnable, 1000);
        } else {
            // Paused
            prefs.setTimerRunning(false);
            prefs.setTimerEndTime(0);
            cancelAlarm(this, true);

            btnTimerStartPause.setText("▶ Resume");
            tvTimerStatus.setText("Timer paused 🌸");
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void resetTimer() {
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        prefs.setTimerRunning(false);
        prefs.setTimerEndTime(0);
        cancelAlarm(this, true);

        timerRemainingSeconds = timerTotalSeconds;
        btnTimerStartPause.setText("▶ Start");
        tvTimerStatus.setText("Timer reset 🌸 Ready!");
        updateTimerDisplay();
    }

    private void updateTimerDisplay() {
        int m = timerRemainingSeconds / 60;
        int s = timerRemainingSeconds % 60;
        tvTimerDisplay.setText(String.format(Locale.US, "%02d:%02d", m, s));
    }

    // ================= STOPWATCH TAB =================
    private void setupStopwatchTab() {
        tvStopwatchDisplay = findViewById(R.id.tv_stopwatch_display);
        btnStopwatchStartPause = findViewById(R.id.btn_stopwatch_start_pause);

        btnStopwatchStartPause.setOnClickListener(v -> toggleStopwatch());
        findViewById(R.id.btn_stopwatch_reset).setOnClickListener(v -> resetStopwatch());

        stopwatchRunnable = new Runnable() {
            @Override
            public void run() {
                if (isStopwatchRunning) {
                    long now = System.currentTimeMillis();
                    long elapsed = (now - stopwatchStartTime) + stopwatchElapsedBeforePause;
                    updateStopwatchDisplay(elapsed);
                    stopwatchHandler.postDelayed(this, 60);
                }
            }
        };
    }

    private void toggleStopwatch() {
        isStopwatchRunning = !isStopwatchRunning;
        if (isStopwatchRunning) {
            stopwatchStartTime = System.currentTimeMillis();
            btnStopwatchStartPause.setText("⏸ Pause");
            stopwatchHandler.post(stopwatchRunnable);
        } else {
            stopwatchElapsedBeforePause += (System.currentTimeMillis() - stopwatchStartTime);
            btnStopwatchStartPause.setText("▶ Resume");
            stopwatchHandler.removeCallbacks(stopwatchRunnable);
        }
    }

    private void resetStopwatch() {
        isStopwatchRunning = false;
        stopwatchHandler.removeCallbacks(stopwatchRunnable);
        stopwatchStartTime = 0;
        stopwatchElapsedBeforePause = 0;
        btnStopwatchStartPause.setText("▶ Start");
        tvStopwatchDisplay.setText("00:00.0");
    }

    private void updateStopwatchDisplay(long elapsedMs) {
        int minutes = (int) (elapsedMs / 60000);
        int seconds = (int) ((elapsedMs % 60000) / 1000);
        int tenths = (int) ((elapsedMs % 1000) / 100);
        tvStopwatchDisplay.setText(String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths));
    }

    private void playChimeTone() {
        if (toneGen != null) {
            try {
                toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 500);
            } catch (Exception ignored) {}
        }
    }

    // ================= STATIC ALARM / TIMER HELPERS =================

    public static void scheduleAlarmModel(Context context, MelodyAlarmModel alarm) {
        if (!alarm.isEnabled()) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, alarm.getHour());
        target.set(Calendar.MINUTE, alarm.getMinute());
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(context, MelodyAlarmReceiver.class);
        intent.setAction(MelodyAlarmReceiver.ACTION_TRIGGER_ALARM);
        intent.putExtra(MelodyAlarmAlertActivity.EXTRA_TITLE, alarm.getLabel());
        intent.putExtra(MelodyAlarmAlertActivity.EXTRA_SOUND_TYPE, alarm.getSound());
        intent.putExtra(MelodyAlarmAlertActivity.EXTRA_ALARM_ID, alarm.getId());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pi = PendingIntent.getBroadcast(context, alarm.getId(), intent, flags);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void scheduleAlarm(Context context, long triggerAtMillis, boolean isTimer, String title) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, MelodyAlarmReceiver.class);
        intent.setAction(isTimer ? MelodyAlarmReceiver.ACTION_TRIGGER_TIMER : MelodyAlarmReceiver.ACTION_TRIGGER_ALARM);
        intent.putExtra(MelodyAlarmAlertActivity.EXTRA_TITLE, title);

        int requestCode = isTimer ? 1002 : 1001;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, flags);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void cancelAlarmById(Context context, int alarmId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, MelodyAlarmReceiver.class);
        intent.setAction(MelodyAlarmReceiver.ACTION_TRIGGER_ALARM);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pi = PendingIntent.getBroadcast(context, alarmId, intent, flags);
        try {
            am.cancel(pi);
        } catch (Exception ignored) {}
    }

    public static void cancelAlarm(Context context, boolean isTimer) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, MelodyAlarmReceiver.class);
        intent.setAction(isTimer ? MelodyAlarmReceiver.ACTION_TRIGGER_TIMER : MelodyAlarmReceiver.ACTION_TRIGGER_ALARM);

        int requestCode = isTimer ? 1002 : 1001;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, flags);
        try {
            am.cancel(pi);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSoundPreview();
        timerHandler.removeCallbacks(timerRunnable);
        stopwatchHandler.removeCallbacks(stopwatchRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSoundPreview();
        clockHandler.removeCallbacks(clockRunnable);
        timerHandler.removeCallbacks(timerRunnable);
        stopwatchHandler.removeCallbacks(stopwatchRunnable);
        if (toneGen != null) {
            toneGen.release();
            toneGen = null;
        }
    }
}
