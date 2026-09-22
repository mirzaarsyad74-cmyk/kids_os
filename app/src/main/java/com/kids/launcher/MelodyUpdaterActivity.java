package com.kids.launcher;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Dedicated In-App OTA Updater for Kids OS.
 * Connected directly to official GitHub Releases (mirzaarsyad74-cmyk/kids_os).
 * Features:
 * - Checks latest release version, changelog, and release assets.
 * - Displays installed vs remote version comparison.
 * - Streams APK download directly with real-time speed & percentage progress.
 * - Seamlessly launches Android Package Installer via FileProvider.
 */
public class MelodyUpdaterActivity extends AppCompatActivity {

    private static final String GITHUB_REPO = "mirzaarsyad74-cmyk/kids_os";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String GITHUB_RELEASE_WEB = "https://github.com/" + GITHUB_REPO + "/releases";

    // Header Views
    private TextView tvUpdaterChannel;
    private TextView btnCheckUpdateHeader;

    // Status & Version Views
    private TextView tvHeroIcon;
    private TextView tvUpdateStatusBadge;
    private TextView tvLastCheckedTime;
    private TextView tvInstalledVersion;
    private TextView tvLatestVersion;
    private TextView tvPackageSize;

    // Download Progress Card
    private View cardDownloadProgress;
    private TextView tvDownloadStatusTitle;
    private TextView tvDownloadPercent;
    private ProgressBar pbDownloadProgress;
    private TextView tvDownloadMetrics;

    // Actions & Changelog
    private TextView btnActionCheck;
    private TextView btnActionDownload;
    private TextView tvReleaseDate;
    private TextView tvReleaseChangelog;
    private TextView btnOpenGithubRelease;

    // State Variables
    private String currentVersionName = "v1.0.0";
    private String latestTag = "v1.0.0";
    private String latestApkDownloadUrl = "";
    private long latestApkSizeBytes = 0;
    private String latestHtmlUrl = GITHUB_RELEASE_WEB;
    private boolean isUpdateAvailable = false;
    private boolean isDownloading = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DeviceBooster.boost(this);
        setWindowUiFlags();
        setContentView(R.layout.activity_melody_updater);

        loadCurrentVersion();
        initViews();
        checkForUpdates(false);
    }

    private void setWindowUiFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void loadCurrentVersion() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersionName = pInfo.versionName;
            if (!currentVersionName.startsWith("v")) {
                currentVersionName = "v" + currentVersionName;
            }
        } catch (Exception e) {
            currentVersionName = "v1.0.0";
        }
    }

    private void initViews() {
        tvUpdaterChannel = findViewById(R.id.tv_updater_channel);
        tvUpdaterChannel.setText("Official Channel: github.com/" + GITHUB_REPO);

        findViewById(R.id.btn_updater_back).setOnClickListener(v -> finish());

        btnCheckUpdateHeader = findViewById(R.id.btn_check_update_header);
        btnCheckUpdateHeader.setOnClickListener(v -> checkForUpdates(true));

        tvHeroIcon = findViewById(R.id.tv_hero_icon);
        tvUpdateStatusBadge = findViewById(R.id.tv_update_status_badge);
        tvLastCheckedTime = findViewById(R.id.tv_last_checked_time);

        tvInstalledVersion = findViewById(R.id.tv_installed_version);
        tvInstalledVersion.setText(currentVersionName);

        tvLatestVersion = findViewById(R.id.tv_latest_version);
        tvPackageSize = findViewById(R.id.tv_package_size);

        cardDownloadProgress = findViewById(R.id.card_download_progress);
        tvDownloadStatusTitle = findViewById(R.id.tv_download_status_title);
        tvDownloadPercent = findViewById(R.id.tv_download_percent);
        pbDownloadProgress = findViewById(R.id.pb_download_progress);
        tvDownloadMetrics = findViewById(R.id.tv_download_metrics);

        btnActionCheck = findViewById(R.id.btn_action_check);
        btnActionCheck.setOnClickListener(v -> checkForUpdates(true));

        btnActionDownload = findViewById(R.id.btn_action_download);
        btnActionDownload.setOnClickListener(v -> startDownloadAndUpdate());

        tvReleaseDate = findViewById(R.id.tv_release_date);
        tvReleaseChangelog = findViewById(R.id.tv_release_changelog);

        btnOpenGithubRelease = findViewById(R.id.btn_open_github_release);
        btnOpenGithubRelease.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(latestHtmlUrl));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkForUpdates(boolean showToast) {
        if (showToast) {
            Toast.makeText(this, "Checking GitHub for updates... 🔄", Toast.LENGTH_SHORT).show();
        }
        btnCheckUpdateHeader.setText("⏳ Checking...");
        tvUpdateStatusBadge.setText("Checking for updates...");
        tvUpdateStatusBadge.setBackgroundResource(R.drawable.bg_melody_pill);
        tvUpdateStatusBadge.setTextColor(Color.parseColor("#831843"));

        new Thread(() -> {
            try {
                URL url = new URL(LATEST_RELEASE_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "KidsOS-Updater");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    latestTag = json.optString("tag_name", "v1.0.0");
                    String releaseName = json.optString("name", "Kids OS " + latestTag);
                    String body = json.optString("body", "No changelog provided.");
                    String publishedAt = json.optString("published_at", "");
                    latestHtmlUrl = json.optString("html_url", GITHUB_RELEASE_WEB);

                    // Find APK asset
                    latestApkDownloadUrl = "";
                    latestApkSizeBytes = 0;
                    JSONArray assets = json.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String assetName = asset.optString("name", "");
                            if (assetName.endsWith(".apk")) {
                                latestApkDownloadUrl = asset.optString("browser_download_url", "");
                                latestApkSizeBytes = asset.optLong("size", 0);
                                break;
                            }
                        }
                    }

                    mainHandler.post(() -> onReleaseInfoLoaded(latestTag, releaseName, body, publishedAt));
                } else {
                    mainHandler.post(() -> onReleaseCheckFailed("GitHub returned HTTP " + code));
                }
            } catch (Exception e) {
                mainHandler.post(() -> onReleaseCheckFailed(e.getMessage()));
            }
        }).start();
    }

    private void onReleaseInfoLoaded(String tag, String name, String changelog, String publishedAt) {
        btnCheckUpdateHeader.setText("🔄 Check Now");

        String timeNow = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tvLastCheckedTime.setText("Last checked: " + timeNow);

        tvLatestVersion.setText(tag);

        if (latestApkSizeBytes > 0) {
            tvPackageSize.setText(String.format(Locale.getDefault(), "%.1f MB", latestApkSizeBytes / (1024.0 * 1024.0)));
        } else {
            tvPackageSize.setText("~11 MB");
        }

        if (publishedAt != null && publishedAt.length() >= 10) {
            tvReleaseDate.setText("Published: " + publishedAt.substring(0, 10));
        } else {
            tvReleaseDate.setText("Published: Latest");
        }

        tvReleaseChangelog.setText(changelog.replace("#", "").trim());

        // Check if tag is different from current
        isUpdateAvailable = isVersionHigher(tag, currentVersionName);

        if (isUpdateAvailable) {
            tvHeroIcon.setText("🚀");
            tvUpdateStatusBadge.setText("🚀 New Update Available: " + tag);
            tvUpdateStatusBadge.setBackgroundResource(R.drawable.bg_melody_chip_selected);
            tvUpdateStatusBadge.setTextColor(Color.WHITE);

            btnActionDownload.setText("🌸 Download & Install " + tag);
            btnActionDownload.setEnabled(true);
        } else {
            tvHeroIcon.setText("✨");
            tvUpdateStatusBadge.setText("✨ System is up to date (" + currentVersionName + ")");
            tvUpdateStatusBadge.setBackgroundResource(R.drawable.bg_melody_chip_selected);
            tvUpdateStatusBadge.setTextColor(Color.WHITE);

            btnActionDownload.setText("🌸 Re-download & Reinstall");
            btnActionDownload.setEnabled(true);
        }
    }

    private void onReleaseCheckFailed(String error) {
        btnCheckUpdateHeader.setText("🔄 Check Now");
        tvUpdateStatusBadge.setText("⚠️ Check failed: " + (error != null ? error : "Network error"));
        tvUpdateStatusBadge.setBackgroundResource(R.drawable.bg_melody_pill);
        tvUpdateStatusBadge.setTextColor(Color.parseColor("#991B1B"));
        tvReleaseChangelog.setText("Could not reach GitHub Releases. Please check your Wi-Fi or Internet connection.");
    }

    private boolean isVersionHigher(String newVer, String curVer) {
        try {
            String v1 = newVer.replace("v", "").trim();
            String v2 = curVer.replace("v", "").trim();
            String[] p1 = v1.split("\\.");
            String[] p2 = v2.split("\\.");
            int length = Math.max(p1.length, p2.length);
            for (int i = 0; i < length; i++) {
                int num1 = i < p1.length ? Integer.parseInt(p1[i]) : 0;
                int num2 = i < p2.length ? Integer.parseInt(p2[i]) : 0;
                if (num1 > num2) return true;
                if (num1 < num2) return false;
            }
        } catch (Exception ignored) {}
        return !newVer.equalsIgnoreCase(curVer);
    }

    private void startDownloadAndUpdate() {
        if (isDownloading) return;

        if (latestApkDownloadUrl == null || latestApkDownloadUrl.trim().isEmpty()) {
            Toast.makeText(this, "No APK asset found for this release yet!", Toast.LENGTH_LONG).show();
            return;
        }

        // Check Unknown Sources Permission for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, "Please allow 'Install Unknown Apps' for Kids Launcher 🌸", Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }

        isDownloading = true;
        btnActionDownload.setEnabled(false);
        btnActionDownload.setText("⏳ Downloading...");
        cardDownloadProgress.setVisibility(View.VISIBLE);
        pbDownloadProgress.setIndeterminate(false);
        pbDownloadProgress.setProgress(0);
        tvDownloadPercent.setText("0%");
        tvDownloadStatusTitle.setText("📥 Downloading Kids OS " + latestTag + "...");
        tvDownloadMetrics.setText("Connecting to GitHub CDN...");

        new Thread(() -> {
            try {
                File cacheDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (cacheDir == null) cacheDir = getCacheDir();
                File targetApk = new File(cacheDir, "kids_os_" + latestTag + ".apk");

                if (targetApk.exists()) {
                    targetApk.delete();
                }

                // Follow GitHub redirects (GitHub releases assets redirect to AWS S3 CDN)
                String downloadUrl = latestApkDownloadUrl;
                HttpURLConnection conn = null;
                for (int redirect = 0; redirect < 5; redirect++) {
                    URL u = new URL(downloadUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "KidsOS-Updater");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);

                    int status = conn.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                        downloadUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                    } else {
                        break;
                    }
                }

                if (conn == null || conn.getResponseCode() != 200) {
                    throw new Exception("Download failed, server returned HTTP " + (conn != null ? conn.getResponseCode() : "null"));
                }

                long totalBytes = conn.getContentLength();
                if (totalBytes <= 0 && latestApkSizeBytes > 0) {
                    totalBytes = latestApkSizeBytes;
                }

                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(targetApk);

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalDownloaded = 0;
                long lastTime = System.currentTimeMillis();
                long bytesSinceLastTime = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalDownloaded += bytesRead;
                    bytesSinceLastTime += bytesRead;

                    long now = System.currentTimeMillis();
                    if (now - lastTime >= 350) {
                        float speedKb = (bytesSinceLastTime / 1024f) / ((now - lastTime) / 1000f);
                        int percent = (totalBytes > 0) ? (int) ((totalDownloaded * 100) / totalBytes) : 0;
                        float downloadedMb = totalDownloaded / (1024f * 1024f);
                        float totalMb = totalBytes / (1024f * 1024f);

                        final int p = percent;
                        final float dMb = downloadedMb;
                        final float tMb = totalMb;
                        final float sKb = speedKb;

                        mainHandler.post(() -> {
                            pbDownloadProgress.setProgress(p);
                            tvDownloadPercent.setText(p + "%");
                            tvDownloadMetrics.setText(String.format(Locale.getDefault(), "%.1f MB / %.1f MB (%.0f KB/s)", dMb, tMb, sKb));
                        });

                        lastTime = now;
                        bytesSinceLastTime = 0;
                    }
                }

                out.flush();
                out.close();
                in.close();
                conn.disconnect();

                mainHandler.post(() -> onDownloadSuccess(targetApk));
            } catch (Exception e) {
                mainHandler.post(() -> onDownloadFailed(e.getMessage()));
            }
        }).start();
    }

    private void onDownloadSuccess(File apkFile) {
        isDownloading = false;
        btnActionDownload.setEnabled(true);
        btnActionDownload.setText("🚀 Auto-Installing Update...");

        pbDownloadProgress.setProgress(100);
        tvDownloadPercent.setText("100%");
        tvDownloadStatusTitle.setText("✨ Download Complete! Auto-installing update now... 🌸");
        tvDownloadMetrics.setText("Package: " + String.format(Locale.getDefault(), "%.1f MB", apkFile.length() / (1024f * 1024f)) + " (Hands-Free Installation)");

        Toast.makeText(this, "Update downloaded! Auto-installing Kids OS... 🚀🌸", Toast.LENGTH_SHORT).show();

        // Arm the accessibility service to auto-click Install & Open without user input
        MelodyGlobalService.setPendingAutoInstall(true);

        promptInstallApk(apkFile);
    }

    private void onDownloadFailed(String error) {
        isDownloading = false;
        btnActionDownload.setEnabled(true);
        btnActionDownload.setText("🌸 Retry Download");
        tvDownloadStatusTitle.setText("❌ Download Failed");
        tvDownloadMetrics.setText(error != null ? error : "Unknown error occurred.");
        Toast.makeText(this, "Download failed: " + error, Toast.LENGTH_LONG).show();
    }

    private void promptInstallApk(File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(this, "com.kids.launcher.fileprovider", apkFile);

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not start installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
