package com.kids.launcher;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MX Player Style Video Player for Kids Launcher.
 * Features:
 * - Vertical Swipe on Left side: Brightness adjustment with HUD.
 * - Vertical Swipe on Right side: Volume adjustment with HUD.
 * - Horizontal Swipe: Seek scrubbing preview HUD.
 * - Double-Tap: Left (-10s), Right (+10s), Center (Play/Pause).
 * - Screen Lock: Prevents accidental child interruptions with floating unlock button.
 * - Aspect Ratio Switcher: Fit, Fill/Stretch, Zoom 16:9 crop.
 * - Playback Speed: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x.
 * - Header: Video Title, live Clock & Battery level.
 * - Bottom Bar: Prev/Next track, Mute toggle, Loop toggle, Orientation rotate.
 */
public class MelodyVideoActivity extends AppCompatActivity {

    public static class VideoItem {
        public final String title;
        public final String path;
        public final long durationMs;
        public final long sizeBytes;
        public final long dateAdded;

        public VideoItem(String title, String path, long durationMs, long sizeBytes, long dateAdded) {
            this.title = title;
            this.path = path;
            this.durationMs = durationMs;
            this.sizeBytes = sizeBytes;
            this.dateAdded = dateAdded;
        }

        public String getFormattedDuration() {
            if (durationMs <= 0) return "00:00";
            long totalSec = durationMs / 1000;
            long hours = totalSec / 3600;
            long min = (totalSec % 3600) / 60;
            long sec = totalSec % 60;
            if (hours > 0) {
                return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, min, sec);
            }
            return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
        }

        public String getFormattedSize() {
            if (sizeBytes < 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f KB", sizeBytes / 1024.0);
            }
            if (sizeBytes < 1024 * 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f MB", sizeBytes / (1024.0 * 1024.0));
            }
            return String.format(Locale.getDefault(), "%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VideoItem videoItem = (VideoItem) o;
            return path != null ? path.equalsIgnoreCase(videoItem.path) : videoItem.path == null;
        }

        @Override
        public int hashCode() {
            return path != null ? path.toLowerCase(Locale.US).hashCode() : 0;
        }
    }

    private View layoutGalleryView;
    private View layoutEmptyVideos;
    private TextView tvVideoCount;
    private EditText etSearchVideos;
    private RecyclerView rvVideosGrid;
    private VideoAdapter videoAdapter;

    // Player Main Views
    private FrameLayout layoutPlayerContainer;
    private VideoView vvPlayer;
    private RelativeLayout layoutPlayerControls;
    private TextView tvPlayerVideoTitle;
    private TextView btnVideoPlayPause;
    private TextView tvPlayerCurrentTime;
    private TextView tvPlayerTotalTime;
    private SeekBar sbPlayerProgress;

    // On-Screen HUD Overlays
    private LinearLayout layoutHudVolume;
    private TextView tvHudVolumeIcon;
    private ProgressBar pbHudVolume;
    private TextView tvHudVolumeText;

    private LinearLayout layoutHudBrightness;
    private TextView tvHudBrightnessIcon;
    private ProgressBar pbHudBrightness;
    private TextView tvHudBrightnessText;

    private LinearLayout layoutHudSeek;
    private TextView tvHudSeekDiff;
    private TextView tvHudSeekTarget;

    private TextView tvHudDoubleTapLeft;
    private TextView tvHudDoubleTapRight;
    private TextView btnPlayerUnlock;

    // Top Controls
    private TextView tvPlayerClockBattery;
    private TextView btnPlayerAspect;
    private TextView btnPlayerSpeed;
    private TextView btnPlayerLock;

    // Bottom Controls
    private TextView btnPlayerMute;
    private TextView btnPlayerLoop;
    private TextView btnVideoPrev;
    private TextView btnVideoRewind;
    private TextView btnVideoForward;
    private TextView btnVideoNext;
    private TextView btnPlayerRotate;

    // State & Gesture variables
    private AudioManager audioManager;
    private MediaPlayer underlyingMediaPlayer;
    private GestureDetector gestureDetector;

    private boolean isScreenLocked = false;
    private boolean isMuted = false;
    private int volumeBeforeMute = -1;
    private boolean isLooping = false;
    private int currentAspectMode = 0; // 0: Fit, 1: Fill, 2: Zoom
    private int currentSpeedIndex = 0;
    private final float[] PLAYBACK_SPEEDS = {1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f};
    private final String[] PLAYBACK_SPEED_LABELS = {"⚡ 1.0x", "⚡ 1.25x", "⚡ 1.5x", "⚡ 2.0x", "⚡ 0.5x", "⚡ 0.75x"};

    private VideoItem currentVideoItem = null;
    private int currentPlayingIndex = -1;
    private int batteryPercent = 100;
    private BroadcastReceiver batteryReceiver;

    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private boolean isSwipeHorizontal = false;
    private boolean isSwipeVertical = false;
    private boolean isSwipeLeftHalf = false;
    private float startBrightness = 0.5f;
    private int startVolume = 0;
    private int startSeekPositionMs = 0;
    private int targetSeekPositionMs = 0;
    private static final int TOUCH_SLOP = 25;

    private boolean isPlayerSeeking = false;
    private final Handler playerHandler = new Handler(Looper.getMainLooper());

    private final Runnable hideControlsRunnable = () -> {
        if (layoutPlayerControls != null && vvPlayer != null && vvPlayer.isPlaying() && !isScreenLocked) {
            layoutPlayerControls.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                layoutPlayerControls.setVisibility(View.GONE);
            }).start();
        }
    };

    private final Runnable hideUnlockButtonRunnable = () -> {
        if (btnPlayerUnlock != null && isScreenLocked) {
            btnPlayerUnlock.animate().alpha(0.35f).setDuration(400).start();
        }
    };

    private int getCurrentTotalDuration() {
        int dur = 0;
        if (vvPlayer != null) {
            try {
                dur = vvPlayer.getDuration();
            } catch (Exception ignored) {}
        }
        if (dur <= 0 && currentVideoItem != null && currentVideoItem.durationMs > 0) {
            dur = (int) currentVideoItem.durationMs;
        }
        return Math.max(0, dur);
    }

    private void updateProgressUI(int currentMs) {
        int total = getCurrentTotalDuration();
        if (total > 0) {
            int prog = (int) (((long) currentMs * 1000) / total);
            sbPlayerProgress.setProgress(Math.max(0, Math.min(1000, prog)));
            tvPlayerTotalTime.setText(formatTime(total));
        }
        tvPlayerCurrentTime.setText(formatTime(Math.max(0, currentMs)));
    }

    private void showDoubleTapHud(boolean isForward) {
        TextView hud = isForward ? tvHudDoubleTapRight : tvHudDoubleTapLeft;
        if (hud != null) {
            hud.setVisibility(View.VISIBLE);
            hud.setAlpha(1.0f);
            hud.animate().alpha(0f).setDuration(600).withEndAction(() -> {
                hud.setVisibility(View.GONE);
            }).start();
        }
    }

    private int findVideoIndex(String path, List<VideoItem> list) {
        if (path == null || list == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (path.equalsIgnoreCase(list.get(i).path)) {
                return i;
            }
        }
        return -1;
    }

    private final Runnable progressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (vvPlayer != null && !isPlayerSeeking) {
                try {
                    int cur = vvPlayer.getCurrentPosition();
                    int total = getCurrentTotalDuration();
                    if (total > 0) {
                        int prog = (int) (((long) cur * 1000) / total);
                        sbPlayerProgress.setProgress(Math.max(0, Math.min(1000, prog)));
                        tvPlayerTotalTime.setText(formatTime(total));
                    }
                    tvPlayerCurrentTime.setText(formatTime(Math.max(0, cur)));
                    if (vvPlayer.isPlaying()) {
                        btnVideoPlayPause.setText("⏸");
                    } else {
                        btnVideoPlayPause.setText("▶");
                    }
                } catch (Exception ignored) {}
            }
            playerHandler.postDelayed(this, 300);
        }
    };

    private final Runnable clockBatteryRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvPlayerClockBattery != null) {
                String timeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                tvPlayerClockBattery.setText(timeStr + " | 🔋 " + batteryPercent + "%");
            }
            playerHandler.postDelayed(this, 10000);
        }
    };

    private final List<VideoItem> allVideos = new ArrayList<>();
    private final List<VideoItem> displayedVideos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DeviceBooster.boost(this);
        setWindowUiFlags();
        setContentView(R.layout.activity_melody_video);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        initViews();
        setupSearch();
        setupGestures();
        setupBatteryReceiver();
        playerHandler.post(clockBatteryRunnable);

        scanAllVideos();

        // Check if an external intent requested a specific video path
        String targetPath = getIntent().getStringExtra("target_video_path");
        if (targetPath != null && new File(targetPath).exists()) {
            playVideo(new VideoItem(new File(targetPath).getName(), targetPath, 0, new File(targetPath).length(), 0));
        }
    }

    private void setWindowUiFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void initViews() {
        layoutGalleryView = findViewById(R.id.layout_gallery_view);
        layoutEmptyVideos = findViewById(R.id.layout_empty_videos);
        tvVideoCount = findViewById(R.id.tv_video_count);
        etSearchVideos = findViewById(R.id.et_search_videos);
        rvVideosGrid = findViewById(R.id.rv_videos_grid);

        // Player Views
        layoutPlayerContainer = findViewById(R.id.layout_player_container);
        vvPlayer = findViewById(R.id.vv_player);
        layoutPlayerControls = findViewById(R.id.layout_player_controls);
        tvPlayerVideoTitle = findViewById(R.id.tv_player_video_title);
        btnVideoPlayPause = findViewById(R.id.btn_video_play_pause);
        tvPlayerCurrentTime = findViewById(R.id.tv_player_current_time);
        tvPlayerTotalTime = findViewById(R.id.tv_player_total_time);
        sbPlayerProgress = findViewById(R.id.sb_player_progress);

        // HUD Elements
        layoutHudVolume = findViewById(R.id.layout_hud_volume);
        tvHudVolumeIcon = findViewById(R.id.tv_hud_volume_icon);
        pbHudVolume = findViewById(R.id.pb_hud_volume);
        tvHudVolumeText = findViewById(R.id.tv_hud_volume_text);

        layoutHudBrightness = findViewById(R.id.layout_hud_brightness);
        tvHudBrightnessIcon = findViewById(R.id.tv_hud_brightness_icon);
        pbHudBrightness = findViewById(R.id.pb_hud_brightness);
        tvHudBrightnessText = findViewById(R.id.tv_hud_brightness_text);

        layoutHudSeek = findViewById(R.id.layout_hud_seek);
        tvHudSeekDiff = findViewById(R.id.tv_hud_seek_diff);
        tvHudSeekTarget = findViewById(R.id.tv_hud_seek_target);

        tvHudDoubleTapLeft = findViewById(R.id.tv_hud_double_tap_left);
        tvHudDoubleTapRight = findViewById(R.id.tv_hud_double_tap_right);
        btnPlayerUnlock = findViewById(R.id.btn_player_unlock);

        // Top Bar
        tvPlayerClockBattery = findViewById(R.id.tv_player_clock_battery);
        btnPlayerAspect = findViewById(R.id.btn_player_aspect);
        btnPlayerSpeed = findViewById(R.id.btn_player_speed);
        btnPlayerLock = findViewById(R.id.btn_player_lock);

        // Bottom Bar
        btnPlayerMute = findViewById(R.id.btn_player_mute);
        btnPlayerLoop = findViewById(R.id.btn_player_loop);
        btnVideoPrev = findViewById(R.id.btn_video_prev);
        btnVideoRewind = findViewById(R.id.btn_video_rewind);
        btnVideoForward = findViewById(R.id.btn_video_forward);
        btnVideoNext = findViewById(R.id.btn_video_next);
        btnPlayerRotate = findViewById(R.id.btn_player_rotate);

        findViewById(R.id.btn_video_gallery_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_video_rescan).setOnClickListener(v -> {
            scanAllVideos();
            Toast.makeText(this, "Refreshed: " + allVideos.size() + " videos found 🌸", Toast.LENGTH_SHORT).show();
        });

        View btnEmptyRescan = findViewById(R.id.btn_empty_video_rescan);
        if (btnEmptyRescan != null) {
            btnEmptyRescan.setOnClickListener(v -> {
                scanAllVideos();
                Toast.makeText(this, "Refreshed: " + allVideos.size() + " videos found 🌸", Toast.LENGTH_SHORT).show();
            });
        }

        // Setup RecyclerView Grid (3 columns)
        rvVideosGrid.setLayoutManager(new GridLayoutManager(this, 3));
        videoAdapter = new VideoAdapter();
        rvVideosGrid.setAdapter(videoAdapter);

        // Player Controls Actions
        findViewById(R.id.btn_close_player).setOnClickListener(v -> closePlayer());

        btnVideoPlayPause.setOnClickListener(v -> togglePlayPause());
        btnVideoRewind.setOnClickListener(v -> rewind10s());
        btnVideoForward.setOnClickListener(v -> forward10s());

        btnVideoNext.setOnClickListener(v -> playNextVideo());
        btnVideoPrev.setOnClickListener(v -> playPrevVideo());

        btnPlayerLock.setOnClickListener(v -> toggleScreenLock());
        btnPlayerUnlock.setOnClickListener(v -> toggleScreenLock());

        btnPlayerAspect.setOnClickListener(v -> cycleAspectRatio());
        btnPlayerSpeed.setOnClickListener(v -> cyclePlaybackSpeed());
        btnPlayerMute.setOnClickListener(v -> toggleMute());
        btnPlayerLoop.setOnClickListener(v -> toggleLoop());
        btnPlayerRotate.setOnClickListener(v -> toggleOrientation());

        layoutPlayerControls.setOnClickListener(v -> toggleControls());

        sbPlayerProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && vvPlayer != null) {
                    int total = getCurrentTotalDuration();
                    if (total > 0) {
                        int targetMs = (int) (((long) progress * total) / 1000);
                        tvPlayerCurrentTime.setText(formatTime(targetMs));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isPlayerSeeking = true;
                playerHandler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isPlayerSeeking = false;
                if (vvPlayer != null) {
                    int total = getCurrentTotalDuration();
                    if (total > 0) {
                        int targetMs = (int) (((long) seekBar.getProgress() * total) / 1000);
                        vvPlayer.seekTo(targetMs);
                        updateProgressUI(targetMs);
                    }
                }
                scheduleHideControls();
            }
        });
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!isScreenLocked) {
                    toggleControls();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isScreenLocked) return false;
                int width = layoutPlayerContainer.getWidth();
                float x = e.getX();
                if (x < width * 0.35f) {
                    rewind10s();
                } else if (x > width * 0.65f) {
                    forward10s();
                } else {
                    togglePlayPause();
                }
                return true;
            }
        });

        layoutPlayerContainer.setOnTouchListener((v, event) -> {
            if (isScreenLocked) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    btnPlayerUnlock.setVisibility(View.VISIBLE);
                    btnPlayerUnlock.setAlpha(1.0f);
                    playerHandler.removeCallbacks(hideUnlockButtonRunnable);
                    playerHandler.postDelayed(hideUnlockButtonRunnable, 3000);
                }
                return true;
            }

            if (layoutPlayerControls != null && layoutPlayerControls.getVisibility() == View.VISIBLE) {
                return false;
            }

            gestureDetector.onTouchEvent(event);

            float currentX = event.getX();
            float currentY = event.getY();
            int width = layoutPlayerContainer.getWidth();
            int height = layoutPlayerContainer.getHeight();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownX = currentX;
                    touchDownY = currentY;
                    isSwipeHorizontal = false;
                    isSwipeVertical = false;
                    isSwipeLeftHalf = (currentX < width * 0.5f);
                    startBrightness = getWindowBrightness();
                    if (audioManager != null) {
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    }
                    if (vvPlayer != null) {
                        startSeekPositionMs = vvPlayer.getCurrentPosition();
                        targetSeekPositionMs = startSeekPositionMs;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = currentX - touchDownX;
                    float deltaY = currentY - touchDownY;

                    if (!isSwipeHorizontal && !isSwipeVertical) {
                        if (Math.abs(deltaY) > TOUCH_SLOP && Math.abs(deltaY) > Math.abs(deltaX) * 1.2f) {
                            isSwipeVertical = true;
                            playerHandler.removeCallbacks(hideControlsRunnable);
                        } else if (Math.abs(deltaX) > TOUCH_SLOP && Math.abs(deltaX) > Math.abs(deltaY) * 1.2f) {
                            isSwipeHorizontal = true;
                            playerHandler.removeCallbacks(hideControlsRunnable);
                        }
                    }

                    if (isSwipeVertical) {
                        float percentDelta = -deltaY / (float) (height * 0.75f);
                        if (isSwipeLeftHalf) {
                            // Brightness swipe on left side
                            float newB = Math.max(0.01f, Math.min(1.0f, startBrightness + percentDelta));
                            setScreenBrightness(newB);
                            showBrightnessHud((int) (newB * 100));
                        } else {
                            // Volume swipe on right side
                            if (audioManager != null) {
                                int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                                int volChange = (int) (percentDelta * maxVol);
                                int newVol = Math.max(0, Math.min(maxVol, startVolume + volChange));
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                                showVolumeHud(newVol, maxVol);
                            }
                        }
                    } else if (isSwipeHorizontal) {
                        // Seek swipe preview
                        if (vvPlayer != null) {
                            int totalDuration = vvPlayer.getDuration();
                            if (totalDuration > 0) {
                                float seekPercent = deltaX / (float) width;
                                long maxSeekSpan = Math.min(180000, Math.max(60000, totalDuration / 3));
                                int seekDeltaMs = (int) (seekPercent * maxSeekSpan);
                                targetSeekPositionMs = Math.max(0, Math.min(totalDuration, startSeekPositionMs + seekDeltaMs));
                                showSeekHud(seekDeltaMs, targetSeekPositionMs, totalDuration);
                            }
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSwipeHorizontal) {
                        if (vvPlayer != null && vvPlayer.getDuration() > 0) {
                            vvPlayer.seekTo(targetSeekPositionMs);
                        }
                        playerHandler.postDelayed(() -> {
                            if (layoutHudSeek != null) layoutHudSeek.setVisibility(View.GONE);
                        }, 500);
                    }
                    if (isSwipeVertical) {
                        playerHandler.postDelayed(() -> {
                            if (layoutHudBrightness != null) layoutHudBrightness.setVisibility(View.GONE);
                            if (layoutHudVolume != null) layoutHudVolume.setVisibility(View.GONE);
                        }, 800);
                    }
                    if (layoutPlayerControls.getVisibility() == View.VISIBLE) {
                        scheduleHideControls();
                    }
                    isSwipeHorizontal = false;
                    isSwipeVertical = false;
                    break;
            }
            return true;
        });
    }

    private void showVolumeHud(int current, int max) {
        if (layoutHudVolume == null) return;
        layoutHudVolume.setVisibility(View.VISIBLE);
        if (layoutHudBrightness != null) layoutHudBrightness.setVisibility(View.GONE);
        if (layoutHudSeek != null) layoutHudSeek.setVisibility(View.GONE);

        int percent = (max > 0) ? (int) (((float) current / (float) max) * 100) : 0;
        pbHudVolume.setProgress(percent);
        tvHudVolumeText.setText("Volume: " + current + " / " + max);

        if (current == 0) {
            tvHudVolumeIcon.setText("🔇");
        } else if (percent < 50) {
            tvHudVolumeIcon.setText("🔉");
        } else {
            tvHudVolumeIcon.setText("🔊");
        }
    }

    private void showBrightnessHud(int percent) {
        if (layoutHudBrightness == null) return;
        layoutHudBrightness.setVisibility(View.VISIBLE);
        if (layoutHudVolume != null) layoutHudVolume.setVisibility(View.GONE);
        if (layoutHudSeek != null) layoutHudSeek.setVisibility(View.GONE);

        pbHudBrightness.setProgress(percent);
        tvHudBrightnessText.setText("Brightness: " + percent + "%");
        if (percent < 30) {
            tvHudBrightnessIcon.setText("🌘");
        } else if (percent < 70) {
            tvHudBrightnessIcon.setText("🌗");
        } else {
            tvHudBrightnessIcon.setText("☀️");
        }
    }

    private void showSeekHud(int deltaMs, int targetMs, int totalMs) {
        if (layoutHudSeek == null) return;
        layoutHudSeek.setVisibility(View.VISIBLE);
        if (layoutHudVolume != null) layoutHudVolume.setVisibility(View.GONE);
        if (layoutHudBrightness != null) layoutHudBrightness.setVisibility(View.GONE);

        String sign = deltaMs >= 0 ? "+" : "-";
        int absSec = Math.abs(deltaMs) / 1000;
        int min = absSec / 60;
        int sec = absSec % 60;
        String deltaFormatted = String.format(Locale.getDefault(), "%s%02d:%02d", sign, min, sec);
        tvHudSeekDiff.setText((deltaMs >= 0 ? "⏩ " : "⏪ ") + deltaFormatted);
        tvHudSeekTarget.setText(formatTime(targetMs) + " / " + formatTime(totalMs));
    }

    private void setScreenBrightness(float brightness) {
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = brightness;
            getWindow().setAttributes(lp);
        } catch (Exception ignored) {}
    }

    private float getWindowBrightness() {
        try {
            float b = getWindow().getAttributes().screenBrightness;
            if (b > 0) return b;
            int sysB = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            return sysB / 255f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    private void togglePlayPause() {
        if (vvPlayer == null) return;
        if (vvPlayer.isPlaying()) {
            vvPlayer.pause();
            btnVideoPlayPause.setText("▶");
            playerHandler.removeCallbacks(hideControlsRunnable);
        } else {
            vvPlayer.start();
            btnVideoPlayPause.setText("⏸");
            scheduleHideControls();
        }
    }

    private void rewind10s() {
        if (vvPlayer == null) return;
        int cur = vvPlayer.getCurrentPosition();
        int target = Math.max(0, cur - 10000);
        vvPlayer.seekTo(target);
        updateProgressUI(target);
        showDoubleTapHud(false);
        scheduleHideControls();
    }

    private void forward10s() {
        if (vvPlayer == null) return;
        int cur = vvPlayer.getCurrentPosition();
        int total = getCurrentTotalDuration();
        int target = (total > 0) ? Math.min(total, cur + 10000) : (cur + 10000);
        vvPlayer.seekTo(target);
        updateProgressUI(target);
        showDoubleTapHud(true);
        scheduleHideControls();
    }

    private void toggleScreenLock() {
        isScreenLocked = !isScreenLocked;
        if (isScreenLocked) {
            layoutPlayerControls.setVisibility(View.GONE);
            btnPlayerUnlock.setVisibility(View.VISIBLE);
            btnPlayerUnlock.setAlpha(1.0f);
            playerHandler.removeCallbacks(hideUnlockButtonRunnable);
            playerHandler.postDelayed(hideUnlockButtonRunnable, 3000);
            Toast.makeText(this, "Screen Locked 🔒 (Tap unlock badge to restore)", Toast.LENGTH_SHORT).show();
        } else {
            btnPlayerUnlock.setVisibility(View.GONE);
            layoutPlayerControls.setVisibility(View.VISIBLE);
            layoutPlayerControls.setAlpha(1.0f);
            scheduleHideControls();
            Toast.makeText(this, "Screen Unlocked 🔓", Toast.LENGTH_SHORT).show();
        }
    }

    private void cycleAspectRatio() {
        currentAspectMode = (currentAspectMode + 1) % 3;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) vvPlayer.getLayoutParams();
        if (currentAspectMode == 0) {
            btnPlayerAspect.setText("📐 Fit");
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            vvPlayer.setScaleX(1.0f);
            vvPlayer.setScaleY(1.0f);
        } else if (currentAspectMode == 1) {
            btnPlayerAspect.setText("📐 Fill");
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.CENTER;
            vvPlayer.setScaleX(1.0f);
            vvPlayer.setScaleY(1.0f);
        } else {
            btnPlayerAspect.setText("📐 Zoom");
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.CENTER;
            vvPlayer.setScaleX(1.25f);
            vvPlayer.setScaleY(1.25f);
        }
        vvPlayer.setLayoutParams(lp);
        scheduleHideControls();
    }

    private void cyclePlaybackSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % PLAYBACK_SPEEDS.length;
        float speed = PLAYBACK_SPEEDS[currentSpeedIndex];
        btnPlayerSpeed.setText(PLAYBACK_SPEED_LABELS[currentSpeedIndex]);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && underlyingMediaPlayer != null) {
            try {
                PlaybackParams params = underlyingMediaPlayer.getPlaybackParams();
                params.setSpeed(speed);
                underlyingMediaPlayer.setPlaybackParams(params);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        scheduleHideControls();
    }

    private void toggleMute() {
        if (audioManager == null) return;
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (!isMuted) {
            volumeBeforeMute = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            isMuted = true;
            btnPlayerMute.setText("🔇");
            showVolumeHud(0, maxVol);
        } else {
            int target = volumeBeforeMute > 0 ? volumeBeforeMute : (maxVol / 2);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            isMuted = false;
            btnPlayerMute.setText("🔊");
            showVolumeHud(target, maxVol);
        }
        playerHandler.postDelayed(() -> {
            if (layoutHudVolume != null) layoutHudVolume.setVisibility(View.GONE);
        }, 800);
        scheduleHideControls();
    }

    private void toggleLoop() {
        isLooping = !isLooping;
        if (isLooping) {
            btnPlayerLoop.setText("🔁");
            btnPlayerLoop.setTextColor(Color.parseColor("#FF4D8D"));
            Toast.makeText(this, "Loop Mode: ON 🔁", Toast.LENGTH_SHORT).show();
        } else {
            btnPlayerLoop.setText("🔁");
            btnPlayerLoop.setTextColor(Color.WHITE);
            Toast.makeText(this, "Loop Mode: OFF", Toast.LENGTH_SHORT).show();
        }
        scheduleHideControls();
    }

    private void toggleOrientation() {
        int current = getRequestedOrientation();
        if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        scheduleHideControls();
    }

    private void playNextVideo() {
        List<VideoItem> list = !displayedVideos.isEmpty() ? displayedVideos : allVideos;
        if (list.isEmpty()) {
            Toast.makeText(this, "No videos available 🌸", Toast.LENGTH_SHORT).show();
            return;
        }

        int nextIndex = 0;
        if (currentPlayingIndex >= 0) {
            nextIndex = (currentPlayingIndex + 1) % list.size();
        } else if (currentVideoItem != null) {
            int found = findVideoIndex(currentVideoItem.path, list);
            if (found >= 0) {
                nextIndex = (found + 1) % list.size();
            }
        }
        playVideo(list.get(nextIndex));
    }

    private void playPrevVideo() {
        List<VideoItem> list = !displayedVideos.isEmpty() ? displayedVideos : allVideos;
        if (list.isEmpty()) {
            Toast.makeText(this, "No videos available 🌸", Toast.LENGTH_SHORT).show();
            return;
        }

        if (vvPlayer != null && vvPlayer.getCurrentPosition() > 3000) {
            vvPlayer.seekTo(0);
            updateProgressUI(0);
            return;
        }

        int prevIndex = list.size() - 1;
        if (currentPlayingIndex >= 0) {
            prevIndex = (currentPlayingIndex - 1 + list.size()) % list.size();
        } else if (currentVideoItem != null) {
            int found = findVideoIndex(currentVideoItem.path, list);
            if (found >= 0) {
                prevIndex = (found - 1 + list.size()) % list.size();
            }
        }
        playVideo(list.get(prevIndex));
    }

    private void setupSearch() {
        etSearchVideos.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterVideos(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    batteryPercent = (int) (((float) level / (float) scale) * 100);
                }
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void scanAllVideos() {
        allVideos.clear();

        // 1. Query Android MediaStore
        try {
            ContentResolver cr = getContentResolver();
            Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            String[] proj = {
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATE_ADDED
            };

            Cursor cursor = cr.query(uri, proj, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC");
            if (cursor != null) {
                int titleIdx = cursor.getColumnIndex(MediaStore.Video.Media.TITLE);
                int dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                int durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                int sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE);
                int dateIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED);

                while (cursor.moveToNext()) {
                    String path = dataIdx != -1 ? cursor.getString(dataIdx) : null;
                    if (path != null && isSupportedVideoFormat(path)) {
                        String title = titleIdx != -1 ? cursor.getString(titleIdx) : null;
                        if (title == null || title.trim().isEmpty()) {
                            title = new File(path).getName();
                        }
                        long duration = durIdx != -1 ? cursor.getLong(durIdx) : 0;
                        long size = sizeIdx != -1 ? cursor.getLong(sizeIdx) : new File(path).length();
                        long date = dateIdx != -1 ? cursor.getLong(dateIdx) : System.currentTimeMillis();
                        allVideos.add(new VideoItem(title, path, duration, size, date));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Direct folder scan fallback
        File[] scanPaths = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                new File(Environment.getExternalStorageDirectory(), "Movies"),
                new File(Environment.getExternalStorageDirectory(), "Download"),
                new File("/storage")
        };

        for (File p : scanPaths) {
            scanDirectory(p, 0);
        }

        filterVideos(etSearchVideos.getText().toString());
    }

    private void scanDirectory(File dir, int depth) {
        if (dir == null || !dir.exists() || !dir.canRead() || depth > 3) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                scanDirectory(f, depth + 1);
            } else if (f.isFile() && isSupportedVideoFormat(f.getName())) {
                boolean exists = false;
                for (VideoItem v : allVideos) {
                    if (v.path.equals(f.getAbsolutePath())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    allVideos.add(new VideoItem(f.getName(), f.getAbsolutePath(), 0, f.length(), f.lastModified()));
                }
            }
        }
    }

    private boolean isSupportedVideoFormat(String path) {
        String lower = path.toLowerCase(Locale.US);
        return lower.endsWith(".mp4")
                || lower.endsWith(".mkv")
                || lower.endsWith(".webm")
                || lower.endsWith(".3gp")
                || lower.endsWith(".avi")
                || lower.endsWith(".mov");
    }

    private void filterVideos(String query) {
        displayedVideos.clear();
        String q = (query == null) ? "" : query.trim().toLowerCase();

        if (q.isEmpty()) {
            displayedVideos.addAll(allVideos);
        } else {
            for (VideoItem item : allVideos) {
                if (item.title.toLowerCase().contains(q)) {
                    displayedVideos.add(item);
                }
            }
        }

        tvVideoCount.setText(allVideos.size() + " Videos");

        if (allVideos.isEmpty()) {
            layoutEmptyVideos.setVisibility(View.VISIBLE);
            rvVideosGrid.setVisibility(View.GONE);
        } else {
            layoutEmptyVideos.setVisibility(View.GONE);
            rvVideosGrid.setVisibility(View.VISIBLE);
        }

        if (videoAdapter != null) {
            videoAdapter.notifyDataSetChanged();
        }
    }

    private void playVideo(VideoItem item) {
        MelodyMusicManager.getInstance().pause();

        currentVideoItem = item;
        List<VideoItem> currentList = !displayedVideos.isEmpty() ? displayedVideos : allVideos;
        currentPlayingIndex = findVideoIndex(item.path, currentList);

        layoutGalleryView.setVisibility(View.GONE);
        layoutPlayerContainer.setVisibility(View.VISIBLE);
        layoutPlayerControls.setVisibility(View.VISIBLE);
        layoutPlayerControls.setAlpha(1.0f);
        btnPlayerUnlock.setVisibility(View.GONE);
        isScreenLocked = false;

        tvPlayerVideoTitle.setText(item.title);
        btnVideoPlayPause.setText("⏸");
        sbPlayerProgress.setProgress(0);
        tvPlayerCurrentTime.setText("00:00");
        int initialTotal = item.durationMs > 0 ? (int) item.durationMs : 0;
        tvPlayerTotalTime.setText(formatTime(initialTotal));

        playerHandler.removeCallbacks(progressUpdateRunnable);

        vvPlayer.setVideoPath(item.path);
        vvPlayer.setOnPreparedListener(mp -> {
            underlyingMediaPlayer = mp;
            mp.setLooping(false);

            // Apply playback speed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    PlaybackParams params = mp.getPlaybackParams();
                    params.setSpeed(PLAYBACK_SPEEDS[currentSpeedIndex]);
                    mp.setPlaybackParams(params);
                } catch (Exception ignored) {}
            }

            vvPlayer.start();
            int total = getCurrentTotalDuration();
            tvPlayerTotalTime.setText(formatTime(total));
            scheduleHideControls();
            playerHandler.removeCallbacks(progressUpdateRunnable);
            playerHandler.post(progressUpdateRunnable);
        });

        vvPlayer.setOnCompletionListener(mp -> {
            if (isLooping) {
                vvPlayer.start();
            } else {
                playNextVideo();
            }
        });
    }

    private void closePlayer() {
        if (vvPlayer != null) {
            try {
                if (vvPlayer.isPlaying()) vvPlayer.stopPlayback();
            } catch (Exception ignored) {}
        }
        playerHandler.removeCallbacks(progressUpdateRunnable);
        playerHandler.removeCallbacks(hideControlsRunnable);
        playerHandler.removeCallbacks(hideUnlockButtonRunnable);

        isScreenLocked = false;
        layoutPlayerContainer.setVisibility(View.GONE);
        layoutGalleryView.setVisibility(View.VISIBLE);
    }

    private void toggleControls() {
        if (layoutPlayerControls.getVisibility() == View.VISIBLE) {
            layoutPlayerControls.setVisibility(View.GONE);
            playerHandler.removeCallbacks(hideControlsRunnable);
        } else {
            layoutPlayerControls.setVisibility(View.VISIBLE);
            layoutPlayerControls.setAlpha(1.0f);
            scheduleHideControls();
        }
    }

    private void scheduleHideControls() {
        playerHandler.removeCallbacks(hideControlsRunnable);
        playerHandler.postDelayed(hideControlsRunnable, 3500);
    }

    private String formatTime(int ms) {
        if (ms <= 0) return "00:00";
        int totalSec = ms / 1000;
        long hours = totalSec / 3600;
        long min = (totalSec % 3600) / 60;
        long sec = totalSec % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, min, sec);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }

    @Override
    public void onBackPressed() {
        if (layoutPlayerContainer.getVisibility() == View.VISIBLE) {
            if (isScreenLocked) {
                Toast.makeText(this, "Screen is Locked! Tap unlock badge first 🔒", Toast.LENGTH_SHORT).show();
                return;
            }
            closePlayer();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (vvPlayer != null && vvPlayer.isPlaying()) {
            vvPlayer.pause();
            btnVideoPlayPause.setText("▶");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (batteryReceiver != null) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
        }
        playerHandler.removeCallbacks(clockBatteryRunnable);
        closePlayer();
    }

    /**
     * RecyclerView Adapter for Video Grid
     */
    private class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

        @NonNull
        @Override
        public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_card, parent, false);
            return new VideoViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
            VideoItem item = displayedVideos.get(position);
            holder.tvTitle.setText(item.title);
            holder.tvDuration.setText(item.getFormattedDuration());
            holder.tvSize.setText(item.getFormattedSize());

            new Thread(() -> {
                try {
                    Bitmap thumb = ThumbnailUtils.createVideoThumbnail(item.path, MediaStore.Images.Thumbnails.MINI_KIND);
                    if (thumb != null) {
                        holder.ivThumb.post(() -> holder.ivThumb.setImageBitmap(thumb));
                    }
                } catch (Exception ignored) {}
            }).start();

            holder.itemView.setOnClickListener(v -> playVideo(item));
        }

        @Override
        public int getItemCount() {
            return displayedVideos.size();
        }

        class VideoViewHolder extends RecyclerView.ViewHolder {
            ImageView ivThumb;
            TextView tvDuration;
            TextView tvTitle;
            TextView tvSize;

            VideoViewHolder(@NonNull View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.iv_video_thumb);
                tvDuration = itemView.findViewById(R.id.tv_video_duration_badge);
                tvTitle = itemView.findViewById(R.id.tv_video_title);
                tvSize = itemView.findViewById(R.id.tv_video_size);
            }
        }
    }
}
