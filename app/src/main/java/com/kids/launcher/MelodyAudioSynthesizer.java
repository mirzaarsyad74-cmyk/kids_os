package com.kids.launcher;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Procedural Audio Synthesizer for Melody Music Player.
 * Generates sweet music box, chime, and lofi melodies completely offline.
 */
public class MelodyAudioSynthesizer {

    private static final int SAMPLE_RATE = 22050;
    private AudioTrack audioTrack;
    private Thread playThread;
    private volatile boolean isPlaying = false;
    private int currentSongIndex = 0;

    public interface PlaybackListener {
        void onBeat(int step, float amplitude);
    }

    private PlaybackListener listener;

    public void setPlaybackListener(PlaybackListener listener) {
        this.listener = listener;
    }

    // Cute Pentatonic & Major Frequencies for sweet melodies
    // C4, D4, E4, G4, A4, C5, D5, E5, G5, A5, C6
    private static final double[] PENTATONIC = {
            261.63, 293.66, 329.63, 392.00, 440.00,
            523.25, 587.33, 659.25, 783.99, 880.00, 1046.50
    };

    // 5 Sweet Song Patterns (note indices in pentatonic scale)
    private static final int[][] SONGS = {
            // Song 0: 🌸 Melody Dreamland
            {5, 7, 8, 7, 5, 4, 5, 2, 4, 5, 7, 9, 8, 7, 5, 4, 7, 5, 4, 2, 4, 5, 7, 8, 9, 8, 7, 5, 4, 2, 0, 4},
            // Song 1: 🍓 Strawberry Bubble Pop
            {4, 4, 7, 7, 9, 9, 7, 0, 5, 5, 4, 4, 2, 2, 0, 0, 4, 7, 9, 7, 5, 4, 2, 0, 7, 7, 9, 8, 7, 5, 4, 5},
            // Song 2: 🌈 Rainbow Twinkle Stars
            {0, 0, 4, 4, 5, 5, 4, 0, 3, 3, 2, 2, 1, 1, 0, 0, 4, 4, 3, 3, 2, 2, 1, 0, 4, 4, 5, 5, 7, 8, 9, 5},
            // Song 3: 🧸 Pastel Cozy Cafe
            {2, 4, 5, 7, 5, 4, 2, 0, 2, 4, 7, 8, 7, 5, 4, 2, 5, 7, 8, 9, 8, 7, 5, 4, 2, 0, 2, 4, 5, 4, 2, 0},
            // Song 4: 🌙 Moonlight Lullaby
            {7, 5, 4, 2, 4, 5, 7, 0, 8, 7, 5, 4, 2, 0, 2, 4, 7, 8, 9, 7, 5, 4, 2, 0, 5, 4, 2, 0, 2, 4, 5, 7}
    };

    public void startSong(int songIndex) {
        stop();
        this.currentSongIndex = Math.max(0, Math.min(SONGS.length - 1, songIndex));
        this.isPlaying = true;

        playThread = new Thread(() -> {
            try {
                int minBufSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

                audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBufSize, 4096),
                        AudioTrack.MODE_STREAM
                );

                audioTrack.play();

                int[] pattern = SONGS[currentSongIndex];
                int noteDurationSamples = (int) (SAMPLE_RATE * 0.28); // ~280ms per note
                short[] buffer = new short[noteDurationSamples];

                int step = 0;
                while (isPlaying) {
                    int noteIdx = pattern[step % pattern.length];
                    double freq = PENTATONIC[noteIdx % PENTATONIC.length];

                    // Synthesize soft music box tone with gentle decay
                    for (int i = 0; i < noteDurationSamples; i++) {
                        double t = (double) i / SAMPLE_RATE;
                        double decay = Math.exp(-4.5 * t); // Smooth bell-like decay
                        // Fundamental + soft overtone + warm harmonic
                        double sample = Math.sin(2.0 * Math.PI * freq * t)
                                + 0.35 * Math.sin(4.0 * Math.PI * freq * t)
                                + 0.15 * Math.sin(6.0 * Math.PI * freq * t);
                        buffer[i] = (short) (sample * decay * 18000);
                    }

                    if (audioTrack != null && isPlaying) {
                        audioTrack.write(buffer, 0, buffer.length);
                    }

                    if (listener != null) {
                        final int currentStep = step;
                        listener.onBeat(currentStep, (float) (freq / 1000.0));
                    }

                    step++;
                }

            } catch (Exception e) {
                Log.e("MelodyAudio", "Playback error: " + e.getMessage());
            } finally {
                if (audioTrack != null) {
                    try {
                        audioTrack.stop();
                        audioTrack.release();
                    } catch (Exception ignored) {}
                    audioTrack = null;
                }
            }
        });

        playThread.start();
    }

    public void playStickerSound(int stickerType) {
        new Thread(() -> {
            try {
                int noteDuration = (int) (SAMPLE_RATE * 0.35);
                short[] buf = new short[noteDuration];
                double baseFreq = stickerType == 0 ? 880.0 : (stickerType == 1 ? 659.25 : (stickerType == 2 ? 1046.5 : 783.99));

                for (int i = 0; i < noteDuration; i++) {
                    double t = (double) i / SAMPLE_RATE;
                    double decay = Math.exp(-6.0 * t);
                    double sample = Math.sin(2.0 * Math.PI * (baseFreq + t * 400.0) * t) * decay;
                    buf[i] = (short) (sample * 24000);
                }

                AudioTrack track = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        buf.length * 2,
                        AudioTrack.MODE_STATIC
                );
                track.write(buf, 0, buf.length);
                track.play();
                Thread.sleep(400);
                track.release();
            } catch (Exception ignored) {}
        }).start();
    }

    public void stop() {
        isPlaying = false;
        if (playThread != null) {
            playThread.interrupt();
            playThread = null;
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
