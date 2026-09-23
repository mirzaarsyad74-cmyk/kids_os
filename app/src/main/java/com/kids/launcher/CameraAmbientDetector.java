package com.kids.launcher;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

/**
 * Intelligent Dual-Engine Ambient Light Detector for Kids OS.
 * Designed specifically for tablets where hardware light sensor events are unavailable or dormant.
 * 
 * 1. Immediate Circadian Estimation: Zero delay, instantly returns time-of-day ambient status
 *    so the UI never hangs on "Detecting...".
 * 2. Front Camera Ambient Luma Sampler: Accurately samples room luminance through the front camera
 *    using a lightweight one-shot preview callback, then immediately releases the camera.
 */
public class CameraAmbientDetector {

    private static final String TAG = "CameraAmbientDetector";
    private static CameraAmbientDetector instance;

    public interface AmbientLightListener {
        void onAmbientUpdate(float lux, String conditionDesc, float recommendedBrightness);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private AmbientLightListener activeListener;
    private boolean isRunning = false;
    private float lastKnownLux = -1;
    private String lastKnownDesc = "☀️ Daylight";
    private float lastKnownBrightness = 0.70f;

    private final Runnable periodicSampler = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            sampleCameraAmbient();
            if (backgroundHandler != null) {
                backgroundHandler.postDelayed(this, 15000); // Sample every 15s
            }
        }
    };

    public static synchronized CameraAmbientDetector getInstance(Context context) {
        if (instance == null) {
            instance = new CameraAmbientDetector(context.getApplicationContext());
        }
        return instance;
    }

    private CameraAmbientDetector(Context context) {
        this.appContext = context;
    }

    public synchronized void start(AmbientLightListener listener) {
        this.activeListener = listener;
        this.isRunning = true;

        // 1. Immediately provide circadian baseline so UI responds in 0ms
        evaluateCircadianBaseline();
        if (activeListener != null) {
            activeListener.onAmbientUpdate(lastKnownLux, lastKnownDesc, lastKnownBrightness);
        }

        // 2. Start background sampler thread
        if (backgroundThread == null) {
            backgroundThread = new HandlerThread("CameraAmbientSampler");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }

        backgroundHandler.removeCallbacks(periodicSampler);
        backgroundHandler.post(periodicSampler);
    }

    public synchronized void stop() {
        isRunning = false;
        activeListener = null;
        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacks(periodicSampler);
        }
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    public void sampleNow() {
        evaluateCircadianBaseline();
        if (activeListener != null) {
            activeListener.onAmbientUpdate(lastKnownLux, lastKnownDesc, lastKnownBrightness);
        }
        if (backgroundHandler != null) {
            backgroundHandler.post(this::sampleCameraAmbient);
        }
    }

    public float getLastKnownLux() {
        if (lastKnownLux <= 0) {
            evaluateCircadianBaseline();
        }
        return lastKnownLux;
    }

    public String getLastKnownDesc() {
        if (lastKnownDesc == null || lastKnownDesc.isEmpty()) {
            evaluateCircadianBaseline();
        }
        return lastKnownDesc;
    }

    public float getLastKnownBrightness() {
        if (lastKnownBrightness <= 0) {
            evaluateCircadianBaseline();
        }
        return lastKnownBrightness;
    }

    private void evaluateCircadianBaseline() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 7 && hour < 12) {
            lastKnownLux = 320f;
            lastKnownDesc = "☀️ Morning Light";
            lastKnownBrightness = 0.72f;
        } else if (hour >= 12 && hour < 17) {
            lastKnownLux = 420f;
            lastKnownDesc = "☀️ Bright Day";
            lastKnownBrightness = 0.82f;
        } else if (hour >= 17 && hour < 20) {
            lastKnownLux = 160f;
            lastKnownDesc = "🌅 Evening Soft";
            lastKnownBrightness = 0.58f;
        } else if (hour >= 20 && hour < 23) {
            lastKnownLux = 55f;
            lastKnownDesc = "🌙 Bedtime Care";
            lastKnownBrightness = 0.38f;
        } else {
            lastKnownLux = 25f;
            lastKnownDesc = "✨ Night Care";
            lastKnownBrightness = 0.28f;
        }
    }

    private void sampleCameraAmbient() {
        Camera camera = null;
        SurfaceTexture dummyTexture = null;
        try {
            int numCameras = Camera.getNumberOfCameras();
            int frontCameraId = -1;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontCameraId = i;
                    break;
                }
            }

            if (frontCameraId == -1 && numCameras > 0) {
                frontCameraId = 0;
            }

            if (frontCameraId == -1) return;

            camera = Camera.open(frontCameraId);
            if (camera == null) return;

            Camera.Parameters params = camera.getParameters();
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();
            if (sizes != null && !sizes.isEmpty()) {
                Camera.Size smallest = sizes.get(sizes.size() - 1);
                params.setPreviewSize(smallest.width, smallest.height);
            }
            camera.setParameters(params);

            dummyTexture = new SurfaceTexture(0);
            camera.setPreviewTexture(dummyTexture);

            final Camera finalCamera = camera;
            final SurfaceTexture finalTexture = dummyTexture;

            camera.setOneShotPreviewCallback((data, cam) -> {
                try {
                    if (data != null && data.length > 0) {
                        long sum = 0;
                        int step = Math.max(1, data.length / 400);
                        int count = 0;
                        int yLimit = Math.min(data.length, params.getPreviewSize().width * params.getPreviewSize().height);
                        for (int i = 0; i < yLimit; i += step) {
                            sum += (data[i] & 0xFF);
                            count++;
                        }
                        int avgLuma = count > 0 ? (int) (sum / count) : 120;
                        float lux = (float) Math.max(15, Math.min(650, Math.pow(avgLuma / 255.0, 1.8) * 600.0));

                        String desc;
                        float brightness;
                        if (lux < 35) {
                            desc = "🌙 Night Care";
                            brightness = 0.28f;
                        } else if (lux < 130) {
                            desc = "💡 Soft Room";
                            brightness = 0.50f;
                        } else if (lux < 320) {
                            desc = "☀️ Indoor Day";
                            brightness = 0.70f;
                        } else {
                            desc = "✨ Bright Sunlit";
                            brightness = 0.88f;
                        }

                        lastKnownLux = lux;
                        lastKnownDesc = desc;
                        lastKnownBrightness = brightness;

                        mainHandler.post(() -> {
                            if (isRunning && activeListener != null) {
                                activeListener.onAmbientUpdate(lux, desc, brightness);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error calculating preview luma", e);
                } finally {
                    try {
                        cam.stopPreview();
                        cam.release();
                    } catch (Exception ignored) {}
                    try {
                        finalTexture.release();
                    } catch (Exception ignored) {}
                }
            });

            camera.startPreview();

        } catch (Exception e) {
            Log.w(TAG, "Camera ambient sample failed; using circadian estimation", e);
            if (camera != null) {
                try {
                    camera.release();
                } catch (Exception ignored) {}
            }
            if (dummyTexture != null) {
                try {
                    dummyTexture.release();
                } catch (Exception ignored) {}
            }
        }
    }
}
