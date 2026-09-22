package com.kids.launcher;

import android.accessibilityservice.AccessibilityService;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Global Melody System Service.
 * - Restores stock Android navigation bar.
 * - Provides a floating, moveable (draggable) My Melody Battery Capsule with live percentage
 *   and cute charging animations across all apps & games.
 * - Melody Touch (AssistiveTouch) floating controller (Back, Home, Recents, Screenshot, Vol, WiFi, Boost).
 * - System-wide Ambient Light Auto-Brightness.
 * - Automatic background RAM boost on app launches to eliminate lag on 4GB RAM tablet.
 * - Blue Light Filter (Night Mode) warm tint overlay.
 * - Posture Reminder periodic gentle notifications.
 * - Volume Limiter (70% cap for ear protection).
 */
public class MelodyGlobalService extends AccessibilityService {

    private WindowManager windowManager;
    private View floatingBatteryView;
    private WindowManager.LayoutParams batteryWindowParams;

    private BatteryGaugeView gaugeFloatingBattery;
    private TextView tvFloatingChargerSpeed;

    // Melody Touch (AssistiveTouch)
    private View touchOrbView;
    private WindowManager.LayoutParams touchOrbParams;
    private View touchMenuView;
    private WindowManager.LayoutParams touchMenuParams;
    private AudioManager audioManager;
    private MelodyMusicManager.MusicStateListener touchMusicListener;

    private BroadcastReceiver batteryReceiver;
    private static MelodyGlobalService instance;

    private int lastBatteryLevel = 100;
    private boolean lastIsCharging = false;

    // Global Auto-Brightness across all apps
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private SensorEventListener lightSensorListener;
    private PreferencesManager prefs;
    private float lastLux = -1;
    private long lastBrightnessTime = 0;

    // Health & Wellbeing Overlays
    private View blueLightOverlayView;
    private final Handler healthHandler = new Handler(Looper.getMainLooper());
    private Runnable postureRunnable;

    private String lastBoostedPackage = "";

    public static MelodyGlobalService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            CharSequence cls = event.getClassName();
            if (pkg != null) {
                String pkgStr = pkg.toString();
                String clsStr = cls != null ? cls.toString() : "";
                if (!"com.android.systemui".equals(pkgStr) && !pkgStr.contains("inputmethod")) {
                    boolean isHomeDesktop = "com.kids.launcher".equals(pkgStr)
                            && ("com.kids.launcher.MainActivity".equals(clsStr)
                                || clsStr.endsWith(".MainActivity")
                                || "MainActivity".equals(clsStr));

                    boolean isDialogOnLauncher = "com.kids.launcher".equals(pkgStr)
                            && (clsStr.contains("Dialog") || "android.app.Dialog".equals(clsStr));

                    if (isHomeDesktop) {
                        // At launcher: auto hide floating battery
                        setFloatingBatteryVisible(false);
                    } else if (!isDialogOnLauncher) {
                        // When opening another app or activity: show floating battery!
                        setFloatingBatteryVisible(true);
                        // Automatically sweep background memory to prevent lag
                        if (!pkgStr.equals(lastBoostedPackage)) {
                            lastBoostedPackage = pkgStr;
                            long freed = DeviceBooster.boostAndGetFreedMb(this, pkgStr);
                            Toast.makeText(this, "🚀 Auto-Boost: " + freed + " MB RAM cleared for smooth play! 🌸", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }
        restoreStockNavBar();
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = new PreferencesManager(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        restoreStockNavBar();
        initFloatingBatteryCapsule();
        initMelodyTouchOrb();
        setupBatteryReceiver();
        setupGlobalAutoBrightness();
        updateBlueLightFilter();
        startHealthReminders();
    }

    public void updateBlueLightFilter() {
        if (windowManager == null || prefs == null) return;
        boolean enabled = prefs.isBlueLightFilterEnabled();

        if (enabled) {
            if (blueLightOverlayView == null) {
                blueLightOverlayView = new View(this);
                blueLightOverlayView.setBackgroundColor(Color.parseColor("#2EFA9800")); // Warm soft amber tint

                int layoutType;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
                } else {
                    layoutType = WindowManager.LayoutParams.TYPE_PHONE;
                }

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

                try {
                    windowManager.addView(blueLightOverlayView, params);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (blueLightOverlayView != null) {
                try {
                    windowManager.removeView(blueLightOverlayView);
                } catch (Exception ignored) {}
                blueLightOverlayView = null;
            }
        }
    }

    private void startHealthReminders() {
        postureRunnable = new Runnable() {
            @Override
            public void run() {
                if (prefs != null && prefs.isPostureReminderEnabled()) {
                    Toast.makeText(MelodyGlobalService.this, "Sit up straight, sweetie! 🧸 Good posture keeps your back happy! 🌸", Toast.LENGTH_LONG).show();
                }
                healthHandler.postDelayed(this, 35 * 60 * 1000L); // every 35 minutes
            }
        };
        healthHandler.postDelayed(postureRunnable, 35 * 60 * 1000L);
    }

    private void restoreStockNavBar() {
        try {
            // Auto hide navigation bar after a short delay across the system
            Settings.Global.putString(getContentResolver(), "policy_control", "immersive.navigation=*");
        } catch (Exception ignored) {}
    }

    private void setupGlobalAutoBrightness() {
        try {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
                if (lightSensor != null) {
                    lightSensorListener = new SensorEventListener() {
                        @Override
                        public void onSensorChanged(SensorEvent event) {
                            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                                handleLightSensorChange(event.values[0]);
                            }
                        }

                        @Override
                        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                    };
                    sensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleLightSensorChange(float lux) {
        if (prefs == null || !prefs.isAutoBrightness()) return;

        long now = System.currentTimeMillis();
        if (now - lastBrightnessTime < 1400 && Math.abs(lux - lastLux) < 35) {
            return;
        }
        lastLux = lux;
        lastBrightnessTime = now;

        int targetBrightness;
        if (lux <= 5) {
            targetBrightness = 30;   // Very dark room
        } else if (lux <= 25) {
            targetBrightness = 65;   // Dim room
        } else if (lux <= 80) {
            targetBrightness = 110;  // Soft indoor
        } else if (lux <= 200) {
            targetBrightness = 165;  // Bright indoor
        } else if (lux <= 500) {
            targetBrightness = 215;  // Very bright
        } else {
            targetBrightness = 255;  // Maximum sunlight
        }

        applySystemBrightness(targetBrightness);
    }

    public void applySystemBrightness(int brightness) {
        try {
            int clamped = Math.max(20, Math.min(255, brightness));
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, clamped);
        } catch (Exception ignored) {}
    }

    public void triggerInstantBoost() {
        DeviceBooster.boost(this);
    }

    /**
     * Initializes the floating, moveable (draggable) My Melody Battery Capsule.
     */
    private void initFloatingBatteryCapsule() {
        if (windowManager == null || floatingBatteryView != null) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        floatingBatteryView = inflater.inflate(R.layout.layout_floating_battery, null);

        gaugeFloatingBattery = floatingBatteryView.findViewById(R.id.gauge_floating_battery);
        if (gaugeFloatingBattery != null) {
            gaugeFloatingBattery.setShowPercentInside(true);
        }
        tvFloatingChargerSpeed = floatingBatteryView.findViewById(R.id.tv_floating_charger_speed);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        batteryWindowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        batteryWindowParams.gravity = Gravity.TOP | Gravity.LEFT;

        // Position by default in the top right area
        DisplayMetrics dm = getResources().getDisplayMetrics();
        batteryWindowParams.x = dm.widthPixels - (int) dpToPx(130);
        batteryWindowParams.y = (int) dpToPx(75);

        // Setup touch & drag movement
        setupDraggableTouchListener(floatingBatteryView, batteryWindowParams);

        // Auto hide on launcher desktop by default
        floatingBatteryView.setVisibility(View.GONE);

        try {
            windowManager.addView(floatingBatteryView, batteryWindowParams);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                batteryWindowParams.type = WindowManager.LayoutParams.TYPE_PHONE;
                windowManager.addView(floatingBatteryView, batteryWindowParams);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Controls floating battery visibility (auto hidden on launcher, shown in other apps).
     */
    public void setFloatingBatteryVisible(boolean visible) {
        if (floatingBatteryView != null) {
            floatingBatteryView.post(() -> {
                floatingBatteryView.setVisibility(visible ? View.VISIBLE : View.GONE);
            });
        }
    }

    private void setupDraggableTouchListener(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isMoved = false;
            private long touchDownTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchDownTime = System.currentTimeMillis();
                        isMoved = false;
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(80).start();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
                            isMoved = true;
                        }
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        if (windowManager != null && floatingBatteryView != null) {
                            try {
                                windowManager.updateViewLayout(floatingBatteryView, params);
                            } catch (Exception ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                        long duration = System.currentTimeMillis() - touchDownTime;
                        if (!isMoved && duration < 350) {
                            try {
                                Intent intent = new Intent(MelodyGlobalService.this, MelodyBatteryActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            } catch (Exception e) {
                                String msg = "🔋 Battery: " + lastBatteryLevel + "%"
                                        + (lastIsCharging ? " • Charging ⚡" : " • Good Health 🌸");
                                Toast.makeText(MelodyGlobalService.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // Clamp inside screen bounds
                            DisplayMetrics metrics = getResources().getDisplayMetrics();
                            int width = v.getWidth() > 0 ? v.getWidth() : (int) dpToPx(80);
                            int height = v.getHeight() > 0 ? v.getHeight() : (int) dpToPx(36);
                            int maxX = metrics.widthPixels - width;
                            int maxY = metrics.heightPixels - height;
                            params.x = Math.max(0, Math.min(maxX, params.x));
                            params.y = Math.max(0, Math.min(maxY, params.y));
                            if (windowManager != null) {
                                try { windowManager.updateViewLayout(v, params); } catch (Exception ignored) {}
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private boolean hasAlertedFullBattery = false;

    private void setupBatteryReceiver() {
        prefs = new PreferencesManager(this);
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;

                int pct = (scale > 0) ? (int) (level * 100.0f / scale) : 100;
                lastBatteryLevel = pct;
                lastIsCharging = isCharging;

                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean isFastCharger = (plugged == BatteryManager.BATTERY_PLUGGED_AC);

                if (gaugeFloatingBattery != null) {
                    gaugeFloatingBattery.setBatteryStatus(pct, isCharging, isFastCharger);
                }

                // Global Battery Saver (< 20%)
                if (pct < 20 && !isCharging) {
                    if (prefs != null && prefs.isAutoBatterySaverEnabled() && !prefs.isBatterySaverActive()) {
                        prefs.setBatterySaverActive(true);
                        DeviceBooster.applyBatterySaver(context, true);
                        applySystemBrightness(65);
                    }
                } else if (isCharging || pct >= 20) {
                    if (prefs != null && prefs.isBatterySaverActive()) {
                        prefs.setBatterySaverActive(false);
                        DeviceBooster.applyBatterySaver(context, false);
                    }
                }

                // Full Battery Sound Alert (100%)
                if (pct >= 100 && isCharging) {
                    if (prefs != null && prefs.isFullBatteryAlertEnabled() && !hasAlertedFullBattery) {
                        hasAlertedFullBattery = true;
                        try {
                            android.media.ToneGenerator toneGen = new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100);
                            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 500);
                        } catch (Exception ignored) {}
                        Toast.makeText(context, "🔋 Battery is 100% Full! You can unplug now 🌸⚡", Toast.LENGTH_LONG).show();
                    }
                } else if (pct < 98 || !isCharging) {
                    hasAlertedFullBattery = false;
                }

                if (tvFloatingChargerSpeed != null) {
                    if (isCharging) {
                        tvFloatingChargerSpeed.setVisibility(View.VISIBLE);
                        tvFloatingChargerSpeed.setText(isFastCharger ? "⚡ Fast" : "⚡ Slow");
                        tvFloatingChargerSpeed.setTextColor(isFastCharger ? Color.parseColor("#0284C7") : Color.parseColor("#D97706"));
                    } else {
                        tvFloatingChargerSpeed.setVisibility(View.GONE);
                    }
                }
            }
        };

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    /**
     * Initializes Melody Touch (AssistiveTouch Floating Orb).
     */
    private void initMelodyTouchOrb() {
        if (windowManager == null || touchOrbView != null) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        touchOrbView = inflater.inflate(R.layout.layout_melody_touch_orb, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        touchOrbParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        touchOrbParams.gravity = Gravity.TOP | Gravity.LEFT;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        touchOrbParams.x = (int) dpToPx(12);
        touchOrbParams.y = dm.heightPixels / 2 - (int) dpToPx(26);

        setupTouchOrbDragAndClick(touchOrbView, touchOrbParams);

        try {
            windowManager.addView(touchOrbView, touchOrbParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTouchOrbDragAndClick(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isMoved = false;
            private long touchDownTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchDownTime = System.currentTimeMillis();
                        isMoved = false;
                        v.setAlpha(1.0f);
                        v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80).start();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 14 || Math.abs(dy) > 14) {
                            isMoved = true;
                        }
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        if (windowManager != null && touchOrbView != null) {
                            try { windowManager.updateViewLayout(touchOrbView, params); } catch (Exception ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                        long duration = System.currentTimeMillis() - touchDownTime;
                        if (!isMoved && duration < 600) {
                            showTouchMenu();
                        } else {
                            DisplayMetrics metrics = getResources().getDisplayMetrics();
                            int orbSize = (int) dpToPx(52);
                            int middle = metrics.widthPixels / 2;
                            if (params.x + orbSize / 2 < middle) {
                                params.x = (int) dpToPx(8);
                            } else {
                                params.x = metrics.widthPixels - orbSize - (int) dpToPx(8);
                            }
                            int maxY = metrics.heightPixels - orbSize - (int) dpToPx(48);
                            params.y = Math.max((int) dpToPx(24), Math.min(params.y, maxY));
                            if (windowManager != null && touchOrbView != null) {
                                try { windowManager.updateViewLayout(touchOrbView, params); } catch (Exception ignored) {}
                            }
                            v.setAlpha(0.75f);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void showTouchMenu() {
        if (windowManager != null && touchMenuView != null) {
            try { windowManager.removeView(touchMenuView); } catch (Exception ignored) {}
            touchMenuView = null;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        touchMenuView = inflater.inflate(R.layout.layout_melody_touch_menu, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        touchMenuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        // Bind Actions
        touchMenuView.findViewById(R.id.touch_menu_overlay_root).setOnClickListener(v -> hideTouchMenu());
        touchMenuView.findViewById(R.id.btn_close_touch_menu).setOnClickListener(v -> hideTouchMenu());

        // 1. Back
        touchMenuView.findViewById(R.id.btn_action_back).setOnClickListener(v -> {
            performGlobalAction(GLOBAL_ACTION_BACK);
            hideTouchMenu();
        });

        // 2. Home
        touchMenuView.findViewById(R.id.btn_action_home).setOnClickListener(v -> {
            performGlobalAction(GLOBAL_ACTION_HOME);
            hideTouchMenu();
        });

        // 3. Recents
        touchMenuView.findViewById(R.id.btn_action_recents).setOnClickListener(v -> {
            performGlobalAction(GLOBAL_ACTION_RECENTS);
            hideTouchMenu();
        });

        // 4. Screenshot
        touchMenuView.findViewById(R.id.btn_action_screenshot).setOnClickListener(v -> {
            hideTouchMenu();
            if (touchOrbView != null) {
                touchOrbView.postDelayed(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                    } else {
                        Toast.makeText(MelodyGlobalService.this, "📸 Capture requested", Toast.LENGTH_SHORT).show();
                    }
                }, 300);
            }
        });

        // 5. Volume Up
        touchMenuView.findViewById(R.id.btn_action_vol_up).setOnClickListener(v -> {
            if (audioManager != null) {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int cap = (prefs != null && prefs.isVolumeLimiterEnabled()) ? (int) (max * (prefs.getVolumeCapPercent() / 100.0f)) : max;
                if (current >= cap) {
                    Toast.makeText(MelodyGlobalService.this, "🎧 Ear Protection: Max volume capped at " + (prefs != null ? prefs.getVolumeCapPercent() : 70) + "%! 💕", Toast.LENGTH_SHORT).show();
                    return;
                }
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            }
        });

        // 6. Volume Down
        touchMenuView.findViewById(R.id.btn_action_vol_down).setOnClickListener(v -> {
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            }
        });

        // 7. WiFi Toggle
        TextView tvWifiLabel = touchMenuView.findViewById(R.id.tv_action_wifi_label);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null && tvWifiLabel != null) {
            tvWifiLabel.setText(wm.isWifiEnabled() ? "WiFi ON" : "WiFi OFF");
        }
        touchMenuView.findViewById(R.id.btn_action_wifi).setOnClickListener(v -> {
            if (wm != null) {
                boolean newState = !wm.isWifiEnabled();
                wm.setWifiEnabled(newState);
                if (tvWifiLabel != null) {
                    tvWifiLabel.setText(newState ? "WiFi ON" : "WiFi OFF");
                }
                Toast.makeText(MelodyGlobalService.this, newState ? "📶 WiFi Turned ON" : "📴 WiFi Turned OFF", Toast.LENGTH_SHORT).show();
            }
        });

        // 8. Boost
        touchMenuView.findViewById(R.id.btn_action_boost).setOnClickListener(v -> {
            long freed = DeviceBooster.boostAndGetFreedMb(MelodyGlobalService.this);
            Toast.makeText(MelodyGlobalService.this, "🚀 Speed Boosted! Freed " + freed + " MB RAM! 🌸✨", Toast.LENGTH_SHORT).show();
            hideTouchMenu();
        });

        // 9. Apple ID Assistant Music Controls
        TextView tvMusicInfo = touchMenuView.findViewById(R.id.tv_touch_music_info);
        TextView tvMusicStatus = touchMenuView.findViewById(R.id.tv_touch_music_status);
        TextView btnMusicPrev = touchMenuView.findViewById(R.id.btn_touch_music_prev);
        TextView btnMusicPlayPause = touchMenuView.findViewById(R.id.btn_touch_music_play_pause);
        TextView btnMusicNext = touchMenuView.findViewById(R.id.btn_touch_music_next);

        MelodyMusicManager musicMgr = MelodyMusicManager.getInstance();
        if (tvMusicInfo != null) {
            tvMusicInfo.setText(musicMgr.getCurrentTitle());
            tvMusicInfo.setSelected(true);
        }
        if (tvMusicStatus != null) {
            tvMusicStatus.setText(musicMgr.isPlaying() ? "Playing" : "Paused");
        }
        if (btnMusicPlayPause != null) {
            btnMusicPlayPause.setText(musicMgr.isPlaying() ? "⏸" : "▶");
        }

        if (btnMusicPrev != null) {
            btnMusicPrev.setOnClickListener(v -> musicMgr.prev());
        }
        if (btnMusicPlayPause != null) {
            btnMusicPlayPause.setOnClickListener(v -> musicMgr.togglePlayPause());
        }
        if (btnMusicNext != null) {
            btnMusicNext.setOnClickListener(v -> musicMgr.next());
        }

        touchMusicListener = new MelodyMusicManager.MusicStateListener() {
            @Override
            public void onTrackChanged(String title, String subtitle, boolean isPlaying, int durationMs) {
                if (tvMusicInfo != null) tvMusicInfo.setText(title);
                if (tvMusicStatus != null) tvMusicStatus.setText(isPlaying ? "Playing" : "Paused");
                if (btnMusicPlayPause != null) btnMusicPlayPause.setText(isPlaying ? "⏸" : "▶");
            }

            @Override
            public void onPlayStateChanged(boolean isPlaying) {
                if (tvMusicStatus != null) tvMusicStatus.setText(isPlaying ? "Playing" : "Paused");
                if (btnMusicPlayPause != null) btnMusicPlayPause.setText(isPlaying ? "⏸" : "▶");
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {}

            @Override
            public void onShuffleModeChanged(boolean isShuffle) {}
        };
        musicMgr.addListener(touchMusicListener);

        try {
            windowManager.addView(touchMenuView, touchMenuParams);
            if (touchOrbView != null) touchOrbView.setVisibility(View.GONE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideTouchMenu() {
        if (touchMusicListener != null) {
            MelodyMusicManager.getInstance().removeListener(touchMusicListener);
            touchMusicListener = null;
        }
        if (windowManager != null && touchMenuView != null) {
            try {
                windowManager.removeView(touchMenuView);
            } catch (Exception ignored) {}
            touchMenuView = null;
        }
        if (touchOrbView != null) {
            touchOrbView.setVisibility(View.VISIBLE);
            touchOrbView.setAlpha(0.75f);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;

        if (touchMusicListener != null) {
            MelodyMusicManager.getInstance().removeListener(touchMusicListener);
            touchMusicListener = null;
        }

        if (windowManager != null && floatingBatteryView != null) {
            try { windowManager.removeView(floatingBatteryView); } catch (Exception ignored) {}
            floatingBatteryView = null;
        }

        if (windowManager != null && touchOrbView != null) {
            try { windowManager.removeView(touchOrbView); } catch (Exception ignored) {}
            touchOrbView = null;
        }

        if (windowManager != null && touchMenuView != null) {
            try { windowManager.removeView(touchMenuView); } catch (Exception ignored) {}
            touchMenuView = null;
        }

        if (batteryReceiver != null) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
            batteryReceiver = null;
        }

        if (sensorManager != null && lightSensorListener != null) {
            try { sensorManager.unregisterListener(lightSensorListener); } catch (Exception ignored) {}
        }
    }
}
