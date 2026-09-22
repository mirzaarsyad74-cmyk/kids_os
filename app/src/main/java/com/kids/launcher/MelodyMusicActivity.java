package com.kids.launcher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Tablet Music Player for Kids.
 * Complete feature set:
 * - Real tablet storage audio playback (.mp3, .m4a, .aac, .wav, .ogg, .flac).
 * - Draggable Seek/Progress Bar with live time display (00:00 / 03:45).
 * - Shuffle (Random) playback mode with toggle feedback.
 * - Repeat modes: Repeat All (🔁), Repeat One (🔂), Repeat Off (➡).
 * - Full Playlist Queue with tap-to-play and active playing indicator.
 * - Live search/filter by song title or artist name.
 * - Background playback across all launcher apps & games.
 * - Apple ID Assistant (AssistiveTouch) remote controls.
 * - Fixed auto-play: Never auto-plays on entry, waits for user tap.
 */
public class MelodyMusicActivity extends AppCompatActivity {

    public static class LocalTrack {
        public final String title;
        public final String artist;
        public final String path;
        public final long durationMs;

        public LocalTrack(String title, String artist, String path, long durationMs) {
            this.title = title;
            this.artist = artist;
            this.path = path;
            this.durationMs = durationMs;
        }

        public String getFormattedDuration() {
            if (durationMs <= 0) return "--:--";
            long totalSec = durationMs / 1000;
            long min = totalSec / 60;
            long sec = totalSec % 60;
            return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
        }
    }

    private MelodyMusicManager musicManager;

    private View viewVinylDisk;
    private ObjectAnimator vinylAnimator;

    private View[] visualizerBars;
    private TextView tvSongTitle;
    private TextView tvSongSubtitle;
    private TextView tvPlayingStatus;

    // Progress
    private SeekBar sbMusicProgress;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private boolean isUserSeeking = false;

    // Transport & Modes
    private TextView btnPlayPause;
    private TextView btnPrev;
    private TextView btnNext;
    private TextView btnBack;
    private TextView btnRescan;
    private TextView btnShuffle;
    private TextView btnRepeat;

    // Playlist
    private TextView tvPlaylistCount;
    private EditText etSearchMusic;
    private View layoutEmptyMusic;
    private RecyclerView rvMusicPlaylist;
    private PlaylistAdapter playlistAdapter;

    private final List<LocalTrack> allAudioFiles = new ArrayList<>();
    private final List<LocalTrack> displayedTracks = new ArrayList<>();

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private int visualizerStep = 0;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (musicManager != null) {
                if (musicManager.isPlaying() && !isUserSeeking) {
                    int curPos = musicManager.getCurrentPosition();
                    int duration = musicManager.getDuration();

                    if (duration > 0) {
                        int progress = (int) (((long) curPos * 1000) / duration);
                        sbMusicProgress.setProgress(progress);
                    } else {
                        sbMusicProgress.setProgress(0);
                    }

                    tvCurrentTime.setText(formatTime(curPos));
                    tvTotalTime.setText(formatTime(duration));

                    // Animate visualizer bars
                    visualizerStep++;
                    if (visualizerBars != null) {
                        float density = getResources().getDisplayMetrics().density;
                        for (int i = 0; i < visualizerBars.length; i++) {
                            int height = (int) (5 + (Math.sin(visualizerStep * 0.35 + i * 0.65) + 1.0) * 7.5);
                            visualizerBars[i].getLayoutParams().height = (int) (height * density);
                            visualizerBars[i].requestLayout();
                        }
                    }
                }
                uiHandler.postDelayed(this, 350);
            }
        }
    };

    private final MelodyMusicManager.MusicStateListener musicStateListener = new MelodyMusicManager.MusicStateListener() {
        @Override
        public void onTrackChanged(String title, String subtitle, boolean isPlaying, int durationMs) {
            runOnUiThread(() -> {
                updatePlaybackUI();
                if (playlistAdapter != null) {
                    playlistAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onPlayStateChanged(boolean isPlaying) {
            runOnUiThread(() -> updatePlaybackUI());
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            runOnUiThread(() -> updateRepeatButtonUI(repeatMode));
        }

        @Override
        public void onShuffleModeChanged(boolean isShuffle) {
            runOnUiThread(() -> updateShuffleButtonUI(isShuffle));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setWindowUiFlags();
        setContentView(R.layout.activity_melody_music);

        musicManager = MelodyMusicManager.getInstance();

        initViews();
        setupVisualizer();
        setupSeekBar();
        setupSearch();
        scanAllDeviceAudio();

        musicManager.setAudioFiles(allAudioFiles);
        filterPlaylist("");

        // DO NOT auto-play on entry; reflect paused state
        updatePlaybackUI();
        updateShuffleButtonUI(musicManager.isShuffle());
        updateRepeatButtonUI(musicManager.getRepeatMode());
    }

    private void setWindowUiFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void initViews() {
        viewVinylDisk = findViewById(R.id.view_vinyl_disk);
        tvSongTitle = findViewById(R.id.tv_song_title);
        tvSongSubtitle = findViewById(R.id.tv_song_subtitle);
        tvPlayingStatus = findViewById(R.id.tv_playing_status);

        sbMusicProgress = findViewById(R.id.sb_music_progress);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);

        btnPlayPause = findViewById(R.id.btn_music_play_pause);
        btnPrev = findViewById(R.id.btn_music_prev);
        btnNext = findViewById(R.id.btn_music_next);
        btnBack = findViewById(R.id.btn_music_back);
        btnRescan = findViewById(R.id.btn_music_rescan);
        btnShuffle = findViewById(R.id.btn_music_shuffle);
        btnRepeat = findViewById(R.id.btn_music_repeat);

        tvPlaylistCount = findViewById(R.id.tv_playlist_count);
        etSearchMusic = findViewById(R.id.et_search_music);
        layoutEmptyMusic = findViewById(R.id.layout_empty_music);
        rvMusicPlaylist = findViewById(R.id.rv_music_playlist);

        btnBack.setOnClickListener(v -> finish());
        btnPlayPause.setOnClickListener(v -> musicManager.togglePlayPause());
        btnPrev.setOnClickListener(v -> musicManager.prev());
        btnNext.setOnClickListener(v -> musicManager.next());

        btnShuffle.setOnClickListener(v -> {
            musicManager.toggleShuffle();
            Toast.makeText(this, musicManager.isShuffle() ? "🔀 Shuffle: ON" : "🔀 Shuffle: OFF", Toast.LENGTH_SHORT).show();
        });

        btnRepeat.setOnClickListener(v -> {
            musicManager.cycleRepeatMode();
            int mode = musicManager.getRepeatMode();
            String msg = (mode == MelodyMusicManager.REPEAT_ALL) ? "🔁 Repeat: ALL" :
                    (mode == MelodyMusicManager.REPEAT_ONE) ? "🔂 Repeat: ONE" : "➡ Repeat: OFF";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        btnRescan.setOnClickListener(v -> refreshMusicList());
        View btnEmptyRescan = findViewById(R.id.btn_empty_rescan);
        if (btnEmptyRescan != null) {
            btnEmptyRescan.setOnClickListener(v -> refreshMusicList());
        }

        // Setup RecyclerView
        rvMusicPlaylist.setLayoutManager(new LinearLayoutManager(this));
        playlistAdapter = new PlaylistAdapter();
        rvMusicPlaylist.setAdapter(playlistAdapter);

        // Setup Vinyl Rotation Animator
        vinylAnimator = ObjectAnimator.ofFloat(viewVinylDisk, "rotation", 0f, 360f);
        vinylAnimator.setDuration(4000);
        vinylAnimator.setRepeatCount(ValueAnimator.INFINITE);
        vinylAnimator.setInterpolator(new LinearInterpolator());
    }

    private void refreshMusicList() {
        scanAllDeviceAudio();
        musicManager.setAudioFiles(allAudioFiles);
        filterPlaylist(etSearchMusic.getText().toString());
        updatePlaybackUI();
        Toast.makeText(this, "Scan complete: " + allAudioFiles.size() + " songs found 🌸", Toast.LENGTH_SHORT).show();
    }

    private void setupSeekBar() {
        sbMusicProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && musicManager != null) {
                    int duration = musicManager.getDuration();
                    int targetMs = (int) (((long) progress * duration) / 1000);
                    tvCurrentTime.setText(formatTime(targetMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (musicManager != null) {
                    int duration = musicManager.getDuration();
                    int targetMs = (int) (((long) seekBar.getProgress() * duration) / 1000);
                    musicManager.seekTo(targetMs);
                }
            }
        });
    }

    private void setupSearch() {
        etSearchMusic.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterPlaylist(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterPlaylist(String query) {
        displayedTracks.clear();
        String q = (query == null) ? "" : query.trim().toLowerCase();

        if (q.isEmpty()) {
            displayedTracks.addAll(allAudioFiles);
        } else {
            for (LocalTrack track : allAudioFiles) {
                if (track.title.toLowerCase().contains(q) || track.artist.toLowerCase().contains(q)) {
                    displayedTracks.add(track);
                }
            }
        }

        tvPlaylistCount.setText(allAudioFiles.size() + " Songs");

        if (allAudioFiles.isEmpty()) {
            layoutEmptyMusic.setVisibility(View.VISIBLE);
            rvMusicPlaylist.setVisibility(View.GONE);
        } else {
            layoutEmptyMusic.setVisibility(View.GONE);
            rvMusicPlaylist.setVisibility(View.VISIBLE);
        }

        if (playlistAdapter != null) {
            playlistAdapter.notifyDataSetChanged();
        }
    }

    private void setupVisualizer() {
        visualizerBars = new View[]{
                findViewById(R.id.v_bar_1),
                findViewById(R.id.v_bar_2),
                findViewById(R.id.v_bar_3),
                findViewById(R.id.v_bar_4),
                findViewById(R.id.v_bar_5),
                findViewById(R.id.v_bar_6),
                findViewById(R.id.v_bar_7),
                findViewById(R.id.v_bar_8),
                findViewById(R.id.v_bar_9),
                findViewById(R.id.v_bar_10)
        };
    }

    private void updatePlaybackUI() {
        if (musicManager == null) return;

        tvSongTitle.setText(musicManager.getCurrentTitle());
        tvSongSubtitle.setText(musicManager.getCurrentSubtitle());

        boolean playing = musicManager.isPlaying();
        int curPos = musicManager.getCurrentPosition();
        int duration = musicManager.getDuration();

        tvCurrentTime.setText(formatTime(curPos));
        tvTotalTime.setText(formatTime(duration));

        if (duration > 0 && !isUserSeeking) {
            int progress = (int) (((long) curPos * 1000) / duration);
            sbMusicProgress.setProgress(progress);
        }

        if (playing) {
            btnPlayPause.setText("⏸");
            tvPlayingStatus.setText("✨ Playing Tablet Song ✨");
            if (vinylAnimator != null) {
                if (vinylAnimator.isPaused()) {
                    vinylAnimator.resume();
                } else if (!vinylAnimator.isStarted()) {
                    vinylAnimator.start();
                }
            }
        } else {
            btnPlayPause.setText("▶");
            tvPlayingStatus.setText(allAudioFiles.isEmpty() ? "🌸 No Music Loaded 🎵" : "🌸 Ready to Play 🎵");
            if (vinylAnimator != null && vinylAnimator.isStarted()) {
                vinylAnimator.pause();
            }
        }
    }

    private void updateShuffleButtonUI(boolean isShuffle) {
        if (isShuffle) {
            btnShuffle.setBackgroundResource(R.drawable.bg_melody_chip_selected);
        } else {
            btnShuffle.setBackgroundResource(R.drawable.bg_melody_pill);
        }
    }

    private void updateRepeatButtonUI(int mode) {
        if (mode == MelodyMusicManager.REPEAT_ALL) {
            btnRepeat.setText("🔁");
            btnRepeat.setBackgroundResource(R.drawable.bg_melody_chip_selected);
        } else if (mode == MelodyMusicManager.REPEAT_ONE) {
            btnRepeat.setText("🔂");
            btnRepeat.setBackgroundResource(R.drawable.bg_melody_chip_selected);
        } else {
            btnRepeat.setText("➡");
            btnRepeat.setBackgroundResource(R.drawable.bg_melody_pill);
        }
    }

    private String formatTime(int ms) {
        if (ms <= 0) return "00:00";
        int totalSec = ms / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }

    /**
     * Scans MediaStore and tablet filesystem for music tracks.
     */
    private void scanAllDeviceAudio() {
        allAudioFiles.clear();

        // 1. Query Android MediaStore
        try {
            ContentResolver cr = getContentResolver();
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] proj = {
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION
            };

            Cursor cursor = cr.query(uri, proj, null, null, MediaStore.Audio.Media.TITLE + " ASC");
            if (cursor != null) {
                int titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int durIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);

                while (cursor.moveToNext()) {
                    String path = dataIdx != -1 ? cursor.getString(dataIdx) : null;
                    if (path != null && isSupportedAudioFormat(path)) {
                        String title = titleIdx != -1 ? cursor.getString(titleIdx) : null;
                        if (title == null || title.trim().isEmpty()) {
                            title = new File(path).getName();
                        }
                        String artist = artistIdx != -1 ? cursor.getString(artistIdx) : null;
                        if (artist == null || "<unknown>".equalsIgnoreCase(artist)) {
                            artist = "Tablet Audio";
                        }
                        long duration = durIdx != -1 ? cursor.getLong(durIdx) : 0;
                        allAudioFiles.add(new LocalTrack(title, artist, path, duration));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Direct folder scan fallback across common music paths
        File[] scanPaths = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                new File(Environment.getExternalStorageDirectory(), "Music"),
                new File(Environment.getExternalStorageDirectory(), "Download"),
                new File("/storage")
        };

        for (File p : scanPaths) {
            scanDirectory(p, 0);
        }
    }

    private void scanDirectory(File dir, int depth) {
        if (dir == null || !dir.exists() || !dir.canRead() || depth > 3) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                scanDirectory(f, depth + 1);
            } else if (f.isFile() && isSupportedAudioFormat(f.getName())) {
                boolean exists = false;
                for (LocalTrack t : allAudioFiles) {
                    if (t.path.equals(f.getAbsolutePath())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    allAudioFiles.add(new LocalTrack(f.getName(), "Storage Audio", f.getAbsolutePath(), 0));
                }
            }
        }
    }

    private boolean isSupportedAudioFormat(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".mp3")
                || lower.endsWith(".m4a")
                || lower.endsWith(".aac")
                || lower.endsWith(".wav")
                || lower.endsWith(".ogg")
                || lower.endsWith(".flac");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (musicManager != null) {
            musicManager.addListener(musicStateListener);
            updatePlaybackUI();
            updateShuffleButtonUI(musicManager.isShuffle());
            updateRepeatButtonUI(musicManager.getRepeatMode());
        }
        uiHandler.removeCallbacks(progressRunnable);
        uiHandler.post(progressRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (musicManager != null) {
            musicManager.removeListener(musicStateListener);
        }
        uiHandler.removeCallbacks(progressRunnable);
        if (vinylAnimator != null) {
            vinylAnimator.pause();
        }
        // Background playback continues smoothly!
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacks(progressRunnable);
        if (vinylAnimator != null) {
            vinylAnimator.cancel();
        }
    }

    /**
     * RecyclerView Adapter for Tablet Music Playlist
     */
    private class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.TrackViewHolder> {

        @NonNull
        @Override
        public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music_track, parent, false);
            return new TrackViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
            LocalTrack track = displayedTracks.get(position);
            holder.tvNumber.setText((position + 1) + ".");
            holder.tvTitle.setText(track.title);
            holder.tvArtist.setText(track.artist);
            holder.tvDuration.setText(track.getFormattedDuration());

            int originalIndex = allAudioFiles.indexOf(track);
            boolean isCurrent = (musicManager != null && musicManager.getCurrentIndex() == originalIndex && !allAudioFiles.isEmpty());
            boolean isPlaying = (isCurrent && musicManager != null && musicManager.isPlaying());

            if (isCurrent) {
                holder.root.setBackgroundResource(R.drawable.bg_melody_card);
                holder.tvNumber.setTextColor(Color.parseColor("#DB2777"));
                holder.tvTitle.setTextColor(Color.parseColor("#DB2777"));
                holder.tvDuration.setTextColor(Color.parseColor("#DB2777"));
                holder.tvStatusIcon.setVisibility(View.VISIBLE);
                holder.tvStatusIcon.setText(isPlaying ? "🎵" : "⏸");
            } else {
                holder.root.setBackgroundResource(R.drawable.bg_melody_pill);
                holder.tvNumber.setTextColor(Color.parseColor("#831843"));
                holder.tvTitle.setTextColor(Color.parseColor("#831843"));
                holder.tvDuration.setTextColor(Color.parseColor("#9D174D"));
                holder.tvStatusIcon.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (musicManager != null && originalIndex != -1) {
                    musicManager.playTrack(originalIndex);
                }
            });
        }

        @Override
        public int getItemCount() {
            return displayedTracks.size();
        }

        class TrackViewHolder extends RecyclerView.ViewHolder {
            View root;
            TextView tvNumber;
            TextView tvTitle;
            TextView tvArtist;
            TextView tvDuration;
            TextView tvStatusIcon;

            TrackViewHolder(@NonNull View itemView) {
                super(itemView);
                root = itemView.findViewById(R.id.item_track_root);
                tvNumber = itemView.findViewById(R.id.tv_track_number);
                tvTitle = itemView.findViewById(R.id.tv_track_title);
                tvArtist = itemView.findViewById(R.id.tv_track_artist);
                tvDuration = itemView.findViewById(R.id.tv_track_duration);
                tvStatusIcon = itemView.findViewById(R.id.tv_track_status_icon);
            }
        }
    }
}
