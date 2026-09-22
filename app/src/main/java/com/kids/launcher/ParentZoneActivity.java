package com.kids.launcher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ParentZoneActivity extends AppCompatActivity {

    private PreferencesManager prefs;
    private ParentAppAdapter adapter;
    private final List<AppModel> allApps = new ArrayList<>();
    private final List<AppModel> filteredApps = new ArrayList<>();

    // Tab Views
    private LinearLayout layoutTabApps;
    private LinearLayout layoutTabTime;
    private LinearLayout layoutTabStats;
    private LinearLayout layoutTabAndroid;
    private View layoutTabSecurity;

    // Tab Buttons
    private Button tabBtnApps;
    private Button tabBtnTime;
    private Button tabBtnStats;
    private Button tabBtnAndroid;
    private Button tabBtnSecurity;

    // Controls
    private TextView tvAppCountBadge;
    private RadioGroup rgTimeLimits;
    private EditText etNewPin;
    private EditText etSearchApps;
    private SwitchCompat switchLockGames;
    private SwitchCompat switchLockBrowsers;
    private SwitchCompat switchBedtime;
    private SwitchCompat switchEyeBreak;
    private SwitchCompat switchAutoBatterySaver;
    private SwitchCompat switchFullBatteryAlert;

    // Health & Wellbeing Controls
    private SwitchCompat switchBlueLight;
    private SwitchCompat switchPostureReminder;
    private SwitchCompat switchVolumeLimiter;

    // Pending App Approvals
    private LinearLayout layoutPendingApprovals;
    private LinearLayout containerPendingApps;

    // Stats
    private TextView tvStatsTimeSpent;
    private TextView tvStatsAppsCount;
    private TextView tvStatsStatus;
    private TextView tvStatsVsYesterday;
    private LinearLayout containerTopApps;
    private Button btnGrantUsageStats;
    private ProgressBar pbDailyUsage;

    // Security & SOS & Safe Web & Location
    private EditText etSosName;
    private EditText etSosNumber;
    private Button btnSaveSos;
    private EditText etSafeDomain;
    private Button btnAddDomain;
    private LinearLayout layoutWhitelistDomains;
    private TextView tvLocationInfo;
    private Button btnOpenMaps;
    private Button btnRefreshLocation;
    private LocationHelper.LocationInfo lastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_zone);

        prefs = new PreferencesManager(this);

        initViews();
        setupTabs();
        setupAdvancedControls();
        setupHealthControls();
        setupAndroidSettingsTiles();
        setupTimeLimits();
        setupAppList();
        setupPendingApprovals();
        setupSecuritySection();

        loadInstalledApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInstalledApps();
        updatePendingApprovalsUi();
    }

    private void initViews() {
        layoutTabApps = findViewById(R.id.layout_tab_apps);
        layoutTabTime = findViewById(R.id.layout_tab_time);
        layoutTabStats = findViewById(R.id.layout_tab_stats);
        layoutTabAndroid = findViewById(R.id.layout_tab_android);
        layoutTabSecurity = findViewById(R.id.layout_tab_security);

        tabBtnApps = findViewById(R.id.tab_btn_apps);
        tabBtnTime = findViewById(R.id.tab_btn_time);
        tabBtnStats = findViewById(R.id.tab_btn_stats);
        tabBtnAndroid = findViewById(R.id.tab_btn_android);
        tabBtnSecurity = findViewById(R.id.tab_btn_security);

        tvAppCountBadge = findViewById(R.id.tv_app_count_badge);
        rgTimeLimits = findViewById(R.id.rg_time_limits);
        etNewPin = findViewById(R.id.et_new_pin);
        etSearchApps = findViewById(R.id.et_search_apps);

        switchLockGames = findViewById(R.id.switch_lock_games);
        switchLockBrowsers = findViewById(R.id.switch_lock_browsers);
        switchBedtime = findViewById(R.id.switch_bedtime);
        switchEyeBreak = findViewById(R.id.switch_eye_break);
        switchAutoBatterySaver = findViewById(R.id.switch_auto_battery_saver);
        switchFullBatteryAlert = findViewById(R.id.switch_full_battery_alert);

        switchBlueLight = findViewById(R.id.switch_blue_light);
        switchPostureReminder = findViewById(R.id.switch_posture_reminder);
        switchVolumeLimiter = findViewById(R.id.switch_volume_limiter);

        layoutPendingApprovals = findViewById(R.id.layout_pending_approvals);
        containerPendingApps = findViewById(R.id.container_pending_apps);

        tvStatsTimeSpent = findViewById(R.id.tv_stats_time_spent);
        tvStatsAppsCount = findViewById(R.id.tv_stats_apps_count);
        tvStatsStatus = findViewById(R.id.tv_stats_status);
        tvStatsVsYesterday = findViewById(R.id.tv_stats_vs_yesterday);
        containerTopApps = findViewById(R.id.container_top_apps);
        btnGrantUsageStats = findViewById(R.id.btn_grant_usage_stats);
        pbDailyUsage = findViewById(R.id.pb_daily_usage);

        etSosName = findViewById(R.id.et_sos_name);
        etSosNumber = findViewById(R.id.et_sos_number);
        btnSaveSos = findViewById(R.id.btn_save_sos);
        etSafeDomain = findViewById(R.id.et_safe_domain);
        btnAddDomain = findViewById(R.id.btn_add_domain);
        layoutWhitelistDomains = findViewById(R.id.layout_whitelist_domains);
        tvLocationInfo = findViewById(R.id.tv_location_info);
        btnOpenMaps = findViewById(R.id.btn_open_maps);
        btnRefreshLocation = findViewById(R.id.btn_refresh_location);

        Button btnBackToKids = findViewById(R.id.btn_back_to_kids);
        Button btnExitToAndroid = findViewById(R.id.btn_exit_to_android);
        Button btnSavePin = findViewById(R.id.btn_save_pin);
        Button btnResetTimer = findViewById(R.id.btn_reset_timer);

        btnBackToKids.setOnClickListener(v -> finish());
        btnExitToAndroid.setOnClickListener(v -> showExitConfirmationDialog());
        btnSavePin.setOnClickListener(v -> saveNewPin());

        btnResetTimer.setOnClickListener(v -> {
            prefs.resetTimer();
            refreshStats();
            Toast.makeText(this, "Daily screen timer reset to 0!", Toast.LENGTH_SHORT).show();
        });

        if (btnGrantUsageStats != null) {
            btnGrantUsageStats.setOnClickListener(v -> UsageStatsHelper.openUsageAccessSettings(this));
        }
    }

    private void setupTabs() {
        tabBtnApps.setOnClickListener(v -> switchTab(0));
        tabBtnTime.setOnClickListener(v -> switchTab(1));
        tabBtnStats.setOnClickListener(v -> switchTab(2));
        tabBtnAndroid.setOnClickListener(v -> switchTab(3));
        tabBtnSecurity.setOnClickListener(v -> switchTab(4));
    }

    private void switchTab(int tabIndex) {
        layoutTabApps.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutTabTime.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        layoutTabStats.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
        layoutTabAndroid.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);
        layoutTabSecurity.setVisibility(tabIndex == 4 ? View.VISIBLE : View.GONE);

        Button[] buttons = {tabBtnApps, tabBtnTime, tabBtnStats, tabBtnAndroid, tabBtnSecurity};
        for (int i = 0; i < buttons.length; i++) {
            if (i == tabIndex) {
                buttons[i].setBackgroundColor(Color.parseColor("#FF4D8D"));
                buttons[i].setTextColor(Color.WHITE);
            } else {
                buttons[i].setBackgroundColor(Color.parseColor("#26174D"));
                buttons[i].setTextColor(Color.parseColor("#D1D5DB"));
            }
        }

        if (tabIndex == 0) {
            updatePendingApprovalsUi();
        } else if (tabIndex == 2) {
            refreshStats();
        } else if (tabIndex == 4) {
            refreshLocationUi();
            updateWhitelistDomainsUi();
        }
    }

    private void setupAndroidSettingsTiles() {
        findViewById(R.id.tile_wifi).setOnClickListener(v -> launchSystemSetting(Settings.ACTION_WIFI_SETTINGS));
        findViewById(R.id.tile_sound).setOnClickListener(v -> launchSystemSetting(Settings.ACTION_SOUND_SETTINGS));
        findViewById(R.id.tile_display).setOnClickListener(v -> launchSystemSetting(Settings.ACTION_DISPLAY_SETTINGS));
        findViewById(R.id.tile_battery).setOnClickListener(v -> launchSystemSetting(Intent.ACTION_POWER_USAGE_SUMMARY));
        findViewById(R.id.tile_master_settings).setOnClickListener(v -> launchSystemSetting(Settings.ACTION_SETTINGS));
        findViewById(R.id.tile_home_apps).setOnClickListener(v -> launchSystemSetting(Settings.ACTION_HOME_SETTINGS));
    }

    private void launchSystemSetting(String action) {
        try {
            Intent intent = new Intent(action);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                startActivity(fallback);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open system setting", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupAdvancedControls() {
        switchLockGames.setChecked(prefs.isLockGames());
        switchLockGames.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setLockGames(isChecked);
            Toast.makeText(this, isChecked ? "All games locked 🚫" : "Games unlocked", Toast.LENGTH_SHORT).show();
        });

        switchLockBrowsers.setChecked(prefs.isLockBrowsers());
        switchLockBrowsers.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setLockBrowsers(isChecked);
            Toast.makeText(this, isChecked ? "Web browsing locked 🚫" : "Web browsing allowed", Toast.LENGTH_SHORT).show();
        });

        switchBedtime.setChecked(prefs.isBedtimeEnabled());
        switchBedtime.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setBedtimeEnabled(isChecked);
            Toast.makeText(this, isChecked ? "Bedtime curfew active (8 PM - 7 AM)" : "Bedtime curfew off", Toast.LENGTH_SHORT).show();
        });

        switchEyeBreak.setChecked(prefs.isBreakTimerEnabled());
        switchEyeBreak.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setBreakTimerEnabled(isChecked);
            Toast.makeText(this, isChecked ? "20-min Eye Protection reminder active 👀" : "Break timer off", Toast.LENGTH_SHORT).show();
        });

        if (switchAutoBatterySaver != null) {
            switchAutoBatterySaver.setChecked(prefs.isAutoBatterySaverEnabled());
            switchAutoBatterySaver.setOnCheckedChangeListener((b, isChecked) -> {
                prefs.setAutoBatterySaverEnabled(isChecked);
                Toast.makeText(this, isChecked ? "Auto Battery Saver active (< 20%) 🪫" : "Battery Saver off", Toast.LENGTH_SHORT).show();
            });
        }

        if (switchFullBatteryAlert != null) {
            switchFullBatteryAlert.setChecked(prefs.isFullBatteryAlertEnabled());
            switchFullBatteryAlert.setOnCheckedChangeListener((b, isChecked) -> {
                prefs.setFullBatteryAlertEnabled(isChecked);
                Toast.makeText(this, isChecked ? "Full Battery Chime active (100%) 🔔" : "Full Battery Chime off", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupHealthControls() {
        if (switchBlueLight != null) {
            switchBlueLight.setChecked(prefs.isBlueLightFilterEnabled());
            switchBlueLight.setOnCheckedChangeListener((b, isChecked) -> {
                prefs.setBlueLightFilterEnabled(isChecked);
                if (MelodyGlobalService.getInstance() != null) {
                    MelodyGlobalService.getInstance().updateBlueLightFilter();
                }
                Toast.makeText(this, isChecked ? "Blue Light Shield Enabled 🌙" : "Blue Light Shield Disabled", Toast.LENGTH_SHORT).show();
            });
        }

        if (switchPostureReminder != null) {
            switchPostureReminder.setChecked(prefs.isPostureReminderEnabled());
            switchPostureReminder.setOnCheckedChangeListener((b, isChecked) -> {
                prefs.setPostureReminderEnabled(isChecked);
                Toast.makeText(this, isChecked ? "Posture Reminders Active 🧸" : "Posture Reminders Off", Toast.LENGTH_SHORT).show();
            });
        }

        if (switchVolumeLimiter != null) {
            switchVolumeLimiter.setChecked(prefs.isVolumeLimiterEnabled());
            switchVolumeLimiter.setOnCheckedChangeListener((b, isChecked) -> {
                prefs.setVolumeLimiterEnabled(isChecked);
                Toast.makeText(this, isChecked ? "Ear Protection Volume Cap (70%) Enabled 🎧" : "Volume Cap Disabled", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupPendingApprovals() {
        updatePendingApprovalsUi();
    }

    private void updatePendingApprovalsUi() {
        if (layoutPendingApprovals == null || containerPendingApps == null) return;
        Set<String> pending = prefs.getPendingApprovalPackages();
        if (pending.isEmpty()) {
            layoutPendingApprovals.setVisibility(View.GONE);
            return;
        }

        layoutPendingApprovals.setVisibility(View.VISIBLE);
        containerPendingApps.removeAllViews();

        PackageManager pm = getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (String pkg : pending) {
            View row = inflater.inflate(R.layout.item_parent_app, containerPendingApps, false);
            ImageView iv = row.findViewById(R.id.iv_parent_app_icon);
            TextView tvTitle = row.findViewById(R.id.tv_parent_app_name);
            TextView tvSub = row.findViewById(R.id.tv_parent_package_name);
            View btnLock = row.findViewById(R.id.btn_lock_app);
            View catBadge = row.findViewById(R.id.tv_parent_app_category_badge);
            SwitchCompat switchAllow = row.findViewById(R.id.switch_allow_app);

            if (btnLock != null) btnLock.setVisibility(View.GONE);
            if (catBadge != null) catBadge.setVisibility(View.GONE);
            if (switchAllow != null) switchAllow.setVisibility(View.GONE);

            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                tvTitle.setText(pm.getApplicationLabel(ai));
                iv.setImageDrawable(pm.getApplicationIcon(ai));
            } catch (Exception e) {
                tvTitle.setText(pkg);
            }
            tvSub.setText("Requires Parent Approval");

            // Add Approve and Block action buttons
            LinearLayout actionsLayout = new LinearLayout(this);
            actionsLayout.setOrientation(LinearLayout.HORIZONTAL);

            Button btnApprove = new Button(this);
            btnApprove.setText("✅ Approve");
            btnApprove.setTextColor(Color.WHITE);
            btnApprove.setTextSize(11);
            btnApprove.setBackgroundColor(Color.parseColor("#10B981"));
            btnApprove.setOnClickListener(v -> {
                prefs.approvePackage(pkg);
                Toast.makeText(this, "App approved for kid! ✨", Toast.LENGTH_SHORT).show();
                loadInstalledApps();
                updatePendingApprovalsUi();
            });

            Button btnBlock = new Button(this);
            btnBlock.setText("🚫 Block");
            btnBlock.setTextColor(Color.WHITE);
            btnBlock.setTextSize(11);
            btnBlock.setBackgroundColor(Color.parseColor("#EF4444"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginStart(8);
            btnBlock.setLayoutParams(lp);
            btnBlock.setOnClickListener(v -> {
                prefs.blockPackage(pkg);
                Toast.makeText(this, "App blocked 🚫", Toast.LENGTH_SHORT).show();
                loadInstalledApps();
                updatePendingApprovalsUi();
            });

            actionsLayout.addView(btnApprove);
            actionsLayout.addView(btnBlock);
            ((LinearLayout) row).addView(actionsLayout);

            containerPendingApps.addView(row);
        }
    }

    private void setupSecuritySection() {
        if (etSosName != null) etSosName.setText(prefs.getSosContactName());
        if (etSosNumber != null) etSosNumber.setText(prefs.getSosPhoneNumber());

        if (btnSaveSos != null) {
            btnSaveSos.setOnClickListener(v -> {
                String name = etSosName.getText().toString().trim();
                String phone = etSosNumber.getText().toString().trim();
                if (name.isEmpty()) name = "Mama";
                prefs.setSosContactName(name);
                prefs.setSosPhoneNumber(phone);
                Toast.makeText(this, "SOS Emergency Contact Saved! 📞", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnAddDomain != null) {
            btnAddDomain.setOnClickListener(v -> {
                String domain = etSafeDomain.getText().toString().trim();
                if (!domain.isEmpty()) {
                    prefs.addSafeWebDomain(domain);
                    etSafeDomain.setText("");
                    updateWhitelistDomainsUi();
                    Toast.makeText(this, "Domain added to safe whitelist! 🛡️", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnRefreshLocation != null) {
            btnRefreshLocation.setOnClickListener(v -> refreshLocationUi());
        }

        if (btnOpenMaps != null) {
            btnOpenMaps.setOnClickListener(v -> {
                if (lastLocation != null) {
                    LocationHelper.openInMaps(this, lastLocation.latitude, lastLocation.longitude);
                } else {
                    Toast.makeText(this, "No location data available yet", Toast.LENGTH_SHORT).show();
                }
            });
        }

        updateWhitelistDomainsUi();
        refreshLocationUi();
    }

    private void updateWhitelistDomainsUi() {
        if (layoutWhitelistDomains == null) return;
        layoutWhitelistDomains.removeAllViews();
        Set<String> whitelist = prefs.getSafeWebWhitelist();

        for (String domain : whitelist) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(8, 4, 8, 4);

            TextView tv = new TextView(this);
            tv.setText("🌐 " + domain);
            tv.setTextColor(Color.parseColor("#E9D5FF"));
            tv.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(lp);

            TextView btnDel = new TextView(this);
            btnDel.setText("✕ Remove");
            btnDel.setTextColor(Color.parseColor("#F43F5E"));
            btnDel.setTextSize(11);
            btnDel.setPadding(12, 6, 12, 6);
            btnDel.setOnClickListener(v -> {
                prefs.removeSafeWebDomain(domain);
                updateWhitelistDomainsUi();
                Toast.makeText(this, "Domain removed", Toast.LENGTH_SHORT).show();
            });

            row.addView(tv);
            row.addView(btnDel);
            layoutWhitelistDomains.addView(row);
        }
    }

    private void refreshLocationUi() {
        if (tvLocationInfo == null) return;
        lastLocation = LocationHelper.getLastKnownLocation(this);
        if (lastLocation != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
            String timeStr = sdf.format(new Date(lastLocation.timestamp));
            tvLocationInfo.setText(String.format(Locale.getDefault(),
                    "📍 Coordinates: Lat %.5f, Lon %.5f (Accuracy: ±%.1fm, Updated: %s)",
                    lastLocation.latitude, lastLocation.longitude, lastLocation.accuracy, timeStr));
            if (btnOpenMaps != null) btnOpenMaps.setEnabled(true);
        } else {
            tvLocationInfo.setText("📍 Location: GPS signal standby. Ensure location is enabled in Android Settings.");
            if (btnOpenMaps != null) btnOpenMaps.setEnabled(false);
        }
    }

    private void refreshStats() {
        int elapsedSecs = prefs.getElapsedSeconds();
        int mins = elapsedSecs / 60;
        int limitMins = prefs.getTimeLimitMinutes();

        tvStatsTimeSpent.setText("Time Active Today: " + mins + " minute" + (mins == 1 ? "" : "s"));

        int count = 0;
        for (AppModel app : allApps) {
            if (app.isAllowed()) count++;
        }
        tvStatsAppsCount.setText("Total Allowed Apps: " + count + " apps");

        if (limitMins <= 0) {
            pbDailyUsage.setMax(120);
            pbDailyUsage.setProgress(Math.min(mins, 120));
            tvStatsStatus.setText("Current Status: Active (Unlimited Time) 🟢");
            tvStatsStatus.setTextColor(Color.parseColor("#34D399"));
        } else {
            pbDailyUsage.setMax(limitMins);
            pbDailyUsage.setProgress(Math.min(mins, limitMins));
            if (mins >= limitMins) {
                tvStatsStatus.setText("Current Status: Quota Reached (Locked) 🔴");
                tvStatsStatus.setTextColor(Color.parseColor("#EF4444"));
            } else {
                tvStatsStatus.setText("Current Status: " + (limitMins - mins) + " mins remaining 🟢");
                tvStatsStatus.setTextColor(Color.parseColor("#34D399"));
            }
        }

        // Top 3 Apps from UsageStats
        if (containerTopApps != null) {
            containerTopApps.removeAllViews();
            boolean hasPermission = UsageStatsHelper.hasUsageStatsPermission(this);
            if (btnGrantUsageStats != null) {
                btnGrantUsageStats.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
            }

            List<UsageStatsHelper.AppUsageInfo> topList = UsageStatsHelper.getDailyUsageStats(this);
            if (topList.isEmpty()) {
                TextView tvEmpty = new TextView(this);
                tvEmpty.setText("No app usage tracked yet today ✨");
                tvEmpty.setTextColor(Color.parseColor("#9CA3AF"));
                tvEmpty.setTextSize(12);
                containerTopApps.addView(tvEmpty);
            } else {
                int displayCount = Math.min(3, topList.size());
                for (int i = 0; i < displayCount; i++) {
                    UsageStatsHelper.AppUsageInfo info = topList.get(i);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setPadding(8, 8, 8, 8);
                    row.setBackgroundColor(Color.parseColor("#26174D"));

                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    rowLp.setMargins(0, 4, 0, 4);
                    row.setLayoutParams(rowLp);

                    ImageView iv = new ImageView(this);
                    iv.setLayoutParams(new LinearLayout.LayoutParams(36, 36));
                    iv.setImageDrawable(info.icon);

                    TextView tvName = new TextView(this);
                    tvName.setText("  " + (i + 1) + ". " + info.label);
                    tvName.setTextColor(Color.WHITE);
                    tvName.setTextSize(13);
                    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    tvName.setLayoutParams(nameLp);

                    TextView tvTime = new TextView(this);
                    tvTime.setText(info.getTimeMinutes() + " mins");
                    tvTime.setTextColor(Color.parseColor("#F472B6"));
                    tvTime.setTextSize(12);

                    row.addView(iv);
                    row.addView(tvName);
                    row.addView(tvTime);
                    containerTopApps.addView(row);
                }
            }
        }
    }

    private void setupAppList() {
        Button btnSelectAll = findViewById(R.id.btn_select_all);
        Button btnDeselectAll = findViewById(R.id.btn_deselect_all);
        RecyclerView rvParentApps = findViewById(R.id.rv_parent_apps);

        btnSelectAll.setOnClickListener(v -> {
            for (AppModel app : allApps) {
                app.setAllowed(true);
                prefs.addAllowedPackage(app.getPackageName());
            }
            adapter.notifyDataSetChanged();
            updateAppCount();
            Toast.makeText(this, "All apps allowed", Toast.LENGTH_SHORT).show();
        });

        btnDeselectAll.setOnClickListener(v -> {
            for (AppModel app : allApps) {
                app.setAllowed(false);
                prefs.removeAllowedPackage(app.getPackageName());
            }
            adapter.notifyDataSetChanged();
            updateAppCount();
            Toast.makeText(this, "All apps restricted", Toast.LENGTH_SHORT).show();
        });

        rvParentApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ParentAppAdapter(this, filteredApps, (app, isAllowed) -> {
            if (isAllowed) {
                prefs.addAllowedPackage(app.getPackageName());
            } else {
                prefs.removeAllowedPackage(app.getPackageName());
            }
            updateAppCount();
        });
        rvParentApps.setAdapter(adapter);

        etSearchApps.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterSearch(String query) {
        filteredApps.clear();
        if (TextUtils.isEmpty(query)) {
            filteredApps.addAll(allApps);
        } else {
            String lower = query.toLowerCase(Locale.ROOT);
            for (AppModel app : allApps) {
                if (app.getLabel().toLowerCase(Locale.ROOT).contains(lower) ||
                        app.getPackageName().toLowerCase(Locale.ROOT).contains(lower)) {
                    filteredApps.add(app);
                }
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void setupTimeLimits() {
        int currentLimit = prefs.getTimeLimitMinutes();
        if (currentLimit <= 0) {
            rgTimeLimits.check(R.id.rb_unlimited);
        } else if (currentLimit == 15) {
            rgTimeLimits.check(R.id.rb_15m);
        } else if (currentLimit == 30) {
            rgTimeLimits.check(R.id.rb_30m);
        } else if (currentLimit == 60) {
            rgTimeLimits.check(R.id.rb_60m);
        }

        rgTimeLimits.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_unlimited) {
                prefs.setTimeLimitMinutes(0);
                Toast.makeText(this, "Time limit set: Unlimited", Toast.LENGTH_SHORT).show();
            } else if (checkedId == R.id.rb_15m) {
                prefs.setTimeLimitMinutes(15);
                Toast.makeText(this, "Time limit set: 15 Minutes", Toast.LENGTH_SHORT).show();
            } else if (checkedId == R.id.rb_30m) {
                prefs.setTimeLimitMinutes(30);
                Toast.makeText(this, "Time limit set: 30 Minutes", Toast.LENGTH_SHORT).show();
            } else if (checkedId == R.id.rb_60m) {
                prefs.setTimeLimitMinutes(60);
                Toast.makeText(this, "Time limit set: 60 Minutes", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveNewPin() {
        String newPin = etNewPin.getText().toString().trim();
        if (newPin.length() != 4 || !TextUtils.isDigitsOnly(newPin)) {
            Toast.makeText(this, "Please enter a valid 4-digit PIN", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.setPin(newPin);
        etNewPin.setText("");
        Toast.makeText(this, "Security PIN updated successfully!", Toast.LENGTH_SHORT).show();
    }

    private void loadInstalledApps() {
        allApps.clear();
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);
        String myPackage = getPackageName();

        for (ResolveInfo resolveInfo : pkgAppsList) {
            String pkg = resolveInfo.activityInfo.packageName;
            if (pkg.equals(myPackage)) continue;

            String label = resolveInfo.loadLabel(pm).toString();
            String activity = resolveInfo.activityInfo.name;
            boolean isAllowed = prefs.isPackageAllowed(pkg);

            android.graphics.drawable.Drawable icon = resolveInfo.loadIcon(pm);
            boolean isYtKids = pkg.contains("youtube.kids") || label.toLowerCase().contains("kids");
            if (!isYtKids && (pkg.contains("morphe") || pkg.contains("youtube") || label.toLowerCase().contains("youtube"))) {
                icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_youtube_official);
            }

            AppModel app = new AppModel(label, pkg, activity, icon, isAllowed);
            String savedCategory = prefs.getAppCategory(pkg, app.getCategory());
            app.setCategory(savedCategory);
            allApps.add(app);
        }

        Collections.sort(allApps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        filterSearch(etSearchApps.getText().toString());
        updateAppCount();
    }

    private void updateAppCount() {
        int count = 0;
        for (AppModel app : allApps) {
            if (app.isAllowed()) count++;
        }
        tvAppCountBadge.setText(count + " allowed for kid");
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Kids Space")
                .setMessage("Do you want to open Android System Settings or Home Launcher Switcher?")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e) {
                        try {
                            Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
                            startActivity(settingsIntent);
                        } catch (Exception ex) {
                            Toast.makeText(this, "Cannot open settings", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton("Launcher Switcher", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(Intent.createChooser(intent, "Choose Launcher"));
                    } catch (Exception e) {
                        Toast.makeText(this, "Error switching launcher", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
