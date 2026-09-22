package com.kids.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class PreferencesManager {
    private static final String PREF_NAME = "kids_launcher_prefs";
    private static final String KEY_PIN = "parent_pin";
    private static final String KEY_ALLOWED_PACKAGES = "allowed_packages";
    private static final String KEY_TIME_LIMIT_MINUTES = "time_limit_minutes";
    private static final String KEY_ELAPSED_SECONDS = "elapsed_seconds";
    private static final String KEY_LAST_DATE = "last_date";
    private static final String KEY_INITIALIZED = "is_initialized";
    private static final String KEY_AVATAR = "kid_avatar";
    private static final String KEY_KID_NAME = "kid_name";

    // Advanced Parental Controls Keys
    private static final String KEY_BEDTIME_ENABLED = "bedtime_enabled";
    private static final String KEY_BEDTIME_START_HOUR = "bedtime_start_hour"; // 0-23
    private static final String KEY_BEDTIME_END_HOUR = "bedtime_end_hour";     // 0-23
    private static final String KEY_BREAK_TIMER_ENABLED = "break_timer_enabled";
    private static final String KEY_BREAK_INTERVAL_MINS = "break_interval_mins";
    private static final String KEY_CONTINUOUS_SECONDS = "continuous_seconds";
    private static final String KEY_LOCK_GAMES = "lock_games";
    private static final String KEY_LOCK_BROWSERS = "lock_browsers";
    private static final String KEY_AUTO_BRIGHTNESS = "auto_brightness_enabled";

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        checkDailyReset();
    }

    public String getAvatar() {
        return prefs.getString(KEY_AVATAR, "🐰");
    }

    public void setAvatar(String avatar) {
        prefs.edit().putString(KEY_AVATAR, avatar).apply();
    }

    public String getKidName() {
        return prefs.getString(KEY_KID_NAME, "Kid Explorer");
    }

    public void setKidName(String name) {
        prefs.edit().putString(KEY_KID_NAME, name).apply();
    }

    private void checkDailyReset() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String lastDate = prefs.getString(KEY_LAST_DATE, "");
        if (!today.equals(lastDate)) {
            prefs.edit()
                    .putString(KEY_LAST_DATE, today)
                    .putInt(KEY_ELAPSED_SECONDS, 0)
                    .putInt(KEY_CONTINUOUS_SECONDS, 0)
                    .apply();
        }
    }

    public boolean isInitialized() {
        return prefs.getBoolean(KEY_INITIALIZED, false);
    }

    public void setInitialized(boolean initialized) {
        prefs.edit().putBoolean(KEY_INITIALIZED, initialized).apply();
    }

    public String getPin() {
        return prefs.getString(KEY_PIN, "1234");
    }

    public void setPin(String pin) {
        prefs.edit().putString(KEY_PIN, pin).apply();
    }

    public Set<String> getAllowedPackages() {
        return new HashSet<>(prefs.getStringSet(KEY_ALLOWED_PACKAGES, new HashSet<String>()));
    }

    public void setAllowedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, new HashSet<>(packages)).apply();
    }

    public void addAllowedPackage(String pkg) {
        Set<String> current = getAllowedPackages();
        current.add(pkg);
        setAllowedPackages(current);
    }

    public void removeAllowedPackage(String pkg) {
        Set<String> current = getAllowedPackages();
        current.remove(pkg);
        setAllowedPackages(current);
    }

    public boolean isPackageAllowed(String pkg) {
        return getAllowedPackages().contains(pkg);
    }

    public int getTimeLimitMinutes() {
        return prefs.getInt(KEY_TIME_LIMIT_MINUTES, 0); // 0 = Unlimited
    }

    public void setTimeLimitMinutes(int minutes) {
        prefs.edit().putInt(KEY_TIME_LIMIT_MINUTES, minutes).apply();
    }

    public int getElapsedSeconds() {
        return prefs.getInt(KEY_ELAPSED_SECONDS, 0);
    }

    public void incrementElapsedSeconds(int seconds) {
        int current = getElapsedSeconds() + seconds;
        prefs.edit().putInt(KEY_ELAPSED_SECONDS, current).apply();
    }

    public void resetTimer() {
        prefs.edit()
                .putInt(KEY_ELAPSED_SECONDS, 0)
                .putInt(KEY_CONTINUOUS_SECONDS, 0)
                .apply();
    }

    public boolean isTimeUp() {
        int limit = getTimeLimitMinutes();
        if (limit <= 0) return false;
        return getElapsedSeconds() >= (limit * 60);
    }

    public int getRemainingSeconds() {
        int limit = getTimeLimitMinutes();
        if (limit <= 0) return Integer.MAX_VALUE;
        int remaining = (limit * 60) - getElapsedSeconds();
        return Math.max(0, remaining);
    }

    // --- Bedtime Curfew Methods ---

    public boolean isBedtimeEnabled() {
        return prefs.getBoolean(KEY_BEDTIME_ENABLED, false);
    }

    public void setBedtimeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BEDTIME_ENABLED, enabled).apply();
    }

    public int getBedtimeStartHour() {
        return prefs.getInt(KEY_BEDTIME_START_HOUR, 20); // 8:00 PM
    }

    public void setBedtimeStartHour(int hour) {
        prefs.edit().putInt(KEY_BEDTIME_START_HOUR, hour).apply();
    }

    public int getBedtimeEndHour() {
        return prefs.getInt(KEY_BEDTIME_END_HOUR, 7); // 7:00 AM
    }

    public void setBedtimeEndHour(int hour) {
        prefs.edit().putInt(KEY_BEDTIME_END_HOUR, hour).apply();
    }

    public boolean isBedtimeNow() {
        if (!isBedtimeEnabled()) return false;
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int start = getBedtimeStartHour();
        int end = getBedtimeEndHour();

        if (start > end) {
            // Over midnight, e.g. 20:00 to 07:00
            return currentHour >= start || currentHour < end;
        } else {
            // Same day, e.g. 13:00 to 15:00
            return currentHour >= start && currentHour < end;
        }
    }

    // --- Eye Break Timer Methods ---

    public boolean isBreakTimerEnabled() {
        return prefs.getBoolean(KEY_BREAK_TIMER_ENABLED, false);
    }

    public void setBreakTimerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BREAK_TIMER_ENABLED, enabled).apply();
    }

    public int getBreakIntervalMins() {
        return prefs.getInt(KEY_BREAK_INTERVAL_MINS, 20); // 20 minutes
    }

    public void setBreakIntervalMins(int mins) {
        prefs.edit().putInt(KEY_BREAK_INTERVAL_MINS, mins).apply();
    }

    public int getContinuousSeconds() {
        return prefs.getInt(KEY_CONTINUOUS_SECONDS, 0);
    }

    public void incrementContinuousSeconds(int seconds) {
        int current = getContinuousSeconds() + seconds;
        prefs.edit().putInt(KEY_CONTINUOUS_SECONDS, current).apply();
    }

    public void resetContinuousSeconds() {
        prefs.edit().putInt(KEY_CONTINUOUS_SECONDS, 0).apply();
    }

    public boolean isBreakTimeNow() {
        if (!isBreakTimerEnabled()) return false;
        int intervalSecs = getBreakIntervalMins() * 60;
        return getContinuousSeconds() >= intervalSecs;
    }

    // --- Background Timer Persistence ---
    private static final String KEY_TIMER_RUNNING = "timer_running";
    private static final String KEY_TIMER_END_TIME = "timer_end_time";
    private static final String KEY_TIMER_TOTAL_SECONDS = "timer_total_seconds";

    public boolean isTimerRunning() {
        return prefs.getBoolean(KEY_TIMER_RUNNING, false);
    }

    public void setTimerRunning(boolean running) {
        prefs.edit().putBoolean(KEY_TIMER_RUNNING, running).apply();
    }

    public long getTimerEndTime() {
        return prefs.getLong(KEY_TIMER_END_TIME, 0L);
    }

    public void setTimerEndTime(long timeMillis) {
        prefs.edit().putLong(KEY_TIMER_END_TIME, timeMillis).apply();
    }

    public int getTimerTotalSeconds() {
        return prefs.getInt(KEY_TIMER_TOTAL_SECONDS, 300);
    }

    public void setTimerTotalSeconds(int seconds) {
        prefs.edit().putInt(KEY_TIMER_TOTAL_SECONDS, seconds).apply();
    }

    // --- Alarm Methods ---
    private static final String KEY_ALARM_ENABLED = "alarm_enabled";
    private static final String KEY_ALARM_HOUR = "alarm_hour";
    private static final String KEY_ALARM_MINUTE = "alarm_minute";
    private static final String KEY_ALARM_LABEL = "alarm_label";

    public boolean isAlarmEnabled() {
        return prefs.getBoolean(KEY_ALARM_ENABLED, false);
    }

    public void setAlarmEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALARM_ENABLED, enabled).apply();
    }

    public int getAlarmHour() {
        return prefs.getInt(KEY_ALARM_HOUR, 7); // Default 7:00 AM
    }

    public void setAlarmHour(int hour) {
        prefs.edit().putInt(KEY_ALARM_HOUR, hour).apply();
    }

    public int getAlarmMinute() {
        return prefs.getInt(KEY_ALARM_MINUTE, 0);
    }

    public void setAlarmMinute(int minute) {
        prefs.edit().putInt(KEY_ALARM_MINUTE, minute).apply();
    }

    public String getAlarmLabel() {
        return prefs.getString(KEY_ALARM_LABEL, "Wake Up ☀️");
    }

    public void setAlarmLabel(String label) {
        prefs.edit().putString(KEY_ALARM_LABEL, label).apply();
    }

    // --- Multi-Alarm Methods ---
    private static final String KEY_MULTI_ALARMS_JSON = "multi_alarms_json";

    public List<MelodyAlarmModel> getAlarms() {
        String jsonStr = prefs.getString(KEY_MULTI_ALARMS_JSON, null);
        List<MelodyAlarmModel> list = new ArrayList<>();
        if (jsonStr != null && !jsonStr.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    MelodyAlarmModel model = MelodyAlarmModel.fromJson(arr.getJSONObject(i));
                    if (model != null) list.add(model);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (list.isEmpty()) {
            list.add(new MelodyAlarmModel(1001, 7, 0, "Wake Up ☀️", "🌸 Sweet Melody", true));
            list.add(new MelodyAlarmModel(1002, 12, 30, "Lunch Time 🍱", "✨ Sparkle Bell", false));
            list.add(new MelodyAlarmModel(1003, 20, 0, "Bedtime 🌙", "🎵 Gentle Chime", false));
            saveAlarms(list);
        }
        return list;
    }

    public void saveAlarms(List<MelodyAlarmModel> alarms) {
        JSONArray arr = new JSONArray();
        for (MelodyAlarmModel alarm : alarms) {
            arr.put(alarm.toJson());
        }
        prefs.edit().putString(KEY_MULTI_ALARMS_JSON, arr.toString()).apply();
    }

    // --- Category Lock Methods ---

    public boolean isLockGames() {
        return prefs.getBoolean(KEY_LOCK_GAMES, false);
    }

    public void setLockGames(boolean lock) {
        prefs.edit().putBoolean(KEY_LOCK_GAMES, lock).apply();
    }

    public boolean isLockBrowsers() {
        return prefs.getBoolean(KEY_LOCK_BROWSERS, false);
    }

    public void setLockBrowsers(boolean lock) {
        prefs.edit().putBoolean(KEY_LOCK_BROWSERS, lock).apply();
    }

    public boolean isAutoBrightness() {
        return prefs.getBoolean(KEY_AUTO_BRIGHTNESS, true);
    }

    public void setAutoBrightness(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_BRIGHTNESS, enabled).apply();
    }

    // --- App Category Customization ---
    private static final String KEY_APP_CATEGORY_PREFIX = "app_category_";

    public String getAppCategory(String packageName, String defaultCategory) {
        return prefs.getString(KEY_APP_CATEGORY_PREFIX + packageName, defaultCategory);
    }

    public void setAppCategory(String packageName, String category) {
        prefs.edit().putString(KEY_APP_CATEGORY_PREFIX + packageName, category).apply();
    }

    // --- Battery Saver (< 20%) ---
    private static final String KEY_AUTO_BATTERY_SAVER = "auto_battery_saver_enabled";
    private static final String KEY_BATTERY_SAVER_ACTIVE = "battery_saver_active";

    public boolean isAutoBatterySaverEnabled() {
        return prefs.getBoolean(KEY_AUTO_BATTERY_SAVER, true);
    }

    public void setAutoBatterySaverEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_BATTERY_SAVER, enabled).apply();
    }

    public boolean isBatterySaverActive() {
        return prefs.getBoolean(KEY_BATTERY_SAVER_ACTIVE, false);
    }

    public void setBatterySaverActive(boolean active) {
        prefs.edit().putBoolean(KEY_BATTERY_SAVER_ACTIVE, active).apply();
    }

    // --- Full Battery Sound Alert (100%) ---
    private static final String KEY_FULL_BATTERY_ALERT = "full_battery_alert_enabled";

    public boolean isFullBatteryAlertEnabled() {
        return prefs.getBoolean(KEY_FULL_BATTERY_ALERT, true);
    }

    public void setFullBatteryAlertEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FULL_BATTERY_ALERT, enabled).apply();
    }

    // --- App Lock / Block List ---
    private static final String KEY_LOCKED_PACKAGES = "locked_packages";

    public Set<String> getLockedPackages() {
        return new HashSet<>(prefs.getStringSet(KEY_LOCKED_PACKAGES, new HashSet<String>()));
    }

    public void setPackageLocked(String pkg, boolean locked) {
        Set<String> set = getLockedPackages();
        if (locked) {
            set.add(pkg);
        } else {
            set.remove(pkg);
        }
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, set).apply();
    }

    public boolean isPackageLocked(String pkg) {
        return getLockedPackages().contains(pkg);
    }

    // --- App Install Approval ---
    private static final String KEY_PENDING_APPROVAL_PACKAGES = "pending_approval_packages";

    public Set<String> getPendingApprovalPackages() {
        return new HashSet<>(prefs.getStringSet(KEY_PENDING_APPROVAL_PACKAGES, new HashSet<String>()));
    }

    public void addPendingApprovalPackage(String pkg) {
        Set<String> set = getPendingApprovalPackages();
        set.add(pkg);
        prefs.edit().putStringSet(KEY_PENDING_APPROVAL_PACKAGES, set).apply();
    }

    public void removePendingApprovalPackage(String pkg) {
        Set<String> set = getPendingApprovalPackages();
        set.remove(pkg);
        prefs.edit().putStringSet(KEY_PENDING_APPROVAL_PACKAGES, set).apply();
    }

    public boolean isPackagePendingApproval(String pkg) {
        return getPendingApprovalPackages().contains(pkg);
    }

    public void approvePackage(String pkg) {
        removePendingApprovalPackage(pkg);
        addAllowedPackage(pkg);
    }

    public void blockPackage(String pkg) {
        removePendingApprovalPackage(pkg);
        removeAllowedPackage(pkg);
    }

    // --- Safe Browsing Whitelist ---
    private static final String KEY_SAFE_WEB_WHITELIST = "safe_web_whitelist";

    public Set<String> getSafeWebWhitelist() {
        Set<String> defaultSet = new HashSet<>();
        defaultSet.add("pbskids.org");
        defaultSet.add("nationalgeographic.com");
        defaultSet.add("wikipedia.org");
        defaultSet.add("scratch.mit.edu");
        defaultSet.add("kiddle.co");
        return new HashSet<>(prefs.getStringSet(KEY_SAFE_WEB_WHITELIST, defaultSet));
    }

    public void addSafeWebDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) return;
        Set<String> set = getSafeWebWhitelist();
        set.add(domain.trim().toLowerCase(Locale.ROOT));
        prefs.edit().putStringSet(KEY_SAFE_WEB_WHITELIST, set).apply();
    }

    public void removeSafeWebDomain(String domain) {
        if (domain == null) return;
        Set<String> set = getSafeWebWhitelist();
        set.remove(domain.trim().toLowerCase(Locale.ROOT));
        prefs.edit().putStringSet(KEY_SAFE_WEB_WHITELIST, set).apply();
    }

    // --- SOS / Emergency Contact ---
    private static final String KEY_SOS_NAME = "sos_contact_name";
    private static final String KEY_SOS_NUMBER = "sos_contact_number";

    public String getSosContactName() {
        return prefs.getString(KEY_SOS_NAME, "Mama");
    }

    public void setSosContactName(String name) {
        prefs.edit().putString(KEY_SOS_NAME, name).apply();
    }

    public String getSosPhoneNumber() {
        return prefs.getString(KEY_SOS_NUMBER, "");
    }

    public void setSosPhoneNumber(String number) {
        prefs.edit().putString(KEY_SOS_NUMBER, number).apply();
    }

    // --- Onboarding & Profile ---
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";

    public boolean isOnboardingCompleted() {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }

    // --- Themes & Wallpaper ---
    private static final String KEY_SELECTED_THEME = "selected_theme";
    private static final String KEY_SELECTED_WALLPAPER = "selected_wallpaper";

    public String getSelectedTheme() {
        return prefs.getString(KEY_SELECTED_THEME, "pink");
    }

    public void setSelectedTheme(String theme) {
        prefs.edit().putString(KEY_SELECTED_THEME, theme).apply();
    }

    public int getSelectedWallpaper() {
        return prefs.getInt(KEY_SELECTED_WALLPAPER, 0);
    }

    public void setSelectedWallpaper(int wallpaperIndex) {
        prefs.edit().putInt(KEY_SELECTED_WALLPAPER, wallpaperIndex).apply();
    }

    // --- Health & Wellbeing ---
    private static final String KEY_BLUE_LIGHT_FILTER = "blue_light_filter_enabled";
    private static final String KEY_POSTURE_REMINDER = "posture_reminder_enabled";
    private static final String KEY_VOLUME_LIMITER = "volume_limiter_enabled";
    private static final String KEY_VOLUME_CAP_PERCENT = "volume_cap_percent";

    public boolean isBlueLightFilterEnabled() {
        return prefs.getBoolean(KEY_BLUE_LIGHT_FILTER, false);
    }

    public void setBlueLightFilterEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BLUE_LIGHT_FILTER, enabled).apply();
    }

    public boolean isPostureReminderEnabled() {
        return prefs.getBoolean(KEY_POSTURE_REMINDER, true);
    }

    public void setPostureReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_POSTURE_REMINDER, enabled).apply();
    }

    public boolean isVolumeLimiterEnabled() {
        return prefs.getBoolean(KEY_VOLUME_LIMITER, true);
    }

    public void setVolumeLimiterEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOLUME_LIMITER, enabled).apply();
    }

    public int getVolumeCapPercent() {
        return prefs.getInt(KEY_VOLUME_CAP_PERCENT, 70);
    }

    public void setVolumeCapPercent(int percent) {
        prefs.edit().putInt(KEY_VOLUME_CAP_PERCENT, percent).apply();
    }
}
