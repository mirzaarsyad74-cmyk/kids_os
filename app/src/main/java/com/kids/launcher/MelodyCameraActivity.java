package com.kids.launcher;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.MediaActionSound;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated My Melody Sparkle Camera for Kids.
 * Features:
 * - Selfie / Front Camera & Back Camera switching.
 * - Cute sticker frames & "No Filter" option.
 * - Flashlight Torch mode on back camera that stays continuously lit until exit.
 * - Preserves stock Android navigation bar.
 * - Auto-saving to Gallery with instant photo preview modal.
 */
public class MelodyCameraActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final int PERMISSION_REQ_CODE = 2001;

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private int currentCameraId = 0; // 0 = back, 1 = front
    private boolean isPreviewRunning = false;

    private MelodyFrameOverlayView frameOverlay;
    private View viewShutterFlash;
    private RelativeLayout layoutPreviewModal;
    private ImageView ivCapturedPhotoPreview;
    private TextView btnPreviewTakeMore;

    private TextView btnFlash;
    private TextView btnFlip;
    private FrameLayout btnShutter;
    private TextView btnBack;

    private TextView chipNone, chipMelody, chipSparkles, chipBerry, chipHearts, chipPolaroid;
    private boolean isTorchActive = false;

    private MediaActionSound sound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setWindowUiFlags();
        setContentView(R.layout.activity_melody_camera);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            sound = new MediaActionSound();
            sound.load(MediaActionSound.SHUTTER_CLICK);
        }

        initViews();
        setupFrameChips();
        checkPermissionsAndStart();
    }

    private void setWindowUiFlags() {
        // Keep stock navigation bar visible and usable
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void initViews() {
        surfaceView = findViewById(R.id.camera_surface_view);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        frameOverlay = findViewById(R.id.melody_frame_overlay);
        viewShutterFlash = findViewById(R.id.view_shutter_flash);
        layoutPreviewModal = findViewById(R.id.layout_photo_preview_modal);
        ivCapturedPhotoPreview = findViewById(R.id.iv_captured_photo_preview);
        btnPreviewTakeMore = findViewById(R.id.btn_preview_take_more);

        btnFlash = findViewById(R.id.btn_camera_flash);
        btnFlip = findViewById(R.id.btn_camera_flip);
        btnShutter = findViewById(R.id.btn_camera_shutter);
        btnBack = findViewById(R.id.btn_camera_back);

        btnBack.setOnClickListener(v -> finish());
        btnShutter.setOnClickListener(v -> takeMelodyPhoto());
        btnFlip.setOnClickListener(v -> flipCamera());
        btnFlash.setOnClickListener(v -> toggleFlash());

        btnPreviewTakeMore.setOnClickListener(v -> {
            layoutPreviewModal.setVisibility(View.GONE);
            startPreview();
        });

        // Default to back camera so flash torch is readily available, or front if requested
        int backId = findCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
        if (backId != -1) {
            currentCameraId = backId;
        } else {
            currentCameraId = 0;
        }
    }

    private void setupFrameChips() {
        chipNone = findViewById(R.id.chip_frame_none);
        chipMelody = findViewById(R.id.chip_frame_melody);
        chipSparkles = findViewById(R.id.chip_frame_sparkles);
        chipBerry = findViewById(R.id.chip_frame_berry);
        chipHearts = findViewById(R.id.chip_frame_hearts);
        chipPolaroid = findViewById(R.id.chip_frame_polaroid);

        if (chipNone != null) {
            chipNone.setOnClickListener(v -> selectFrame(MelodyFrameOverlayView.FRAME_NONE, chipNone));
        }
        chipMelody.setOnClickListener(v -> selectFrame(MelodyFrameOverlayView.FRAME_MELODY_EARS, chipMelody));
        chipSparkles.setOnClickListener(v -> selectFrame(MelodyFrameOverlayView.FRAME_SPARKLES_STARS, chipSparkles));
        chipBerry.setOnClickListener(v -> selectFrame(MelodyFrameOverlayView.FRAME_SWEET_STRAWBERRY, chipBerry));
        chipHearts.setOnClickListener(v -> selectFrame(MelodyFrameOverlayView.FRAME_HEART_CLOUDS, chipHearts));
        chipPolaroid.setOnClickListener(v -> selectFrame(MelodyFrameOverlayView.FRAME_POLAROID, chipPolaroid));
    }

    private void selectFrame(int frameType, TextView selectedChip) {
        frameOverlay.setFrame(frameType);

        TextView[] chips = {chipNone, chipMelody, chipSparkles, chipBerry, chipHearts, chipPolaroid};
        for (TextView chip : chips) {
            if (chip != null) {
                chip.setBackgroundResource(R.drawable.bg_melody_chip_unselected);
                chip.setTextColor(Color.parseColor("#831843"));
            }
        }
        if (selectedChip != null) {
            selectedChip.setBackgroundResource(R.drawable.bg_melody_chip_selected);
            selectedChip.setTextColor(Color.WHITE);
        }
    }

    private void checkPermissionsAndStart() {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        boolean allGranted = true;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (!allGranted) {
            ActivityCompat.requestPermissions(this, perms, PERMISSION_REQ_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            reopenCamera();
        }
    }

    private int findCameraId(int facing) {
        int numCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == facing) {
                return i;
            }
        }
        return -1;
    }

    private void reopenCamera() {
        stopCamera();
        try {
            camera = Camera.open(currentCameraId);
            Camera.Parameters params = camera.getParameters();

            // Set preview resolution
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();
            if (sizes != null && !sizes.isEmpty()) {
                Camera.Size optimal = sizes.get(0);
                for (Camera.Size s : sizes) {
                    if (s.width >= 800 && s.width <= 1280) {
                        optimal = s;
                        break;
                    }
                }
                params.setPreviewSize(optimal.width, optimal.height);
            }

            // Set picture size
            List<Camera.Size> picSizes = params.getSupportedPictureSizes();
            if (picSizes != null && !picSizes.isEmpty()) {
                params.setPictureSize(picSizes.get(0).width, picSizes.get(0).height);
            }

            // Check and restore continuous torch if back camera
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(currentCameraId, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                List<String> supported = params.getSupportedFlashModes();
                if (isTorchActive && supported != null && supported.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    btnFlash.setText("🔦 Light: ON");
                    btnFlash.setBackgroundResource(R.drawable.bg_melody_chip_selected);
                    btnFlash.setTextColor(Color.WHITE);
                } else if (!isTorchActive) {
                    if (supported != null && supported.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    }
                    btnFlash.setText("⚡ Flash: OFF");
                    btnFlash.setBackgroundResource(R.drawable.bg_melody_pill);
                    btnFlash.setTextColor(Color.parseColor("#831843"));
                }
            } else {
                btnFlash.setText("⚡ Flash: OFF");
                btnFlash.setBackgroundResource(R.drawable.bg_melody_pill);
                btnFlash.setTextColor(Color.parseColor("#831843"));
            }

            camera.setParameters(params);
            camera.setDisplayOrientation(0); // landscape
            camera.setPreviewDisplay(surfaceHolder);
            startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        if (camera != null && !isPreviewRunning) {
            try {
                camera.startPreview();
                isPreviewRunning = true;
            } catch (Exception ignored) {}
        }
    }

    private void stopCamera() {
        if (camera != null) {
            try {
                Camera.Parameters params = camera.getParameters();
                if (params != null) {
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    camera.setParameters(params);
                }
            } catch (Exception ignored) {}
            try {
                if (isPreviewRunning) {
                    camera.stopPreview();
                    isPreviewRunning = false;
                }
                camera.release();
            } catch (Exception ignored) {}
            camera = null;
        }
    }

    private void flipCamera() {
        int numCameras = Camera.getNumberOfCameras();
        if (numCameras <= 1) {
            Toast.makeText(this, "Only one camera available on this tablet", Toast.LENGTH_SHORT).show();
            return;
        }
        currentCameraId = (currentCameraId + 1) % numCameras;
        reopenCamera();
    }

    /**
     * Toggles continuous flashlight (TORCH mode) on back camera.
     * Stays continuously ON until toggled off or user exits the camera app.
     */
    private void toggleFlash() {
        if (camera == null) return;
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(currentCameraId, info);
            if (info.facing != Camera.CameraInfo.CAMERA_FACING_BACK) {
                Toast.makeText(this, "🔦 Flashlight is on the Back Camera!", Toast.LENGTH_SHORT).show();
                return;
            }

            Camera.Parameters params = camera.getParameters();
            List<String> supported = params.getSupportedFlashModes();
            if (supported == null || !supported.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                Toast.makeText(this, "Flashlight not supported on this camera", Toast.LENGTH_SHORT).show();
                return;
            }

            isTorchActive = !isTorchActive;
            if (isTorchActive) {
                params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                btnFlash.setText("🔦 Light: ON");
                btnFlash.setBackgroundResource(R.drawable.bg_melody_chip_selected);
                btnFlash.setTextColor(Color.WHITE);
                Toast.makeText(this, "🔦 Flashlight stays ON until you exit camera!", Toast.LENGTH_SHORT).show();
            } else {
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                btnFlash.setText("⚡ Flash: OFF");
                btnFlash.setBackgroundResource(R.drawable.bg_melody_pill);
                btnFlash.setTextColor(Color.parseColor("#831843"));
            }
            camera.setParameters(params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void takeMelodyPhoto() {
        if (camera == null || !isPreviewRunning) return;

        // Visual flash & sound
        if (sound != null) {
            sound.play(MediaActionSound.SHUTTER_CLICK);
        }
        viewShutterFlash.setVisibility(View.VISIBLE);
        viewShutterFlash.setAlpha(1f);
        viewShutterFlash.animate().alpha(0f).setDuration(250).withEndAction(() -> viewShutterFlash.setVisibility(View.GONE)).start();

        try {
            camera.takePicture(null, null, (data, cam) -> {
                isPreviewRunning = false;
                savePhotoWithFrame(data);
            });
        } catch (Exception e) {
            e.printStackTrace();
            startPreview();
        }
    }

    private void savePhotoWithFrame(byte[] jpegData) {
        new Thread(() -> {
            try {
                Bitmap original = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                if (original == null) return;

                // Adjust mirror if front camera
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(currentCameraId, info);
                Bitmap orientedBitmap;
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    Matrix m = new Matrix();
                    m.preScale(-1.0f, 1.0f);
                    orientedBitmap = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), m, true);
                } else {
                    orientedBitmap = original;
                }

                Bitmap composited = orientedBitmap.copy(Bitmap.Config.ARGB_8888, true);
                // Only composite frame if a filter is selected (not FRAME_NONE)
                if (frameOverlay.getFrame() != MelodyFrameOverlayView.FRAME_NONE) {
                    Canvas canvas = new Canvas(composited);
                    MelodyFrameOverlayView.drawFrameOverlay(canvas, composited.getWidth(), composited.getHeight(), frameOverlay.getFrame());
                }

                // Save to Gallery directory
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MelodyCamera");
                if (!dir.exists()) dir.mkdirs();

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File photoFile = new File(dir, "MELODY_" + timeStamp + ".jpg");

                FileOutputStream fos = new FileOutputStream(photoFile);
                composited.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                fos.flush();
                fos.close();

                // Scan into Android Media Store
                MediaScannerConnection.scanFile(this, new String[]{photoFile.getAbsolutePath()}, new String[]{"image/jpeg"}, null);

                // Show on UI
                runOnUiThread(() -> {
                    ivCapturedPhotoPreview.setImageBitmap(composited);
                    layoutPreviewModal.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "🌸 Photo saved to Gallery!", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Could not save photo", Toast.LENGTH_SHORT).show();
                    startPreview();
                });
            }
        }).start();
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        reopenCamera();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        startPreview();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        stopCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Turn off continuous flashlight upon exiting camera activity
        isTorchActive = false;
        stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isTorchActive = false;
        stopCamera();
        if (sound != null) {
            sound.release();
        }
    }
}
