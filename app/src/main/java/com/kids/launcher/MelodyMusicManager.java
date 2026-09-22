package com.kids.launcher;

import android.media.MediaPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Global Singleton Music Manager for Kids Launcher.
 * Features:
 * - Real tablet storage audio playback (.mp3, .m4a, .aac, .wav, .flac, .ogg).
 * - Background playback across all apps, launcher, and games.
 * - Shuffle (Random) playback mode.
 * - Repeat mode: REPEAT_ALL, REPEAT_ONE, REPEAT_OFF.
 * - Draggable Seek/Progress support (seekTo, getCurrentPosition, getDuration).
 * - Live synchronization with MelodyMusicActivity and Apple ID Assistant (AssistiveTouch).
 */
public class MelodyMusicManager {

    public static final int REPEAT_ALL = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_OFF = 2;

    private static MelodyMusicManager instance;

    public interface MusicStateListener {
        void onTrackChanged(String title, String subtitle, boolean isPlaying, int durationMs);
        void onPlayStateChanged(boolean isPlaying);
        void onRepeatModeChanged(int repeatMode);
        void onShuffleModeChanged(boolean isShuffle);
    }

    private final List<MusicStateListener> listeners = new ArrayList<>();

    private MediaPlayer mediaPlayer;
    private final List<MelodyMusicActivity.LocalTrack> playlist = new ArrayList<>();
    private int currentTrackIndex = 0;
    private boolean isPlaying = false;

    private boolean isShuffle = false;
    private int repeatMode = REPEAT_ALL;
    private final Random random = new Random();

    private MelodyMusicManager() {
    }

    public static synchronized MelodyMusicManager getInstance() {
        if (instance == null) {
            instance = new MelodyMusicManager();
        }
        return instance;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isShuffle() {
        return isShuffle;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public int getCurrentIndex() {
        return currentTrackIndex;
    }

    public List<MelodyMusicActivity.LocalTrack> getAudioFiles() {
        return playlist;
    }

    public void setAudioFiles(List<MelodyMusicActivity.LocalTrack> files) {
        playlist.clear();
        playlist.addAll(files);
        if (currentTrackIndex >= playlist.size()) {
            currentTrackIndex = 0;
        }
        notifyTrackChanged();
    }

    public String getCurrentTitle() {
        if (!playlist.isEmpty() && currentTrackIndex < playlist.size()) {
            return playlist.get(currentTrackIndex).title;
        }
        return "No Music Found";
    }

    public String getCurrentSubtitle() {
        if (!playlist.isEmpty() && currentTrackIndex < playlist.size()) {
            return playlist.get(currentTrackIndex).artist;
        }
        return "Add MP3 to tablet storage 🎵";
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception ignored) {}
        }
        if (!playlist.isEmpty() && currentTrackIndex < playlist.size()) {
            return (int) playlist.get(currentTrackIndex).durationMs;
        }
        return 0;
    }

    public void seekTo(int positionMs) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(positionMs);
            } catch (Exception ignored) {}
        }
    }

    public void toggleShuffle() {
        isShuffle = !isShuffle;
        for (MusicStateListener l : new ArrayList<>(listeners)) {
            l.onShuffleModeChanged(isShuffle);
        }
    }

    public void cycleRepeatMode() {
        if (repeatMode == REPEAT_ALL) {
            repeatMode = REPEAT_ONE;
        } else if (repeatMode == REPEAT_ONE) {
            repeatMode = REPEAT_OFF;
        } else {
            repeatMode = REPEAT_ALL;
        }
        for (MusicStateListener l : new ArrayList<>(listeners)) {
            l.onRepeatModeChanged(repeatMode);
        }
    }

    public void addListener(MusicStateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        listener.onTrackChanged(getCurrentTitle(), getCurrentSubtitle(), isPlaying, getDuration());
        listener.onShuffleModeChanged(isShuffle);
        listener.onRepeatModeChanged(repeatMode);
    }

    public void removeListener(MusicStateListener listener) {
        listeners.remove(listener);
    }

    private void notifyTrackChanged() {
        String title = getCurrentTitle();
        String sub = getCurrentSubtitle();
        int dur = getDuration();
        for (MusicStateListener l : new ArrayList<>(listeners)) {
            l.onTrackChanged(title, sub, isPlaying, dur);
        }
    }

    private void notifyPlayStateChanged() {
        for (MusicStateListener l : new ArrayList<>(listeners)) {
            l.onPlayStateChanged(isPlaying);
        }
    }

    public void playTrack(int index) {
        if (playlist.isEmpty()) {
            stop();
            return;
        }
        stopAllPlayback();
        currentTrackIndex = (index + playlist.size()) % playlist.size();

        MelodyMusicActivity.LocalTrack track = playlist.get(currentTrackIndex);
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(track.path);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            mediaPlayer.setOnCompletionListener(mp -> handleTrackCompletion());
            notifyTrackChanged();
        } catch (Exception e) {
            e.printStackTrace();
            isPlaying = false;
            notifyPlayStateChanged();
        }
    }

    private void handleTrackCompletion() {
        if (repeatMode == REPEAT_ONE) {
            // Replay the current track from beginning
            playTrack(currentTrackIndex);
            return;
        }

        if (isShuffle && playlist.size() > 1) {
            int nextIdx;
            do {
                nextIdx = random.nextInt(playlist.size());
            } while (nextIdx == currentTrackIndex);
            playTrack(nextIdx);
            return;
        }

        // Sequential
        if (currentTrackIndex + 1 < playlist.size()) {
            playTrack(currentTrackIndex + 1);
        } else {
            // Reached end of playlist
            if (repeatMode == REPEAT_ALL) {
                playTrack(0);
            } else {
                stop();
            }
        }
    }

    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }

    public void play() {
        if (playlist.isEmpty()) return;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.start();
                isPlaying = true;
                notifyPlayStateChanged();
                return;
            } catch (Exception ignored) {}
        }
        playTrack(currentTrackIndex);
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
            } catch (Exception ignored) {}
        }
        isPlaying = false;
        notifyPlayStateChanged();
    }

    public void stop() {
        stopAllPlayback();
        isPlaying = false;
        notifyPlayStateChanged();
    }

    public void next() {
        if (playlist.isEmpty()) return;
        if (isShuffle && playlist.size() > 1) {
            int nextIdx;
            do {
                nextIdx = random.nextInt(playlist.size());
            } while (nextIdx == currentTrackIndex);
            playTrack(nextIdx);
        } else {
            playTrack((currentTrackIndex + 1) % playlist.size());
        }
    }

    public void prev() {
        if (playlist.isEmpty()) return;
        // If current song has played for more than 3 seconds, restart it
        if (mediaPlayer != null && getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }

        if (isShuffle && playlist.size() > 1) {
            int prevIdx;
            do {
                prevIdx = random.nextInt(playlist.size());
            } while (prevIdx == currentTrackIndex);
            playTrack(prevIdx);
        } else {
            playTrack((currentTrackIndex - 1 + playlist.size()) % playlist.size());
        }
    }

    private void stopAllPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }
}
