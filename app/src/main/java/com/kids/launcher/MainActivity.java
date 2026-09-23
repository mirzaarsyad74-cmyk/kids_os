package com.kids.launcher;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.ActivityInfo;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.net.Uri;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private PreferencesManager prefs;
    private AppAdapter adapter;
    private final List<AppModel> allAllowedApps = new ArrayList<>();
    private final List<AppModel> displayedApps = new ArrayList<>();

    // iWawa Header & Navigation Views
    private ImageView ivMelodyBg;
    private TextView tvGreeting;
    private TextView tvAvatarBadge;
    private TextView tvChildName;
    private TextView tvClock;
    private TextView tvDate;
    private LinearLayout layoutPageDots;
    private ImageButton btnIwawaBack;
    private ImageButton btnIwawaMenu;
    private GridLayoutManager gridLayoutManager;
    private MelodyWifiView ivWifiStatus;
    private BroadcastReceiver wifiReceiver;

    private TextView tvTimerBadge;
    private LinearLayout layoutBatteryCapsule;
    private BatteryGaugeView gaugeBattery;
    private TextView tvBatteryPercent;
    private TextView tvChargingSparkle;
    private ObjectAnimator chargingSparkleAnimator;
    private TextView btnVolumeQuick;
    private FrameLayout btnNotificationBell;
    public static final String ACTION_SHOW_QUICK_SETTINGS = "com.kids.launcher.ACTION_SHOW_QUICK_SETTINGS";

    private View viewNotificationDot;
    private TextView btnWallpaperPicker;
    private TextView btnSosCall;
    private boolean isEyeBreakShowing = false;

    // Overlays
    private LinearLayout layoutTimeUpOverlay;
    private LinearLayout layoutVolumeHud;
    private ProgressBar pbVolumeHud;
    private TextView tvHudPercentage;
    private TextView tvHudSpeakerIcon;
    private FrameLayout layoutControlCenterContainer;

    // Control center widgets
    private SeekBar sbBrightness;
    private SeekBar sbVolume;
    private TextView tvNotifBatteryText;
    private TextView tvNotifTimeText;

    // Quick Setting Tiles
    private LinearLayout layoutQuickWifi;
    private TextView tvQuickWifiIcon, tvQuickWifiLabel;
    private LinearLayout layoutQuickBluetooth;
    private TextView tvQuickBtIcon, tvQuickBtLabel;
    private LinearLayout layoutQuickGps;
    private TextView tvQuickGpsIcon, tvQuickGpsLabel;
    private LinearLayout layoutQuickTorch;
    private TextView tvQuickTorchIcon, tvQuickTorchLabel;
    private LinearLayout layoutQuickScreenshot;
    private TextView tvQuickScreenshotIcon, tvQuickScreenshotLabel;
    private LinearLayout layoutQuickRotation;
    private TextView tvQuickRotationIcon, tvQuickRotationLabel;
    private boolean isTorchOn = false;
    private float touchDownY = 0f;
    private float touchDownX = 0f;

    private RecyclerView rvKidsApps;
    private TextView chipAll, chipGames, chipCreative, chipMedia, chipLearning;
    private String currentCategory = AppModel.CAT_ALL;

    private AudioManager audioManager;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private boolean isAutoBrightness = true;

    private TextView btnAutoBrightnessToggle;
    private TextView tvSurroundingLightStatus;
    private ValueAnimator brightnessAnimator;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Handler hudHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable hudDismissRunnable;

    private BroadcastReceiver batteryReceiver;
    private boolean hasAlertedFullBattery = false;


    // Recents Overlay
    private FrameLayout layoutRecentsOverlay;
    private LinearLayout layoutRecentAppsContainer;
    private TextView tvNoRecents;
    private Button btnClearAllRecents;
    private View btnCloseRecents;
    private final List<AppModel> recentApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupNavBarAutoHide();
        setContentView(R.layout.activity_main);

        prefs = new PreferencesManager(this);
        if (!prefs.isOnboardingCompleted()) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
        prefs.setAutoBrightness(true);
        isAutoBrightness = true;
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        initDefaultWhitelistIfNeeded();
        initViews();
        setupCategoryChips();
        setupControlCenter();
        setupBatteryReceiver();
        setupWifiReceiver();
        prewarmWebViewInBackground();

        tvAvatarBadge.setText(prefs.getAvatar());
        tvAvatarBadge.setOnClickListener(v -> showAvatarPicker());

        gridLayoutManager = new GridLayoutManager(this, 2, GridLayoutManager.HORIZONTAL, false);
        rvKidsApps.setLayoutManager(gridLayoutManager);
        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(rvKidsApps);

        adapter = new AppAdapter(this, displayedApps, this::launchApp);
        rvKidsApps.setAdapter(adapter);

        rvKidsApps.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updatePageDots(gridLayoutManager);
                }
            }
        });

        View btnParentZone = findViewById(R.id.btn_parent_zone);
        if (btnParentZone != null) {
            btnParentZone.setOnClickListener(v -> showPinDialog(() -> {
                Intent intent = new Intent(MainActivity.this, ParentZoneActivity.class);
                startActivity(intent);
            }));
        }

        View btnUnlockParent = findViewById(R.id.btn_unlock_parent);
        if (btnUnlockParent != null) {
            btnUnlockParent.setOnClickListener(v -> showPinDialog(() -> {
                prefs.resetTimer();
                if (layoutTimeUpOverlay != null) layoutTimeUpOverlay.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Playtime extended!", Toast.LENGTH_SHORT).show();
            }));
        }

        startClockAndTimer();
    }

    private void initViews() {
        ivMelodyBg = findViewById(R.id.iv_melody_bg);
        tvGreeting = findViewById(R.id.tv_greeting);
        btnWallpaperPicker = findViewById(R.id.btn_wallpaper_picker);
        btnSosCall = findViewById(R.id.btn_sos_call);

        tvDate = findViewById(R.id.tv_date);
        tvChildName = findViewById(R.id.tv_child_name);
        layoutPageDots = findViewById(R.id.layout_page_dots);
        btnIwawaBack = findViewById(R.id.btn_iwawa_back);
        btnIwawaMenu = findViewById(R.id.btn_iwawa_menu);

        if (tvChildName != null) {
            tvChildName.setText("Irdina");
        }

        if (btnIwawaMenu != null) {
            btnIwawaMenu.setOnClickListener(v -> showPinDialog(() -> {
                Intent intent = new Intent(MainActivity.this, ParentZoneActivity.class);
                startActivity(intent);
            }));
        }

        if (btnIwawaBack != null) {
            btnIwawaBack.setOnClickListener(v -> showPinDialog(() -> {
                finish();
            }));
        }

        View layoutKidProfile = findViewById(R.id.layout_kid_profile);
        if (layoutKidProfile != null) {
            layoutKidProfile.setOnClickListener(v -> showAvatarPicker());
        }

        applyWallpaper();
        updateGreeting();

        if (btnWallpaperPicker != null) {
            btnWallpaperPicker.setOnClickListener(v -> WallpaperHelper.showWallpaperPicker(this, idx -> applyWallpaper()));
        }

        if (btnSosCall != null) {
            btnSosCall.setOnClickListener(v -> triggerSosCall());
        }

        ivWifiStatus = findViewById(R.id.iv_wifi_status);
        tvAvatarBadge = findViewById(R.id.tv_avatar_badge);
        tvClock = findViewById(R.id.tv_clock);
        tvTimerBadge = findViewById(R.id.tv_timer_badge);
        layoutBatteryCapsule = findViewById(R.id.layout_battery_capsule);
        gaugeBattery = findViewById(R.id.gauge_battery);
        tvBatteryPercent = findViewById(R.id.tv_battery_percent);
        tvChargingSparkle = findViewById(R.id.tv_charging_sparkle);

        layoutRecentsOverlay = findViewById(R.id.layout_recents_overlay);
        layoutRecentAppsContainer = findViewById(R.id.layout_recent_apps_container);
        tvNoRecents = findViewById(R.id.tv_no_recents);
        btnClearAllRecents = findViewById(R.id.btn_clear_all_recents);
        btnCloseRecents = findViewById(R.id.btn_close_recents);

        btnVolumeQuick = findViewById(R.id.btn_volume_quick);
        btnNotificationBell = findViewById(R.id.btn_notification_bell);
        viewNotificationDot = findViewById(R.id.view_notification_dot);

        layoutTimeUpOverlay = findViewById(R.id.layout_time_up_overlay);
        layoutVolumeHud = findViewById(R.id.layout_volume_hud);
        pbVolumeHud = findViewById(R.id.pb_volume_hud);
        tvHudPercentage = findViewById(R.id.tv_hud_percentage);
        tvHudSpeakerIcon = findViewById(R.id.tv_hud_speaker_icon);
        layoutControlCenterContainer = findViewById(R.id.layout_control_center_container);

        sbBrightness = findViewById(R.id.sb_brightness);
        sbVolume = findViewById(R.id.sb_volume);
        tvNotifBatteryText = findViewById(R.id.tv_notif_battery_text);
        tvNotifTimeText = findViewById(R.id.tv_notif_time_text);

        btnAutoBrightnessToggle = findViewById(R.id.btn_auto_brightness_toggle);
        tvSurroundingLightStatus = findViewById(R.id.tv_surrounding_light_status);

        rvKidsApps = findViewById(R.id.rv_kids_apps);

        chipAll = findViewById(R.id.chip_all);
        chipGames = findViewById(R.id.chip_games);
        chipCreative = findViewById(R.id.chip_creative);
        chipMedia = findViewById(R.id.chip_media);
        chipLearning = findViewById(R.id.chip_learning);

        View btnBoostRam = findViewById(R.id.btn_boost_ram);
        if (btnBoostRam != null) {
            btnBoostRam.setOnClickListener(v -> performBoost());
        }

        if (btnVolumeQuick != null) btnVolumeQuick.setOnClickListener(v -> showVolumeHud(getCurrentVolumePercent()));
        if (btnNotificationBell != null) btnNotificationBell.setOnClickListener(v -> toggleControlCenter(true));
        if (ivWifiStatus != null) ivWifiStatus.setOnClickListener(v -> toggleControlCenter(true));
        if (layoutBatteryCapsule != null) {
            layoutBatteryCapsule.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, MelodyBatteryActivity.class));
            });
        }
        View btnCloseControlCenter = findViewById(R.id.btn_close_control_center);
        if (btnCloseControlCenter != null) btnCloseControlCenter.setOnClickListener(v -> toggleControlCenter(false));
        if (layoutControlCenterContainer != null) layoutControlCenterContainer.setOnClickListener(v -> toggleControlCenter(false));

        if (btnCloseRecents != null) btnCloseRecents.setOnClickListener(v -> closeRecentsOverlay());
        if (layoutRecentsOverlay != null) layoutRecentsOverlay.setOnClickListener(v -> closeRecentsOverlay());
        if (btnClearAllRecents != null) btnClearAllRecents.setOnClickListener(v -> clearAllRecents());
    }

    private void performBoost() {
        View btnBoostRam = findViewById(R.id.btn_boost_ram);
        TextView tvBoostIcon = findViewById(R.id.tv_boost_icon);
        TextView tvBoostText = findViewById(R.id.tv_boost_text);

        if (btnBoostRam != null) {
            btnBoostRam.animate().scaleX(1.18f).scaleY(1.18f).setDuration(150).withEndAction(() -> {
                btnBoostRam.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }).start();
        }

        if (tvBoostIcon != null) {
            tvBoostIcon.animate().rotationBy(360f).setDuration(400).start();
        }

        long freedMb = DeviceBooster.boostAndGetFreedMb(this);

        if (tvBoostText != null) {
            tvBoostText.setText("+" + freedMb + "MB ⚡");
            tvBoostText.postDelayed(() -> tvBoostText.setText("Boost ⚡"), 3500);
        }

        Toast.makeText(this, "🚀 Speed Boosted! Freed " + freedMb + " MB RAM — Device is super smooth! 🌸✨", Toast.LENGTH_SHORT).show();
    }

    private void setupControlCenter() {
        // Quick Settings Tiles
        layoutQuickWifi = findViewById(R.id.layout_quick_wifi);
        tvQuickWifiIcon = findViewById(R.id.tv_quick_wifi_icon);
        tvQuickWifiLabel = findViewById(R.id.tv_quick_wifi_label);

        layoutQuickBluetooth = findViewById(R.id.layout_quick_bluetooth);
        tvQuickBtIcon = findViewById(R.id.tv_quick_bt_icon);
        tvQuickBtLabel = findViewById(R.id.tv_quick_bt_label);

        layoutQuickGps = findViewById(R.id.layout_quick_gps);
        tvQuickGpsIcon = findViewById(R.id.tv_quick_gps_icon);
        tvQuickGpsLabel = findViewById(R.id.tv_quick_gps_label);

        layoutQuickTorch = findViewById(R.id.layout_quick_torch);
        tvQuickTorchIcon = findViewById(R.id.tv_quick_torch_icon);
        tvQuickTorchLabel = findViewById(R.id.tv_quick_torch_label);

        layoutQuickScreenshot = findViewById(R.id.layout_quick_screenshot);
        tvQuickScreenshotIcon = findViewById(R.id.tv_quick_screenshot_icon);
        tvQuickScreenshotLabel = findViewById(R.id.tv_quick_screenshot_label);

        layoutQuickRotation = findViewById(R.id.layout_quick_rotation);
        tvQuickRotationIcon = findViewById(R.id.tv_quick_rotation_icon);
        tvQuickRotationLabel = findViewById(R.id.tv_quick_rotation_label);

        if (layoutQuickWifi != null) {
            layoutQuickWifi.setOnClickListener(v -> {
                try {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        boolean current = wm.isWifiEnabled();
                        boolean next = !current;

                        layoutQuickWifi.setBackgroundResource(next ? R.drawable.bg_melody_quick_tile_active : R.drawable.bg_melody_quick_tile_inactive);
                        if (tvQuickWifiLabel != null) {
                            tvQuickWifiLabel.setText(next ? "Enabling..." : "Disabling...");
                            tvQuickWifiLabel.setTextColor(next ? Color.WHITE : Color.parseColor("#831843"));
                        }

                        boolean ok = wm.setWifiEnabled(next);
                        if (!ok) {
                            try {
                                Settings.Global.putInt(getContentResolver(), Settings.Global.WIFI_ON, next ? 1 : 0);
                            } catch (Exception ignored) {}
                            try {
                                Runtime.getRuntime().exec("svc wifi " + (next ? "enable" : "disable"));
                            } catch (Exception ignored) {}
                        }
                        Toast.makeText(this, next ? "Wi-Fi Enabled 🌸" : "Wi-Fi Disabled", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Failed to toggle Wi-Fi", e);
                }
                new Handler(Looper.getMainLooper()).postDelayed(this::updateQuickTilesUi, 1000);
            });
        }

        if (layoutQuickBluetooth != null) {
            layoutQuickBluetooth.setOnClickListener(v -> {
                try {
                    BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
                    if (ba != null) {
                        boolean isBtOn = ba.isEnabled();
                        boolean next = !isBtOn;

                        layoutQuickBluetooth.setBackgroundResource(next ? R.drawable.bg_melody_quick_tile_active : R.drawable.bg_melody_quick_tile_inactive);
                        if (tvQuickBtLabel != null) {
                            tvQuickBtLabel.setText(next ? "Enabling..." : "Disabling...");
                            tvQuickBtLabel.setTextColor(next ? Color.WHITE : Color.parseColor("#831843"));
                        }

                        if (isBtOn) {
                            ba.disable();
                            Toast.makeText(this, "Bluetooth Disabled", Toast.LENGTH_SHORT).show();
                        } else {
                            ba.enable();
                            Toast.makeText(this, "Bluetooth Enabled 🌸", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Failed to toggle Bluetooth", e);
                }
                new Handler(Looper.getMainLooper()).postDelayed(this::updateQuickTilesUi, 800);
            });
        }

        if (layoutQuickGps != null) {
            layoutQuickGps.setOnClickListener(v -> {
                try {
                    LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    boolean isGpsOn = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    boolean next = !isGpsOn;

                    // Direct toggle using WRITE_SECURE_SETTINGS - never open Android settings!
                    Settings.Secure.putString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED, next ? "+gps" : "-gps");
                    Toast.makeText(this, next ? "Location Enabled 📍🌸" : "Location Disabled", Toast.LENGTH_SHORT).show();
                    updateQuickTilesUi();
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Failed to toggle Location", e);
                }
            });
        }

        if (layoutQuickTorch != null) {
            layoutQuickTorch.setOnClickListener(v -> {
                toggleFlashlight();
                updateQuickTilesUi();
            });
        }

        if (layoutQuickScreenshot != null) {
            layoutQuickScreenshot.setOnClickListener(v -> {
                toggleControlCenter(false);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (MelodyGlobalService.getInstance() != null) {
                        MelodyGlobalService.getInstance().takeGlobalScreenshot();
                    } else {
                        Toast.makeText(this, "Screenshot captured 📸", Toast.LENGTH_SHORT).show();
                    }
                }, 400);
            });
        }

        if (layoutQuickRotation != null) {
            layoutQuickRotation.setOnClickListener(v -> {
                int cur = getRequestedOrientation();
                if (cur == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    Toast.makeText(this, "Orientation: Locked Landscape 🔒", Toast.LENGTH_SHORT).show();
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    Toast.makeText(this, "Orientation: Auto-Rotate (Landscape) 🔄", Toast.LENGTH_SHORT).show();
                }
                updateQuickTilesUi();
            });
        }

        // Volume Slider
        if (audioManager != null) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            sbVolume.setMax(max);
            sbVolume.setProgress(current);
            sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && audioManager != null) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                        showVolumeHud((int) (progress * 100.0f / max));
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Auto-Brightness initial state
        isAutoBrightness = prefs.isAutoBrightness();
        updateAutoBrightnessUi();

        if (btnAutoBrightnessToggle != null) {
            btnAutoBrightnessToggle.setOnClickListener(v -> {
                setAutoBrightnessState(!isAutoBrightness);
            });
        }

        // Brightness Slider
        sbBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean userIsDragging = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && userIsDragging) {
                    setAutoBrightnessState(false);
                    setScreenBrightness(Math.max(10, progress) / 100.0f);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userIsDragging = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userIsDragging = false;
            }
        });

        // Turbo Boost RAM button
        TextView btnBoost = findViewById(R.id.btn_control_center_boost);
        TextView tvBoostStatus = findViewById(R.id.tv_boost_status);
        if (btnBoost != null) {
            btnBoost.setOnClickListener(v -> {
                DeviceBooster.boost(this);
                if (tvBoostStatus != null) {
                    tvBoostStatus.setText(DeviceBooster.getAvailableMemoryMb(this));
                }
                Toast.makeText(this, "🚀 Speed Boosted! Cleared background memory ✨", Toast.LENGTH_SHORT).show();
            });
        }

        updateQuickTilesUi();
    }

    private void updateQuickTilesUi() {
        // Wi-Fi
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            boolean isWifiOn = wm != null && wm.isWifiEnabled();
            if (layoutQuickWifi != null) {
                layoutQuickWifi.setBackgroundResource(isWifiOn ? R.drawable.bg_melody_quick_tile_active : R.drawable.bg_melody_quick_tile_inactive);
            }
            if (tvQuickWifiLabel != null) {
                tvQuickWifiLabel.setText(isWifiOn ? "Wi-Fi ON" : "Wi-Fi");
                tvQuickWifiLabel.setTextColor(isWifiOn ? Color.WHITE : Color.parseColor("#831843"));
            }
        } catch (Exception ignored) {}

        // Bluetooth
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            boolean isBtOn = ba != null && ba.isEnabled();
            if (layoutQuickBluetooth != null) {
                layoutQuickBluetooth.setBackgroundResource(isBtOn ? R.drawable.bg_melody_quick_tile_active : R.drawable.bg_melody_quick_tile_inactive);
            }
            if (tvQuickBtLabel != null) {
                tvQuickBtLabel.setText(isBtOn ? "Bluetooth ON" : "Bluetooth");
                tvQuickBtLabel.setTextColor(isBtOn ? Color.WHITE : Color.parseColor("#831843"));
            }
        } catch (Exception ignored) {}

        // Location / GPS
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            boolean isGpsOn = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (layoutQuickGps != null) {
                layoutQuickGps.setBackgroundResource(isGpsOn ? R.drawable.bg_melody_quick_tile_active : R.drawable.bg_melody_quick_tile_inactive);
            }
            if (tvQuickGpsLabel != null) {
                tvQuickGpsLabel.setText(isGpsOn ? "Location ON" : "Location");
                tvQuickGpsLabel.setTextColor(isGpsOn ? Color.WHITE : Color.parseColor("#831843"));
            }
        } catch (Exception ignored) {}

        // Torch
        if (layoutQuickTorch != null) {
            layoutQuickTorch.setBackgroundResource(isTorchOn ? R.drawable.bg_melody_quick_tile_active : R.drawable.bg_melody_quick_tile_inactive);
        }
        if (tvQuickTorchLabel != null) {
            tvQuickTorchLabel.setText(isTorchOn ? "Torch ON" : "Flashlight");
            tvQuickTorchLabel.setTextColor(isTorchOn ? Color.WHITE : Color.parseColor("#831843"));
        }

        // Screen Rotation
        int orient = getRequestedOrientation();
        boolean isAutoRotate = (orient == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        if (layoutQuickRotation != null) {
            layoutQuickRotation.setBackgroundResource(isAutoRotate ? R.drawable.bg_melody_quick_tile_active : R.drawable.bg_melody_quick_tile_inactive);
        }
        if (tvQuickRotationLabel != null) {
            tvQuickRotationLabel.setText(isAutoRotate ? "Auto-Rotate" : "Locked");
            tvQuickRotationLabel.setTextColor(isAutoRotate ? Color.WHITE : Color.parseColor("#831843"));
        }
    }

    private void toggleFlashlight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (cm != null) {
                    String[] ids = cm.getCameraIdList();
                    if (ids != null && ids.length > 0) {
                        isTorchOn = !isTorchOn;
                        cm.setTorchMode(ids[0], isTorchOn);
                        Toast.makeText(this, isTorchOn ? "Flashlight ON 🔦" : "Flashlight OFF", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }
        Toast.makeText(this, "Flashlight not supported on this device", Toast.LENGTH_SHORT).show();
    }

    private void updateAutoBrightnessUi() {
        if (btnAutoBrightnessToggle == null) return;
        if (isAutoBrightness) {
            btnAutoBrightnessToggle.setText("✨ Auto: ON");
            btnAutoBrightnessToggle.setBackgroundResource(R.drawable.bg_melody_chip_selected);
            btnAutoBrightnessToggle.setTextColor(Color.WHITE);
        } else {
            btnAutoBrightnessToggle.setText("Manual Mode");
            btnAutoBrightnessToggle.setBackgroundResource(R.drawable.bg_melody_card);
            btnAutoBrightnessToggle.setTextColor(Color.parseColor("#831843"));
            if (tvSurroundingLightStatus != null) {
                tvSurroundingLightStatus.setText("Manual brightness set");
            }
        }
    }

    private void setAutoBrightnessState(boolean enabled) {
        isAutoBrightness = enabled;
        prefs.setAutoBrightness(enabled);
        updateAutoBrightnessUi();
        if (enabled) {
            if (sensorManager != null && lightSensor != null) {
                sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
            Toast.makeText(this, "Auto-Brightness enabled (Ambient Light)", Toast.LENGTH_SHORT).show();
        } else {
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            Toast.makeText(this, "Manual brightness enabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void animateBrightnessTo(float target) {
        float clamped = Math.max(0.18f, Math.min(1.0f, target));
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float start = lp.screenBrightness > 0 ? lp.screenBrightness : 0.70f;

        if (brightnessAnimator != null && brightnessAnimator.isRunning()) {
            brightnessAnimator.cancel();
        }

        brightnessAnimator = ValueAnimator.ofFloat(start, clamped);
        brightnessAnimator.setDuration(450);
        brightnessAnimator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            lp.screenBrightness = val;
            getWindow().setAttributes(lp);
            try {
                int systemBrightness = (int) (val * 255);
                android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, systemBrightness);
            } catch (Exception ignored) {}
            if (sbBrightness != null && isAutoBrightness) {
                sbBrightness.setProgress((int) (val * 100));
            }
        });
        brightnessAnimator.start();
    }

    private void toggleControlCenter(boolean show) {
        layoutControlCenterContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            updateQuickTilesUi();
            if (viewNotificationDot != null) viewNotificationDot.setVisibility(View.GONE);
            int remainingSecs = prefs.getRemainingSeconds();
            if (prefs.getTimeLimitMinutes() <= 0) {
                tvNotifTimeText.setText("Daily screen time: Unlimited today!");
            } else {
                tvNotifTimeText.setText("Screen time remaining: " + (remainingSecs / 60) + " minutes");
            }
        }
    }

    private void setScreenBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightness;
        getWindow().setAttributes(lp);
        try {
            int systemBrightness = (int) (Math.max(0.10f, Math.min(1.0f, brightness)) * 255);
            android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, systemBrightness);
        } catch (Exception ignored) {}
    }

    // Auto Brightness from Hardware Ambient Light Sensor
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isAutoBrightness || event.sensor.getType() != Sensor.TYPE_LIGHT) return;
        float lux = event.values[0];
        float target;
        String desc;
        if (lux < 20) {
            target = 0.28f;
            desc = "🌙 Dim (" + (int)lux + " lx)";
        } else if (lux < 120) {
            target = 0.55f;
            desc = "💡 Soft (" + (int)lux + " lx)";
        } else if (lux < 400) {
            target = 0.80f;
            desc = "☀️ Bright (" + (int)lux + " lx)";
        } else {
            target = 0.98f;
            desc = "✨ Sunlit (" + (int)lux + " lx)";
        }
        animateBrightnessTo(target);
        if (tvSurroundingLightStatus != null) {
            tvSurroundingLightStatus.setText("Ambient: " + desc);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // Volume Keys Interception for Floating Animated HUD
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (audioManager != null) {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int cap = (prefs != null && prefs.isVolumeLimiterEnabled()) ? (int) (max * (prefs.getVolumeCapPercent() / 100.0f)) : max;
                if (current >= cap) {
                    Toast.makeText(this, "🎧 Ear Protection: Max volume capped at " + (prefs != null ? prefs.getVolumeCapPercent() : 70) + "%! 💕", Toast.LENGTH_SHORT).show();
                    showVolumeHud(getCurrentVolumePercent());
                    return true;
                }
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                showVolumeHud(getCurrentVolumePercent());
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                showVolumeHud(getCurrentVolumePercent());
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int getCurrentVolumePercent() {
        if (audioManager == null) return 50;
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        return (int) (current * 100.0f / max);
    }

    private void showVolumeHud(int percent) {
        hudHandler.removeCallbacks(hudDismissRunnable);
        pbVolumeHud.setProgress(percent);
        tvHudPercentage.setText(percent + "%");

        if (percent == 0) {
            tvHudSpeakerIcon.setText("🔇");
        } else if (percent < 50) {
            tvHudSpeakerIcon.setText("🔉");
        } else {
            tvHudSpeakerIcon.setText("🔊");
        }

        if (layoutVolumeHud.getVisibility() != View.VISIBLE) {
            layoutVolumeHud.setVisibility(View.VISIBLE);
            layoutVolumeHud.setAlpha(0f);
            layoutVolumeHud.setTranslationY(-40f);
            layoutVolumeHud.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .start();
        }

        hudDismissRunnable = () -> {
            layoutVolumeHud.animate()
                    .alpha(0f)
                    .translationY(-40f)
                    .setDuration(220)
                    .withEndAction(() -> layoutVolumeHud.setVisibility(View.GONE))
                    .start();
        };
        hudHandler.postDelayed(hudDismissRunnable, 2000);
    }

    private void setupBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING 
                        || status == BatteryManager.BATTERY_STATUS_FULL;

                int pct = (int) (level * 100.0f / scale);
                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean isFastCharger = (plugged == BatteryManager.BATTERY_PLUGGED_AC);

                if (gaugeBattery != null) {
                    gaugeBattery.setBatteryStatus(pct, isCharging, isFastCharger);
                }

                // Auto Battery Saver (< 20%)
                if (pct < 20 && !isCharging) {
                    if (prefs.isAutoBatterySaverEnabled() && !prefs.isBatterySaverActive()) {
                        prefs.setBatterySaverActive(true);
                        DeviceBooster.applyBatterySaver(context, true);
                        setScreenBrightness(0.25f);
                        Toast.makeText(context, "🪫 Low Battery (" + pct + "%)! Battery Saver mode enabled 🌸", Toast.LENGTH_LONG).show();
                    }
                } else if (isCharging || pct >= 20) {
                    if (prefs.isBatterySaverActive()) {
                        prefs.setBatterySaverActive(false);
                        DeviceBooster.applyBatterySaver(context, false);
                    }
                }

                // Full Battery Sound Notice (100%)
                if (pct >= 100 && isCharging) {
                    if (prefs.isFullBatteryAlertEnabled() && !hasAlertedFullBattery) {
                        hasAlertedFullBattery = true;
                        playFullBatteryAlertSound();
                        Toast.makeText(context, "🔋 Battery is 100% Full! You can unplug your tablet now 🌸⚡", Toast.LENGTH_LONG).show();
                    }
                } else if (pct < 98 || !isCharging) {
                    hasAlertedFullBattery = false;
                }

                if (tvBatteryPercent != null) {
                    tvBatteryPercent.setText(pct + "%");
                    if (pct <= 20 && !isCharging) {
                        tvBatteryPercent.setTextColor(Color.parseColor("#E11D48"));
                    } else {
                        tvBatteryPercent.setTextColor(Color.parseColor("#831843"));
                    }
                }

                if (tvChargingSparkle != null) {
                    if (isCharging) {
                        tvChargingSparkle.setVisibility(View.VISIBLE);
                        if (chargingSparkleAnimator == null) {
                            chargingSparkleAnimator = ObjectAnimator.ofPropertyValuesHolder(
                                    tvChargingSparkle,
                                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.35f),
                                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.35f),
                                    PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 1.0f)
                            );
                            chargingSparkleAnimator.setDuration(800);
                            chargingSparkleAnimator.setRepeatCount(ValueAnimator.INFINITE);
                            chargingSparkleAnimator.setRepeatMode(ValueAnimator.REVERSE);
                            chargingSparkleAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                        }
                        if (!chargingSparkleAnimator.isRunning()) {
                            chargingSparkleAnimator.start();
                        }
                    } else {
                        tvChargingSparkle.setVisibility(View.GONE);
                        if (chargingSparkleAnimator != null && chargingSparkleAnimator.isRunning()) {
                            chargingSparkleAnimator.cancel();
                            tvChargingSparkle.setScaleX(1.0f);
                            tvChargingSparkle.setScaleY(1.0f);
                            tvChargingSparkle.setAlpha(1.0f);
                        }
                    }
                }

                if (tvNotifBatteryText != null) {
                    if (isCharging) {
                        tvNotifBatteryText.setText("⚡ Melody Tablet is charging (" + pct + "%) ⚡");
                    } else if (pct <= 20) {
                        tvNotifBatteryText.setText("🪫 Battery low (" + pct + "%). Time to plug in! 🔌");
                    } else {
                        tvNotifBatteryText.setText("🔋 Battery is healthy (" + pct + "%). Ready to play!");
                    }
                }
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void playFullBatteryAlertSound() {
        try {
            android.media.ToneGenerator toneGen = new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100);
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 500);
        } catch (Exception ignored) {}
    }

    private void applyWallpaper() {
        if (ivMelodyBg != null && prefs != null) {
            ivMelodyBg.setImageDrawable(WallpaperHelper.getWallpaperDrawable(prefs.getSelectedWallpaper()));
        }
    }

    private void updateGreeting() {
        if (tvGreeting != null && prefs != null) {
            tvGreeting.setText("Hi, " + prefs.getKidName() + "! 🌸");
        }
    }

    private void triggerSosCall() {
        String name = prefs.getSosContactName();
        String phone = prefs.getSosPhoneNumber();
        new AlertDialog.Builder(this)
                .setTitle("📞 Call " + name + "?")
                .setMessage("Do you want to call " + name + " right now? 💕")
                .setPositiveButton("Call 📞", (dialog, which) -> {
                    try {
                        Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                        if (!TextUtils.isEmpty(phone)) {
                            dialIntent.setData(Uri.parse("tel:" + phone));
                        }
                        startActivity(dialIntent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not open phone dialer", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void launchApp(AppModel app) {
        if (app == null) return;

        if (prefs.isPackageLocked(app.getPackageName())) {
            showLockedAppDialog(app);
            return;
        }

        executeAppLaunch(app);
    }

    private void showLockedAppDialog(AppModel app) {
        MelodyPinPadDialog.show(this, "Unlock " + app.getLabel() + " 🔒", "Enter parent PIN to open this app", () -> {
            executeAppLaunch(app);
        });
    }

    private void executeAppLaunch(AppModel app) {
        // Auto-boost device RAM and eliminate lag before launching any app
        long freed = DeviceBooster.boostAndGetFreedMb(this, app.getPackageName());
        Toast.makeText(this, "🚀 Auto-Boost: " + freed + " MB RAM cleared for " + app.getLabel() + "! 🌸", Toast.LENGTH_SHORT).show();

        addRecentApp(app);
        try {
            // Check for built-in Melody apps
            if (getPackageName().equals(app.getPackageName())) {
                Intent builtIn = null;
                if (MelodyCameraActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyCameraActivity.class);
                } else if (MelodyMusicActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyMusicActivity.class);
                } else if (MelodyCalculatorActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyCalculatorActivity.class);
                } else if (MelodyClockActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyClockActivity.class);
                } else if (MelodyGalleryActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyGalleryActivity.class);
                } else if (MelodyBatteryActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyBatteryActivity.class);
                } else if (MelodySafeBrowserActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodySafeBrowserActivity.class);
                } else if (MelodyFileManagerActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyFileManagerActivity.class);
                } else if (MelodyVideoActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyVideoActivity.class);
                } else if (MelodyQuickShareActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyQuickShareActivity.class);
                } else if (MelodyUpdaterActivity.class.getName().equals(app.getActivityName())) {
                    builtIn = new Intent(this, MelodyUpdaterActivity.class);
                }

                if (builtIn != null) {
                    startActivity(builtIn);
                    overridePendingTransition(R.anim.melody_app_open_enter, R.anim.melody_app_open_exit);
                    return;
                }
            }

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(app.getPackageName(), app.getActivityName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            overridePendingTransition(R.anim.melody_app_open_enter, R.anim.melody_app_open_exit);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open " + app.getLabel(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addRecentApp(AppModel app) {
        if (app == null) return;
        for (int i = 0; i < recentApps.size(); i++) {
            if (recentApps.get(i).getPackageName().equals(app.getPackageName())) {
                recentApps.remove(i);
                break;
            }
        }
        recentApps.add(0, app);
        if (recentApps.size() > 8) {
            recentApps.remove(recentApps.size() - 1);
        }
    }

    private void toggleRecentsOverlay() {
        if (layoutRecentsOverlay == null) return;
        if (layoutRecentsOverlay.getVisibility() == View.VISIBLE) {
            closeRecentsOverlay();
        } else {
            showRecentsOverlay();
        }
    }

    private void showRecentsOverlay() {
        if (layoutControlCenterContainer != null && layoutControlCenterContainer.getVisibility() == View.VISIBLE) {
            toggleControlCenter(false);
        }

        // Auto seed with allowed apps if recent stack is currently empty
        if (recentApps.isEmpty() && !allAllowedApps.isEmpty()) {
            for (int i = 0; i < Math.min(4, allAllowedApps.size()); i++) {
                recentApps.add(allAllowedApps.get(i));
            }
        }

        populateRecentCards();

        layoutRecentsOverlay.setVisibility(View.VISIBLE);
        layoutRecentsOverlay.setAlpha(0f);
        layoutRecentsOverlay.animate().alpha(1f).setDuration(200).start();
    }

    private void closeRecentsOverlay() {
        if (layoutRecentsOverlay == null) return;
        layoutRecentsOverlay.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> layoutRecentsOverlay.setVisibility(View.GONE))
                .start();
    }

    private void populateRecentCards() {
        if (layoutRecentAppsContainer == null) return;
        layoutRecentAppsContainer.removeAllViews();
        if (recentApps.isEmpty()) {
            if (tvNoRecents != null) tvNoRecents.setVisibility(View.VISIBLE);
            if (btnClearAllRecents != null) {
                btnClearAllRecents.setEnabled(false);
                btnClearAllRecents.setAlpha(0.5f);
            }
            return;
        }

        if (tvNoRecents != null) tvNoRecents.setVisibility(View.GONE);
        if (btnClearAllRecents != null) {
            btnClearAllRecents.setEnabled(true);
            btnClearAllRecents.setAlpha(1.0f);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AppModel app : recentApps) {
            View card = inflater.inflate(R.layout.item_recent_app_card, layoutRecentAppsContainer, false);
            ImageView ivIcon = card.findViewById(R.id.iv_recent_icon);
            TextView tvName = card.findViewById(R.id.tv_recent_name);
            TextView tvBadge = card.findViewById(R.id.tv_recent_badge);
            View btnDismiss = card.findViewById(R.id.btn_dismiss_recent);

            ivIcon.setImageDrawable(app.getIcon());
            tvName.setText(app.getLabel());
            tvBadge.setText("⭐ " + app.getCategory());

            card.setOnClickListener(v -> {
                closeRecentsOverlay();
                launchApp(app);
            });

            btnDismiss.setOnClickListener(v -> {
                card.animate()
                        .scaleX(0f)
                        .scaleY(0f)
                        .alpha(0f)
                        .setDuration(180)
                        .withEndAction(() -> {
                            layoutRecentAppsContainer.removeView(card);
                            recentApps.remove(app);
                            if (recentApps.isEmpty()) {
                                if (tvNoRecents != null) tvNoRecents.setVisibility(View.VISIBLE);
                                if (btnClearAllRecents != null) {
                                    btnClearAllRecents.setEnabled(false);
                                    btnClearAllRecents.setAlpha(0.5f);
                                }
                            }
                        })
                        .start();
            });

            layoutRecentAppsContainer.addView(card);
        }
    }

    private void clearAllRecents() {
        if (layoutRecentAppsContainer == null) return;
        int count = layoutRecentAppsContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = layoutRecentAppsContainer.getChildAt(i);
            child.animate()
                    .translationY(-60f)
                    .alpha(0f)
                    .setDuration(160 + (i * 30L))
                    .start();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            recentApps.clear();
            if (layoutRecentAppsContainer != null) layoutRecentAppsContainer.removeAllViews();
            if (tvNoRecents != null) tvNoRecents.setVisibility(View.VISIBLE);
            if (btnClearAllRecents != null) {
                btnClearAllRecents.setEnabled(false);
                btnClearAllRecents.setAlpha(0.5f);
            }
            Toast.makeText(MainActivity.this, "🧹 All open windows cleared!", Toast.LENGTH_SHORT).show();
            closeRecentsOverlay();
        }, 260);
    }

    private void setupCategoryChips() {
        if (chipAll == null) return;
        if (chipGames != null) {
            chipGames.setText("🎮 Games");
        }
        chipAll.setOnClickListener(v -> selectCategory(AppModel.CAT_ALL, chipAll));
        if (chipGames != null) chipGames.setOnClickListener(v -> selectCategory(AppModel.CAT_GAMES, chipGames));
        if (chipCreative != null) chipCreative.setOnClickListener(v -> selectCategory(AppModel.CAT_CREATIVE, chipCreative));
        if (chipMedia != null) chipMedia.setOnClickListener(v -> selectCategory(AppModel.CAT_MEDIA, chipMedia));
        if (chipLearning != null) chipLearning.setOnClickListener(v -> selectCategory(AppModel.CAT_LEARNING, chipLearning));
    }

    private void selectCategory(String category, TextView selectedChip) {
        currentCategory = category;
        TextView[] chips = {chipAll, chipGames, chipCreative, chipMedia, chipLearning};
        for (TextView chip : chips) {
            if (chip != null) {
                if (chip == selectedChip) {
                    chip.setBackgroundResource(R.drawable.bg_melody_chip_selected);
                    chip.setTextColor(Color.WHITE);
                } else {
                    chip.setBackgroundResource(R.drawable.bg_melody_chip_unselected);
                    chip.setTextColor(Color.parseColor("#9D174D"));
                }
            }
        }
        filterAppsByCategory();
    }

    private void filterAppsByCategory() {
        displayedApps.clear();
        for (AppModel app : allAllowedApps) {
            if (app.matchesCategory(currentCategory)) {
                displayedApps.add(app);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        setupPageDots();
    }

    private void setupPageDots() {
        if (layoutPageDots == null) return;
        layoutPageDots.removeAllViews();
        int totalApps = displayedApps.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalApps / 10.0));
        if (totalPages <= 1) {
            layoutPageDots.setVisibility(View.GONE);
            return;
        }
        layoutPageDots.setVisibility(View.VISIBLE);
        int dotSize = (int) (10 * getResources().getDisplayMetrics().density);
        int dotMargin = (int) (5 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < totalPages; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.setMargins(dotMargin, 0, dotMargin, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == 0 ? R.drawable.dot_selected : R.drawable.dot_unselected);
            layoutPageDots.addView(dot);
        }
    }

    private void updatePageDots(GridLayoutManager layoutManager) {
        if (layoutPageDots == null || layoutPageDots.getChildCount() <= 1) return;
        int firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition();
        if (firstVisible < 0) firstVisible = layoutManager.findFirstVisibleItemPosition();
        int currentPage = Math.max(0, firstVisible / 10);
        int childCount = layoutPageDots.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View dot = layoutPageDots.getChildAt(i);
            if (dot != null) {
                dot.setBackgroundResource(i == currentPage ? R.drawable.dot_selected : R.drawable.dot_unselected);
            }
        }
    }

    private final Handler navBarHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideNavBarRunnable = this::hideSystemNavBar;

    private void hideSystemNavBar() {
        View decorView = getWindow().getDecorView();
        if (decorView != null) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void scheduleNavBarAutoHide() {
        navBarHandler.removeCallbacks(hideNavBarRunnable);
        navBarHandler.postDelayed(hideNavBarRunnable, 3500); // Auto hide after 3.5 seconds
    }

    private void setupNavBarAutoHide() {
        try {
            android.provider.Settings.Global.putString(getContentResolver(), "policy_control", "immersive.navigation=*");
        } catch (Exception ignored) {}

        View decorView = getWindow().getDecorView();
        if (decorView != null) {
            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                    scheduleNavBarAutoHide();
                }
            });
        }
        hideSystemNavBar();
        scheduleNavBarAutoHide();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        scheduleNavBarAutoHide();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownY = ev.getRawY();
                touchDownX = ev.getRawX();
                break;
            case MotionEvent.ACTION_UP:
                float deltaY = ev.getRawY() - touchDownY;
                float deltaX = Math.abs(ev.getRawX() - touchDownX);
                if (touchDownY < 120 && deltaY > 80 && deltaY > deltaX * 1.3f) {
                    toggleControlCenter(true);
                    return true;
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void ensureMelodyGlobalServiceEnabled() {
        try {
            String serviceName = getPackageName() + "/" + MelodyGlobalService.class.getName();
            String enabled = android.provider.Settings.Secure.getString(getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || !enabled.contains(serviceName)) {
                String newServices = (enabled == null || enabled.isEmpty()) ? serviceName : enabled + ":" + serviceName;
                android.provider.Settings.Secure.putString(getContentResolver(),
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices);
                android.provider.Settings.Secure.putString(getContentResolver(),
                        android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureMelodyGlobalServiceEnabled();
        setupNavBarAutoHide();

        // At launcher: auto hide floating battery (launcher top status bar has its own battery gauge)
        if (MelodyGlobalService.getInstance() != null) {
            MelodyGlobalService.getInstance().setFloatingBatteryVisible(false);
        }

        applyWallpaper();
        updateGreeting();
        if (tvAvatarBadge != null) {
            tvAvatarBadge.setText(prefs.getAvatar());
        }
        isAutoBrightness = prefs.isAutoBrightness();
        updateAutoBrightnessUi();
        if (isAutoBrightness) {
            if (sensorManager != null && lightSensor != null) {
                sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(lp);
        }
        updateQuickTilesUi();
        loadAllowedApps();
        checkPlaytimeState();

        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null && gaugeBattery != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            int pct = (int) (level * 100.0f / scale);
            int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            boolean isFastCharger = (plugged == BatteryManager.BATTERY_PLUGGED_AC);
            gaugeBattery.setBatteryStatus(pct, isCharging, isFastCharger);
            if (tvBatteryPercent != null) tvBatteryPercent.setText(pct + "%");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && ACTION_SHOW_QUICK_SETTINGS.equals(intent.getAction())) {
            toggleControlCenter(true);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupNavBarAutoHide();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Show floating battery when exiting launcher to open another app
        if (MelodyGlobalService.getInstance() != null) {
            MelodyGlobalService.getInstance().setFloatingBatteryVisible(true);
        }

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void initDefaultWhitelistIfNeeded() {
        // Strip barbiecore and iWawa from allowed packages
        prefs.removeAllowedPackage("com.y8.barbiecore");
        prefs.removeAllowedPackage("com.sencatech.iwawa.iwawahome");
        prefs.removeAllowedPackage("com.sencatech.iwawa.iwawadraw");

        // Ensure modded and kids YouTube are allowed
        prefs.addAllowedPackage("app.morphe.android.youtube");
        prefs.addAllowedPackage("com.google.android.apps.youtube.kids");

        if (!prefs.isInitialized()) {
            Set<String> defaults = new HashSet<>();
            defaults.add("app.morphe.android.youtube");
            defaults.add("com.google.android.apps.youtube.kids");
            defaults.add("com.android.calculator2");
            defaults.add("com.android.gallery3d");
            defaults.add("com.android.music");
            defaults.add("com.android.deskclock");

            prefs.setAllowedPackages(defaults);
            prefs.setInitialized(true);
        }
    }

    private void loadAllowedApps() {
        allAllowedApps.clear();
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);
        Set<String> allowedPackages = prefs.getAllowedPackages();
        boolean lockGames = prefs.isLockGames();
        boolean lockBrowsers = prefs.isLockBrowsers();

        for (ResolveInfo resolveInfo : pkgAppsList) {
            String pkg = resolveInfo.activityInfo.packageName;
            if ("com.y8.barbiecore".equals(pkg) || pkg.toLowerCase(Locale.US).contains("iwawa")) continue; // Never load Barbiecore or iWawa

            // Suppress stock AOSP duplicates if using cute Melody versions
            if ("com.android.calculator2".equals(pkg) || "com.android.deskclock".equals(pkg)
                    || "com.android.gallery3d".equals(pkg) || "com.android.music".equals(pkg)
                    || "com.android.camera".equals(pkg) || "com.android.camera2".equals(pkg)) {
                continue;
            }

            if (prefs.isPackagePendingApproval(pkg)) {
                continue; // Requires parent approval before appearing on kids launcher
            }

            if (allowedPackages.contains(pkg) && !pkg.equals(getPackageName())) {
                String label = resolveInfo.loadLabel(pm).toString();
                String activity = resolveInfo.activityInfo.name;

                android.graphics.drawable.Drawable icon = resolveInfo.loadIcon(pm);
                // Override modded YouTube with original official YouTube logo, but let YouTube Kids use its own logo!
                boolean isYtKids = pkg.contains("youtube.kids") || label.toLowerCase().contains("kids");
                if (!isYtKids && (pkg.contains("morphe") || pkg.contains("youtube") || label.toLowerCase().contains("youtube"))) {
                    icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_youtube_official);
                }

                AppModel app = new AppModel(label, pkg, activity, icon, true);

                // Apply custom category from parent zone settings
                String customCat = prefs.getAppCategory(pkg, app.getCategory());
                app.setCategory(customCat);

                if (lockGames && app.getCategory().equals(AppModel.CAT_GAMES)) {
                    continue;
                }
                if (lockBrowsers && (pkg.contains("chrome") || pkg.contains("browser"))) {
                    continue;
                }

                allAllowedApps.add(app);
            }
        }

        // Add built-in Sparkle Camera (renamed: remove Melody word)
        AppModel cameraApp = new AppModel(
                "Camera 📸",
                getPackageName(),
                MelodyCameraActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_camera),
                true
        );
        cameraApp.setCategory(prefs.getAppCategory(getPackageName() + ".camera", AppModel.CAT_CREATIVE));
        allAllowedApps.add(cameraApp);

        // Add built-in Music Player (renamed: remove Melody word)
        AppModel musicApp = new AppModel(
                "Music 🎶",
                getPackageName(),
                MelodyMusicActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_music),
                true
        );
        musicApp.setCategory(prefs.getAppCategory(getPackageName() + ".music", AppModel.CAT_MEDIA));
        allAllowedApps.add(musicApp);

        // Add built-in Cute Melody Calculator
        AppModel calcApp = new AppModel(
                "Calculator 🔢",
                getPackageName(),
                MelodyCalculatorActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_calculator),
                true
        );
        calcApp.setCategory(prefs.getAppCategory(getPackageName() + ".calculator", AppModel.CAT_LEARNING));
        allAllowedApps.add(calcApp);

        // Add built-in Cute Melody Clock & Timer
        AppModel clockApp = new AppModel(
                "Clock ⏰",
                getPackageName(),
                MelodyClockActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_clock),
                true
        );
        clockApp.setCategory(prefs.getAppCategory(getPackageName() + ".clock", AppModel.CAT_LEARNING));
        allAllowedApps.add(clockApp);

        // Add built-in Cute Melody Gallery
        AppModel galleryApp = new AppModel(
                "Gallery 🖼️",
                getPackageName(),
                MelodyGalleryActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_gallery),
                true
        );
        galleryApp.setCategory(prefs.getAppCategory(getPackageName() + ".gallery", AppModel.CAT_CREATIVE));
        allAllowedApps.add(galleryApp);

        // Add built-in Battery Management App ("Battery 🔋")
        AppModel batteryApp = new AppModel(
                "Battery 🔋",
                getPackageName(),
                MelodyBatteryActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_battery_app),
                true
        );
        batteryApp.setCategory(prefs.getAppCategory(getPackageName() + ".battery", AppModel.CAT_LEARNING));
        allAllowedApps.add(batteryApp);

        // Add built-in Safe Browser ("Safe Browser 🛡️")
        AppModel browserApp = new AppModel(
                "Safe Browser 🛡️",
                getPackageName(),
                MelodySafeBrowserActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_browser),
                true
        );
        browserApp.setCategory(prefs.getAppCategory(getPackageName() + ".browser", AppModel.CAT_LEARNING));
        allAllowedApps.add(browserApp);

        // Add built-in File Manager ("Files 📁")
        AppModel filesApp = new AppModel(
                "Files 📁",
                getPackageName(),
                MelodyFileManagerActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_files),
                true
        );
        filesApp.setCategory(prefs.getAppCategory(getPackageName() + ".files", AppModel.CAT_LEARNING));
        allAllowedApps.add(filesApp);

        // Add built-in Video Player ("Videos 🎬")
        AppModel videoApp = new AppModel(
                "Videos 🎬",
                getPackageName(),
                MelodyVideoActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_video),
                true
        );
        videoApp.setCategory(prefs.getAppCategory(getPackageName() + ".videos", AppModel.CAT_MEDIA));
        allAllowedApps.add(videoApp);

        // Add built-in Quick Share & Bluetooth ("Quick Share 📡")
        AppModel shareApp = new AppModel(
                "Quick Share 📡",
                getPackageName(),
                MelodyQuickShareActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_quick_share),
                true
        );
        shareApp.setCategory(prefs.getAppCategory(getPackageName() + ".share", AppModel.CAT_LEARNING));
        allAllowedApps.add(shareApp);

        // Add built-in System Updater ("System Update 🚀")
        AppModel updaterApp = new AppModel(
                "System Update 🚀",
                getPackageName(),
                MelodyUpdaterActivity.class.getName(),
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_melody_updater),
                true
        );
        updaterApp.setCategory(prefs.getAppCategory(getPackageName() + ".updater", AppModel.CAT_LEARNING));
        allAllowedApps.add(updaterApp);

        Collections.sort(allAllowedApps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        filterAppsByCategory();
    }

    private void startClockAndTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateClock();
                if (prefs.getTimeLimitMinutes() > 0) {
                    prefs.incrementElapsedSeconds(1);
                }
                if (prefs.isBreakTimerEnabled()) {
                    prefs.incrementContinuousSeconds(1);
                }
                checkPlaytimeState();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        if (tvClock != null) tvClock.setText(sdf.format(new Date()));
        SimpleDateFormat sdfDate = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        if (tvDate != null) tvDate.setText(sdfDate.format(new Date()));
    }

    private void checkPlaytimeState() {
        TextView tvLockIcon = findViewById(R.id.tv_lock_icon);
        TextView tvLockTitle = findViewById(R.id.tv_lock_title);
        TextView tvLockDesc = findViewById(R.id.tv_lock_desc);
        LinearLayout layoutEyeBreak = findViewById(R.id.layout_eye_break_overlay);
        Button btnFinishBreak = findViewById(R.id.btn_finish_break);

        if (btnFinishBreak != null) {
            btnFinishBreak.setOnClickListener(v -> {
                prefs.resetContinuousSeconds();
                layoutEyeBreak.setVisibility(View.GONE);
                Toast.makeText(this, "Welcome back! Keep exploring! 🌟", Toast.LENGTH_SHORT).show();
            });
        }

        if (prefs.isBedtimeNow()) {
            tvTimerBadge.setText("🌙 Bedtime");
            if (tvLockIcon != null) tvLockIcon.setText("🌙🐰💤");
            if (tvLockTitle != null) tvLockTitle.setText("Bedtime Curfew Lockout 🌙");
            if (tvLockDesc != null) tvLockDesc.setText("It's sleep time! Tablet is locked until morning. Sweet dreams! 🌸✨");
            layoutTimeUpOverlay.setVisibility(View.VISIBLE);
            return;
        }

        int limit = prefs.getTimeLimitMinutes();
        if (limit > 0 && prefs.isTimeUp()) {
            tvTimerBadge.setText("⏱️ Time's Up!");
            if (tvLockIcon != null) tvLockIcon.setText("🐰💤");
            if (tvLockTitle != null) tvLockTitle.setText("Playtime is Finished!");
            if (tvLockDesc != null) tvLockDesc.setText("Great job exploring today! Time to rest your eyes or play in the garden. 🌸✨");
            layoutTimeUpOverlay.setVisibility(View.VISIBLE);
            return;
        }

        layoutTimeUpOverlay.setVisibility(View.GONE);

        if (prefs.isBreakTimeNow()) {
            if (!isEyeBreakShowing) {
                isEyeBreakShowing = true;
                MelodyEyeBreakDialog.show(this, () -> {
                    prefs.resetContinuousSeconds();
                    isEyeBreakShowing = false;
                });
            }
        }

        if (limit <= 0) {
            tvTimerBadge.setText("⏱️ Unlimited");
        } else {
            int remainingSecs = prefs.getRemainingSeconds();
            int mins = remainingSecs / 60;
            int secs = remainingSecs % 60;
            tvTimerBadge.setText(String.format(Locale.getDefault(), "⏱️ %02d:%02d left", mins, secs));
        }
    }

    private void showAvatarPicker() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_avatar);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.65),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        GridLayout grid = dialog.findViewById(R.id.grid_avatars);
        Button btnClose = dialog.findViewById(R.id.btn_close_avatar);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        String[] avatars = {"🐰", "🎀", "🦄", "🍓", "🌸", "💖", "⭐", "👑", "🐱", "🌈"};
        for (String av : avatars) {
            TextView tv = new TextView(this);
            tv.setText(av);
            tv.setTextSize(32);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(20, 20, 20, 20);
            tv.setBackgroundResource(R.drawable.bg_melody_pill);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.setMargins(10, 10, 10, 10);
            tv.setLayoutParams(params);

            tv.setOnClickListener(v -> {
                prefs.setAvatar(av);
                tvAvatarBadge.setText(av);
                Toast.makeText(this, "Avatar chosen! " + av, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            grid.addView(tv);
        }

        dialog.show();
    }

    private void showPinDialog(Runnable onPinSuccess) {
        MelodyPinPadDialog.show(this, "Parent Verification 🔒", "Enter 4-digit PIN to continue", () -> {
            if (onPinSuccess != null) {
                onPinSuccess.run();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (layoutRecentsOverlay != null && layoutRecentsOverlay.getVisibility() == View.VISIBLE) {
            closeRecentsOverlay();
            return;
        }
        if (layoutControlCenterContainer != null && layoutControlCenterContainer.getVisibility() == View.VISIBLE) {
            toggleControlCenter(false);
            return;
        }
        Toast.makeText(this, "To exit, ask a parent to open Parent Zone 🔒", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Exception ignored) {}
        }
        if (wifiReceiver != null) {
            try {
                unregisterReceiver(wifiReceiver);
            } catch (Exception ignored) {}
        }
    }

    private void setupWifiReceiver() {
        updateWifiIndicator();
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateWifiIndicator();
                updateQuickTilesUi();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(wifiReceiver, filter);
    }

    private void updateWifiIndicator() {
        if (ivWifiStatus == null) return;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return;
            boolean enabled = wm.isWifiEnabled();
            if (!enabled) {
                ivWifiStatus.updateWifiState(false, false, 0, 0, "");
                return;
            }
            WifiInfo info = wm.getConnectionInfo();
            boolean connected = false;
            int level = 0;
            int speed = 0;
            String ssid = "";
            if (info != null && info.getNetworkId() != -1) {
                connected = true;
                level = WifiManager.calculateSignalLevel(info.getRssi(), 5);
                speed = info.getLinkSpeed();
                ssid = info.getSSID();
            }
            ivWifiStatus.updateWifiState(enabled, connected, level, speed, ssid);
        } catch (Exception ignored) {}
    }

    private void prewarmWebViewInBackground() {
        new Thread(() -> {
            try {
                // Pre-warm Android System WebView to eliminate cold-start lag for YouTube and browsers
                new android.webkit.WebView(getApplicationContext()).destroy();
            } catch (Exception ignored) {}
        }).start();
    }
}
