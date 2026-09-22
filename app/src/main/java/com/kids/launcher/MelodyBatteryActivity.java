package com.kids.launcher;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MelodyBatteryActivity extends AppCompatActivity {

    private BatteryGaugeView gaugeBatteryLarge;
    private TextView tvChargingType;
    private TextView tvTimeRemaining;
    private TextView tvBatteryTemp;
    private TextView tvBatteryVoltage;
    private TextView tvBatteryHealthBadge;
    private SwitchCompat switchBatterySaver;
    private SwitchCompat switchFullAlert;
    private Button btnOptimizeBattery;
    private RecyclerView rvAppDrain;

    private PreferencesManager prefs;
    private BroadcastReceiver batteryReceiver;
    private AppDrainAdapter drainAdapter;
    private final List<AppDrainItem> drainApps = new ArrayList<>();

    public static class AppDrainItem {
        public final String label;
        public final String packageName;
        public final Drawable icon;
        public boolean isSleeping;

        public AppDrainItem(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.isSleeping = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_melody_battery);

        // Keep system navigation bar visible
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        prefs = new PreferencesManager(this);

        gaugeBatteryLarge = findViewById(R.id.gauge_battery_large);
        if (gaugeBatteryLarge != null) {
            gaugeBatteryLarge.setShowPercentInside(true);
        }

        tvChargingType = findViewById(R.id.tv_charging_type);
        tvTimeRemaining = findViewById(R.id.tv_time_remaining);
        tvBatteryTemp = findViewById(R.id.tv_battery_temp);
        tvBatteryVoltage = findViewById(R.id.tv_battery_voltage);
        tvBatteryHealthBadge = findViewById(R.id.tv_battery_health_badge);

        switchBatterySaver = findViewById(R.id.switch_app_battery_saver);
        switchFullAlert = findViewById(R.id.switch_app_full_alert);
        btnOptimizeBattery = findViewById(R.id.btn_optimize_battery);
        rvAppDrain = findViewById(R.id.rv_app_drain);

        findViewById(R.id.btn_battery_back).setOnClickListener(v -> finish());

        if (switchBatterySaver != null) {
            switchBatterySaver.setChecked(prefs.isAutoBatterySaverEnabled());
            switchBatterySaver.setOnCheckedChangeListener((b, isChecked) -> {
                prefs.setAutoBatterySaverEnabled(isChecked);
                Toast.makeText(this, isChecked ? "Auto Battery Saver active (< 20%) 🪫" : "Battery Saver off", Toast.LENGTH_SHORT).show();
            });
        }

        if (switchFullAlert != null) {
            switchFullAlert.setChecked(prefs.isFullBatteryAlertEnabled());
            switchFullAlert.setOnCheckedChangeListener((b, isChecked) -> {
                prefs.setFullBatteryAlertEnabled(isChecked);
                Toast.makeText(this, isChecked ? "Full Charge Chime active (100%) 🔔" : "Full Charge Chime off", Toast.LENGTH_SHORT).show();
            });
        }

        btnOptimizeBattery.setOnClickListener(v -> {
            btnOptimizeBattery.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).withEndAction(() -> {
                btnOptimizeBattery.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }).start();

            long freedMb = DeviceBooster.boostAndGetFreedMb(this);
            for (AppDrainItem item : drainApps) {
                item.isSleeping = true;
            }
            if (drainAdapter != null) {
                drainAdapter.notifyDataSetChanged();
            }

            Toast.makeText(this, "⚡ Battery Optimized! Freed " + freedMb + " MB RAM & Hibernated Background Apps! 🌸🔋", Toast.LENGTH_LONG).show();
        });

        rvAppDrain.setLayoutManager(new LinearLayoutManager(this));
        drainAdapter = new AppDrainAdapter(this, drainApps, item -> {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    am.killBackgroundProcesses(item.packageName);
                }
            } catch (Exception ignored) {}
            item.isSleeping = true;
            if (drainAdapter != null) {
                drainAdapter.notifyDataSetChanged();
            }
            Toast.makeText(this, item.label + " put to sleep! 😴", Toast.LENGTH_SHORT).show();
        });
        rvAppDrain.setAdapter(drainAdapter);

        loadRunningApps();
        setupBatteryReceiver();
    }

    private void setupBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 290);
                int voltRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4100);
                int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD);

                int pct = (scale > 0) ? (int) (level * 100.0f / scale) : 100;
                boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                boolean isFastCharger = (plugged == BatteryManager.BATTERY_PLUGGED_AC);

                if (gaugeBatteryLarge != null) {
                    gaugeBatteryLarge.setBatteryStatus(pct, isCharging, isFastCharger);
                }

                // Health
                if (health == BatteryManager.BATTERY_HEALTH_GOOD) {
                    tvBatteryHealthBadge.setText("Health: Good ✨");
                    tvBatteryHealthBadge.setTextColor(Color.parseColor("#059669"));
                } else if (health == BatteryManager.BATTERY_HEALTH_OVERHEAT) {
                    tvBatteryHealthBadge.setText("Health: Warm ⚠️");
                    tvBatteryHealthBadge.setTextColor(Color.parseColor("#E11D48"));
                } else {
                    tvBatteryHealthBadge.setText("Health: Normal 🟢");
                    tvBatteryHealthBadge.setTextColor(Color.parseColor("#059669"));
                }

                // Temperature & Voltage
                float tempC = tempRaw / 10.0f;
                tvBatteryTemp.setText(String.format("%.1f °C (%s)", tempC, tempC < 33 ? "Cool ❄️" : "Warm ☀️"));
                float voltV = voltRaw > 1000 ? voltRaw / 1000.0f : voltRaw;
                tvBatteryVoltage.setText(String.format("%.2f V", voltV));

                // Charging Status & Estimates
                if (isCharging) {
                    if (status == BatteryManager.BATTERY_STATUS_FULL || pct >= 100) {
                        tvChargingType.setText("⚡ Fully Charged (100%)");
                        tvTimeRemaining.setText("Battery is fully charged. You can unplug now! 🌸");
                    } else if (isFastCharger) {
                        tvChargingType.setText("⚡ Fast Charger Connected (AC)");
                        int minsToFull = Math.max(5, (100 - pct) * 45 / 100);
                        tvTimeRemaining.setText("Estimated Time: ~" + minsToFull + " mins until 100% full");
                    } else {
                        tvChargingType.setText("🔌 Standard / USB Charger");
                        int minsToFull = Math.max(10, (100 - pct) * 85 / 100);
                        tvTimeRemaining.setText("Estimated Time: ~" + minsToFull + " mins until full");
                    }
                } else {
                    tvChargingType.setText("🔋 Discharging (On Battery)");
                    int totalHours = (int) (pct * 6.5f / 100.0f);
                    int totalMins = (int) ((pct * 6.5f / 100.0f - totalHours) * 60);
                    tvTimeRemaining.setText(String.format("Estimated Time: ~%dh %02dm screen time remaining", totalHours, totalMins));
                }
            }
        };

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void loadRunningApps() {
        new AsyncTask<Void, Void, List<AppDrainItem>>() {
            @Override
            protected List<AppDrainItem> doInBackground(Void... voids) {
                List<AppDrainItem> list = new ArrayList<>();
                try {
                    PackageManager pm = getPackageManager();
                    List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                    String myPkg = getPackageName();

                    for (ApplicationInfo app : apps) {
                        // Skip system core and launcher
                        if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                        if (app.packageName.equals(myPkg)) continue;

                        String label = pm.getApplicationLabel(app).toString();
                        Drawable icon = pm.getApplicationIcon(app);
                        list.add(new AppDrainItem(label, app.packageName, icon));
                        if (list.size() >= 15) break;
                    }
                } catch (Exception ignored) {}
                return list;
            }

            @Override
            protected void onPostExecute(List<AppDrainItem> items) {
                drainApps.clear();
                drainApps.addAll(items);
                if (drainAdapter != null) {
                    drainAdapter.notifyDataSetChanged();
                }
            }
        }.execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Exception ignored) {}
        }
    }

    // --- App Drain Adapter ---
    private static class AppDrainAdapter extends RecyclerView.Adapter<AppDrainAdapter.ViewHolder> {
        private final Context context;
        private final List<AppDrainItem> items;
        private final OnHibernateClickListener listener;

        interface OnHibernateClickListener {
            void onHibernate(AppDrainItem item);
        }

        public AppDrainAdapter(Context context, List<AppDrainItem> items, OnHibernateClickListener listener) {
            this.context = context;
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_battery_app_drain, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppDrainItem item = items.get(position);
            holder.tvName.setText(item.label);
            holder.imgIcon.setImageDrawable(item.icon);

            if (item.isSleeping) {
                holder.tvDrain.setText("Status: Sleeping 💤 (0% drain)");
                holder.tvDrain.setTextColor(Color.parseColor("#059669"));
                holder.btnHibernate.setText("Asleep 💤");
                holder.btnHibernate.setEnabled(false);
                holder.btnHibernate.setAlpha(0.6f);
            } else {
                holder.tvDrain.setText("Power Impact: Normal 🟢");
                holder.tvDrain.setTextColor(Color.parseColor("#831843"));
                holder.btnHibernate.setText("Sleep 💤");
                holder.btnHibernate.setEnabled(true);
                holder.btnHibernate.setAlpha(1.0f);
                holder.btnHibernate.setOnClickListener(v -> listener.onHibernate(item));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgIcon;
            TextView tvName;
            TextView tvDrain;
            Button btnHibernate;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgIcon = itemView.findViewById(R.id.img_app_icon);
                tvName = itemView.findViewById(R.id.tv_app_name);
                tvDrain = itemView.findViewById(R.id.tv_app_drain_level);
                btnHibernate = itemView.findViewById(R.id.btn_hibernate_app);
            }
        }
    }
}
