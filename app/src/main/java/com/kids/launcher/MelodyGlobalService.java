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
import android.view.accessibility.AccessibilityNodeInfo;
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
    private View topStatusBarShieldView;
    private final Handler healthHandler = new Handler(Looper.getMainLooper());
    private Runnable postureRunnable;

    private String lastBoostedPackage = "";

    // Automatic Hands-Free Package Installer Support
    private static boolean isPendingAutoInstall = false;
    private static long autoInstallStartTime = 0;

    public static void setPendingAutoInstall(boolean pending) {
        isPendingAutoInstall = pending;
        autoInstallStartTime = pending ? System.currentTimeMillis() : 0;
    }

    public static boolean isPendingAutoInstall() {
        return isPendingAutoInstall;
    }

    public static MelodyGlobalService getInstance() {
        return instance;
    }

    // In-App Purchase Blocker: cooldown to prevent toast spam
    private long lastIapBlockTime = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (isPendingAutoInstall) {
            handleAutoInstallEvent(event);
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            CharSequence cls = event.getClassName();
            if (pkg != null) {
                String pkgStr = pkg.toString();
                String clsStr = cls != null ? cls.toString() : "";

                // ── In-App Purchase Blocker ──
                // Block Google Play billing/purchase dialogs instantly
                if ("com.android.vending".equals(pkgStr) && isPlayBillingWindow(clsStr)) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    long now = System.currentTimeMillis();
                    if (now - lastIapBlockTime > 3000) {
                        lastIapBlockTime = now;
                        Toast.makeText(this,
                                "🌸 Purchases are blocked! Ask a parent for help 💕",
                                Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                if ("com.android.systemui".equals(pkgStr)) {
                    // Lock down stock Android Notification Panel and Quick Settings completely
                    collapseStockStatusBar();
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    return;
                } else if (!pkgStr.contains("inputmethod")) {
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

    public void takeGlobalScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        } else {
            Toast.makeText(this, "Screenshot captured 📸", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleAutoInstallEvent(AccessibilityEvent event) {
        if (!isPendingAutoInstall) return;

        // Auto-expire after 60 seconds
        if (System.currentTimeMillis() - autoInstallStartTime > 60000) {
            isPendingAutoInstall = false;
            return;
        }

        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String pkgStr = pkg.toString();

        if (pkgStr.contains("packageinstaller")) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            try {
                // 1. Check for "Install" or "Update" buttons
                boolean clickedInstall = clickNodeByTextOrId(root,
                        new String[]{"Install", "Update", "Pasang", "INSTALL", "UPDATE", "PASANG"},
                        new String[]{"ok_button", "button1"});

                // 2. If already finished, click "Open" or "Done"
                if (!clickedInstall) {
                    boolean clickedOpen = clickNodeByTextOrId(root,
                            new String[]{"Open", "Buka", "OPEN", "BUKA", "Done", "Selesai", "DONE"},
                            new String[]{"launch_button", "done_button", "button1"});
                    if (clickedOpen) {
                        isPendingAutoInstall = false;
                    }
                }
            } finally {
                root.recycle();
            }
        }
    }

    private boolean clickNodeByTextOrId(AccessibilityNodeInfo root, String[] targetTexts, String[] targetIdSubstrings) {
        if (root == null) return false;

        // Search by view resource ID
        for (String idPart : targetIdSubstrings) {
            java.util.List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId("com.google.android.packageinstaller:id/" + idPart);
            if (list == null || list.isEmpty()) {
                list = root.findAccessibilityNodeInfosByViewId("com.android.packageinstaller:id/" + idPart);
            }
            if (list == null || list.isEmpty()) {
                list = root.findAccessibilityNodeInfosByViewId("android:id/" + idPart);
            }
            if (list != null && !list.isEmpty()) {
                for (AccessibilityNodeInfo node : list) {
                    if (node.isEnabled() && node.isClickable()) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    }
                }
            }
        }

        // Search by text
        for (String text : targetTexts) {
            java.util.List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
            if (list != null && !list.isEmpty()) {
                for (AccessibilityNodeInfo node : list) {
                    if (node.isEnabled()) {
                        if (node.isClickable()) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            return true;
                        }
                        AccessibilityNodeInfo parent = node.getParent();
                        if (parent != null && parent.isClickable()) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Detects Google Play Store billing/purchase windows.
     * Matches known Finsky purchase flow activities and any billing-related class names.
     */
    private boolean isPlayBillingWindow(String className) {
        if (className == null || className.isEmpty()) return false;
        String lower = className.toLowerCase();
        // Google Play billing flow activities (Finsky = Play Store internal codename)
        return lower.contains("purchase")
                || lower.contains("billing")
                || lower.contains("subscribe")
                || lower.contains("lightpurchaseflow")
                || lower.contains("acquisitionactivity")
                || lower.contains("paymentflow")
                || lower.contains("finsky.activities.buy");
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
        initStatusBarShield();
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

    private void initStatusBarShield() {
        if (windowManager == null || topStatusBarShieldView != null) return;

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        int shieldHeight = (int) (40 * getResources().getDisplayMetrics().density);

        WindowManager.LayoutParams shieldParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                shieldHeight,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        shieldParams.gravity = Gravity.TOP | Gravity.START;

        topStatusBarShieldView = new View(this);
        topStatusBarShieldView.setBackgroundColor(Color.TRANSPARENT);

        topStatusBarShieldView.setOnTouchListener(new View.OnTouchListener() {
            private float startY = 0f;
            private float startX = 0f;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        startX = event.getRawX();
                        collapseStockStatusBar();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        collapseStockStatusBar();
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dy = event.getRawY() - startY;
                        float dx = Math.abs(event.getRawX() - startX);
                        collapseStockStatusBar();
                        if (dy > 12 || dx < 30) {
                            openMelodyControlCenter();
                        }
                        return true;
                }
                return true;
            }
        });

        try {
            windowManager.addView(topStatusBarShieldView, shieldParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void collapseStockStatusBar() {
        try {
            Object statusBarService = getSystemService("statusbar");
            if (statusBarService != null) {
                java.lang.reflect.Method collapsePanels = statusBarService.getClass().getMethod("collapsePanels");
                collapsePanels.invoke(statusBarService);
            }
        } catch (Exception e1) {
            try {
                Object statusBarService = getSystemService("statusbar");
                if (statusBarService != null) {
                    java.lang.reflect.Method collapse = statusBarService.getClass().getMethod("collapse");
                    collapse.invoke(statusBarService);
                }
            } catch (Exception ignored) {}
        }
    }

    public void openMelodyControlCenter() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction(MainActivity.ACTION_SHOW_QUICK_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
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
        touchOrbParams.x = (int) dpToPx(8);
        touchOrbParams.y = dm.heightPixels / 2 - (int) dpToPx(34);

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
            private boolean isDragging = false;
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
                        isDragging = false;
                        v.setAlpha(1.0f);
                        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(80).start();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        float distance = (float) Math.hypot(deltaX, deltaY);

                        // Only begin moving window layout if movement exceeds touch slop (18dp)
                        if (!isDragging && distance > dpToPx(18)) {
                            isDragging = true;
                        }

                        if (isDragging) {
                            params.x = initialX + (int) deltaX;
                            params.y = initialY + (int) deltaY;
                            if (windowManager != null && touchOrbView != null) {
                                try { windowManager.updateViewLayout(touchOrbView, params); } catch (Exception ignored) {}
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                        long duration = System.currentTimeMillis() - touchDownTime;
                        float totalDist = (float) Math.hypot(event.getRawX() - initialTouchX, event.getRawY() - initialTouchY);

                        // If moved less than click slop (28dp) and released within 850ms -> ALWAYS A CLICK!
                        if (!isDragging || (totalDist < dpToPx(28) && duration < 850)) {
                            // Reset back to initial pos so micro jitter doesn't move it
                            params.x = initialX;
                            params.y = initialY;
                            if (windowManager != null && touchOrbView != null) {
                                try { windowManager.updateViewLayout(touchOrbView, params); } catch (Exception ignored) {}
                            }
                            try {
                                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                            } catch (Exception ignored) {}
                            showTouchMenu();
                        } else {
                            // Drag completed: snap to nearest screen edge (left or right)
                            DisplayMetrics metrics = getResources().getDisplayMetrics();
                            int orbSize = (int) dpToPx(68);
                            int middle = metrics.widthPixels / 2;
                            if (params.x + orbSize / 2 < middle) {
                                params.x = (int) dpToPx(4);
                            } else {
                                params.x = metrics.widthPixels - orbSize - (int) dpToPx(4);
                            }
                            int maxY = metrics.heightPixels - orbSize - (int) dpToPx(48);
                            params.y = Math.max((int) dpToPx(20), Math.min(params.y, maxY));
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
        boolean currentWifi = MelodyNetworkHelper.isWifiEnabled(this);
        if (tvWifiLabel != null) {
            tvWifiLabel.setText(currentWifi ? "WiFi ON" : "WiFi OFF");
        }
        touchMenuView.findViewById(R.id.btn_action_wifi).setOnClickListener(v -> {
            boolean nextWifi = !MelodyNetworkHelper.isWifiEnabled(this);
            MelodyNetworkHelper.setWifiEnabled(this, nextWifi);
            if (tvWifiLabel != null) {
                tvWifiLabel.setText(nextWifi ? "WiFi ON" : "WiFi OFF");
            }
            Toast.makeText(MelodyGlobalService.this, nextWifi ? "📶 WiFi Turning ON 🌸" : "📴 WiFi Turning OFF", Toast.LENGTH_SHORT).show();
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

        if (windowManager != null && topStatusBarShieldView != null) {
            try { windowManager.removeView(topStatusBarShieldView); } catch (Exception ignored) {}
            topStatusBarShieldView = null;
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
