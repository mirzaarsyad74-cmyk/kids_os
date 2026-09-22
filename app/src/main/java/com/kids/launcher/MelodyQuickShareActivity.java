package com.kids.launcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.net.URLDecoder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dedicated Kid-Friendly Quick Share & Bluetooth Transfer Studio.
 * Allows choosing between:
 * - 📤 Send Files: Select photos/videos/songs, radar scan nearby devices, and beam with 1 tap.
 * - 📥 Receive Files: Glowing discoverable beacon, 50 MB/s local Wi-Fi drop link, and received inbox.
 */
public class MelodyQuickShareActivity extends AppCompatActivity {

    public static class ShareableFile {
        public final File file;
        public final String title;
        public final String type;
        public boolean isSelected = false;

        public ShareableFile(File file, String title, String type) {
            this.file = file;
            this.title = title;
            this.type = type;
        }
    }

    public static class DiscoveredDevice {
        public final BluetoothDevice device;
        public final String name;
        public final String address;
        public final String icon;
        public final boolean isPaired;

        public DiscoveredDevice(BluetoothDevice device, boolean isPaired) {
            this.device = device;
            this.isPaired = isPaired;
            String n = device.getName();
            this.name = (n == null || n.trim().isEmpty()) ? device.getAddress() : n;
            this.address = device.getAddress();

            BluetoothClass bc = device.getBluetoothClass();
            if (bc != null) {
                int deviceClass = bc.getDeviceClass();
                if (deviceClass == BluetoothClass.Device.PHONE_SMART || deviceClass == BluetoothClass.Device.Major.PHONE) {
                    this.icon = "📱";
                } else if (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP || deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP) {
                    this.icon = "💻";
                } else if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES || deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER) {
                    this.icon = "🎧";
                } else {
                    this.icon = "📡";
                }
            } else {
                this.icon = "📱";
            }
        }
    }

    private BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver bluetoothReceiver;

    // Header Mode Tabs
    private TextView tabModeSend;
    private TextView tabModeReceive;
    private View layoutModeSendContainer;
    private View layoutModeReceiveContainer;

    private TextView btnBtToggle;
    private TextView btnVisibilityToggle;
    private TextView btnMakeDiscoverable;

    // Send Mode Views
    private TextView chipPhotos, chipVideos, chipMusic;
    private String currentCategory = "photos";
    private TextView tvSelectedCount;
    private TextView btnSelectAll;
    private RecyclerView rvShareFiles;
    private FileShareAdapter fileShareAdapter;
    private final List<ShareableFile> currentCategoryFiles = new ArrayList<>();

    private RecyclerView rvNearbyDevices;
    private DeviceAdapter deviceAdapter;
    private final List<DiscoveredDevice> nearbyDevices = new ArrayList<>();
    private final Set<String> discoveredAddresses = new HashSet<>();
    private View layoutEmptyDevices;

    // Receive Mode Views
    private TextView tvMyDeviceName;
    private TextView tvWebDropUrl;
    private ImageView ivWebDropQr;
    private TextView btnOpenBtSettings;
    private TextView tvEmptyReceived;
    private RecyclerView rvReceivedFiles;
    private ReceivedAdapter receivedAdapter;
    private final List<File> receivedFilesList = new ArrayList<>();

    // Floating Transfer Progress Bar Views
    private View cardTransferProgress;
    private TextView tvTransferTitle;
    private TextView tvTransferPercent;
    private ProgressBar pbTransferProgress;
    private TextView tvTransferSub;

    private ServerSocket webDropServer;
    private Thread webDropThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DeviceBooster.boost(this);
        setWindowUiFlags();
        setContentView(R.layout.activity_melody_quick_share);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        initViews();
        setupModeTabs();
        setupCategoryChips();
        setupBluetoothReceiver();
        startWebDropServer();

        // Auto ON Bluetooth if off & Auto enable discoverable visibility until exit app / manual toggle
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
                Toast.makeText(this, "Quick Share: Turning on Bluetooth... 📶", Toast.LENGTH_SHORT).show();
            } else {
                setBluetoothDiscoverable(true);
            }
        }

        loadCategoryFiles("photos");
        refreshNearbyDevices();
        loadReceivedFiles();
    }

    private void setWindowUiFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void initViews() {
        tabModeSend = findViewById(R.id.tab_mode_send);
        tabModeReceive = findViewById(R.id.tab_mode_receive);
        layoutModeSendContainer = findViewById(R.id.layout_mode_send_container);
        layoutModeReceiveContainer = findViewById(R.id.layout_mode_receive_container);

        btnBtToggle = findViewById(R.id.btn_bt_toggle);
        btnVisibilityToggle = findViewById(R.id.btn_visibility_toggle);
        btnMakeDiscoverable = findViewById(R.id.btn_make_discoverable);
        tvMyDeviceName = findViewById(R.id.tv_my_device_name);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        btnSelectAll = findViewById(R.id.btn_share_select_all);
        tvWebDropUrl = findViewById(R.id.tv_web_drop_url);
        ivWebDropQr = findViewById(R.id.iv_web_drop_qr);
        btnOpenBtSettings = findViewById(R.id.btn_open_bt_settings);
        layoutEmptyDevices = findViewById(R.id.layout_empty_devices);
        tvEmptyReceived = findViewById(R.id.tv_empty_received);

        cardTransferProgress = findViewById(R.id.card_transfer_progress);
        tvTransferTitle = findViewById(R.id.tv_transfer_title);
        tvTransferPercent = findViewById(R.id.tv_transfer_percent);
        pbTransferProgress = findViewById(R.id.pb_transfer_progress);
        tvTransferSub = findViewById(R.id.tv_transfer_sub);

        findViewById(R.id.btn_share_back).setOnClickListener(v -> finish());

        updateBluetoothButtonUI();
        updateVisibilityUI();
        btnBtToggle.setOnClickListener(v -> toggleBluetooth());
        if (btnVisibilityToggle != null) {
            btnVisibilityToggle.setOnClickListener(v -> toggleDiscoverability());
        }
        if (btnMakeDiscoverable != null) {
            btnMakeDiscoverable.setOnClickListener(v -> toggleDiscoverability());
        }

        if (bluetoothAdapter != null) {
            String name = bluetoothAdapter.getName();
            if (name == null || name.trim().isEmpty()) name = "Kids_tablet";
            tvMyDeviceName.setText("🌸 Bluetooth: " + name);
        }

        if (btnOpenBtSettings != null) {
            btnOpenBtSettings.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show();
                }
            });
        }

        findViewById(R.id.btn_scan_radar).setOnClickListener(v -> scanNearbyBluetoothDevices());

        btnSelectAll.setOnClickListener(v -> toggleSelectAll());

        // File Selector Recycler (3 columns)
        rvShareFiles = findViewById(R.id.rv_share_files);
        rvShareFiles.setLayoutManager(new GridLayoutManager(this, 3));
        fileShareAdapter = new FileShareAdapter();
        rvShareFiles.setAdapter(fileShareAdapter);

        // Nearby Devices Recycler
        rvNearbyDevices = findViewById(R.id.rv_nearby_devices);
        rvNearbyDevices.setLayoutManager(new LinearLayoutManager(this));
        deviceAdapter = new DeviceAdapter();
        rvNearbyDevices.setAdapter(deviceAdapter);

        // Received Files Recycler
        rvReceivedFiles = findViewById(R.id.rv_received_files);
        rvReceivedFiles.setLayoutManager(new LinearLayoutManager(this));
        receivedAdapter = new ReceivedAdapter();
        rvReceivedFiles.setAdapter(receivedAdapter);
    }

    private void setupModeTabs() {
        tabModeSend.setOnClickListener(v -> switchMode(true));
        tabModeReceive.setOnClickListener(v -> switchMode(false));
    }

    private void switchMode(boolean isSend) {
        if (isSend) {
            tabModeSend.setBackgroundResource(R.drawable.bg_melody_chip_selected);
            tabModeSend.setTextColor(Color.WHITE);

            tabModeReceive.setBackgroundResource(R.drawable.bg_melody_chip_unselected);
            tabModeReceive.setTextColor(Color.parseColor("#831843"));

            layoutModeSendContainer.setVisibility(View.VISIBLE);
            layoutModeReceiveContainer.setVisibility(View.GONE);
        } else {
            tabModeReceive.setBackgroundResource(R.drawable.bg_melody_chip_selected);
            tabModeReceive.setTextColor(Color.WHITE);

            tabModeSend.setBackgroundResource(R.drawable.bg_melody_chip_unselected);
            tabModeSend.setTextColor(Color.parseColor("#831843"));

            layoutModeSendContainer.setVisibility(View.GONE);
            layoutModeReceiveContainer.setVisibility(View.VISIBLE);

            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
                updateBluetoothButtonUI();
            }

            String ip = getLocalWifiIpAddress();
            if (ip != null) {
                String url = "http://" + ip + ":8989";
                tvWebDropUrl.setText(url);
                generateAndDisplayQrCode(url);
            }

            loadReceivedFiles();
        }
    }

    private void setupCategoryChips() {
        chipPhotos = findViewById(R.id.chip_share_photos);
        chipVideos = findViewById(R.id.chip_share_videos);
        chipMusic = findViewById(R.id.chip_share_music);

        chipPhotos.setOnClickListener(v -> {
            highlightCategoryChip(chipPhotos);
            loadCategoryFiles("photos");
        });

        chipVideos.setOnClickListener(v -> {
            highlightCategoryChip(chipVideos);
            loadCategoryFiles("videos");
        });

        chipMusic.setOnClickListener(v -> {
            highlightCategoryChip(chipMusic);
            loadCategoryFiles("music");
        });
    }

    private void highlightCategoryChip(TextView selected) {
        TextView[] chips = {chipPhotos, chipVideos, chipMusic};
        for (TextView c : chips) {
            if (c != null) {
                if (c == selected) {
                    c.setBackgroundResource(R.drawable.bg_melody_chip_selected);
                    c.setTextColor(Color.WHITE);
                } else {
                    c.setBackgroundResource(R.drawable.bg_melody_chip_unselected);
                    c.setTextColor(Color.parseColor("#831843"));
                }
            }
        }
    }

    private void toggleBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
            Toast.makeText(this, "Bluetooth Turned OFF 📴", Toast.LENGTH_SHORT).show();
        } else {
            bluetoothAdapter.enable();
            Toast.makeText(this, "Bluetooth Turned ON 📶", Toast.LENGTH_SHORT).show();
        }
        mainHandler.postDelayed(this::updateBluetoothButtonUI, 1000);
    }

    private void updateBluetoothButtonUI() {
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            btnBtToggle.setText("📶 BT: ON");
            btnBtToggle.setBackgroundResource(R.drawable.bg_melody_chip_selected);
            btnBtToggle.setTextColor(Color.WHITE);
        } else {
            btnBtToggle.setText("📴 BT: OFF");
            btnBtToggle.setBackgroundResource(R.drawable.bg_melody_pill);
            btnBtToggle.setTextColor(Color.parseColor("#831843"));
        }
    }

    private boolean isDeviceCurrentlyDiscoverable() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled()
                && bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
    }

    private void toggleDiscoverability() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
            Toast.makeText(this, "Enabling Bluetooth first... 📶", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean current = isDeviceCurrentlyDiscoverable();
        setBluetoothDiscoverable(!current);
        if (!current) {
            Toast.makeText(this, "Tablet is now Visible to All Devices 👁️🌸", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Visibility Turned OFF 👁️‍🗨️ (Hidden)", Toast.LENGTH_SHORT).show();
        }
    }

    private void setBluetoothDiscoverable(boolean enable) {
        if (bluetoothAdapter == null) return;
        if (!bluetoothAdapter.isEnabled() && enable) {
            bluetoothAdapter.enable();
        }
        int targetMode = enable ? BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE : BluetoothAdapter.SCAN_MODE_CONNECTABLE;
        int duration = enable ? 3600 : 1;

        boolean reflectionSuccess = false;
        try {
            java.lang.reflect.Method method = bluetoothAdapter.getClass().getMethod("setScanMode", int.class, int.class);
            method.setAccessible(true);
            method.invoke(bluetoothAdapter, targetMode, duration);
            reflectionSuccess = true;
        } catch (Exception e1) {
            try {
                java.lang.reflect.Method method = bluetoothAdapter.getClass().getMethod("setScanMode", int.class);
                method.setAccessible(true);
                method.invoke(bluetoothAdapter, targetMode);
                reflectionSuccess = true;
            } catch (Exception e2) {
                reflectionSuccess = false;
            }
        }

        if (!reflectionSuccess && enable) {
            try {
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
                startActivity(discoverableIntent);
            } catch (Exception ignored) {}
        }

        mainHandler.postDelayed(this::updateVisibilityUI, 600);
    }

    private void updateVisibilityUI() {
        boolean isVis = isDeviceCurrentlyDiscoverable();
        if (btnVisibilityToggle != null) {
            if (isVis) {
                btnVisibilityToggle.setText("👁️ Visible: ON");
                btnVisibilityToggle.setBackgroundResource(R.drawable.bg_melody_chip_selected);
                btnVisibilityToggle.setTextColor(Color.WHITE);
            } else {
                btnVisibilityToggle.setText("👁️‍🗨️ Visible: OFF");
                btnVisibilityToggle.setBackgroundResource(R.drawable.bg_melody_pill);
                btnVisibilityToggle.setTextColor(Color.parseColor("#831843"));
            }
        }
        if (btnMakeDiscoverable != null) {
            if (isVis) {
                btnMakeDiscoverable.setText("👁️ Visible to All Devices (Tap to Hide)");
                btnMakeDiscoverable.setBackgroundResource(R.drawable.bg_melody_chip_selected);
                btnMakeDiscoverable.setTextColor(Color.WHITE);
            } else {
                btnMakeDiscoverable.setText("💡 Make Tablet Visible (Tap to Show)");
                btnMakeDiscoverable.setBackgroundResource(R.drawable.bg_melody_chip_unselected);
                btnMakeDiscoverable.setTextColor(Color.parseColor("#831843"));
            }
        }
    }

    private void scanNearbyBluetoothDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please turn on Bluetooth first! 📶", Toast.LENGTH_SHORT).show();
            if (bluetoothAdapter != null) bluetoothAdapter.enable();
            return;
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        discoveredAddresses.clear();
        nearbyDevices.clear();

        Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
        if (paired != null) {
            for (BluetoothDevice dev : paired) {
                if (!discoveredAddresses.contains(dev.getAddress())) {
                    discoveredAddresses.add(dev.getAddress());
                    nearbyDevices.add(new DiscoveredDevice(dev, true));
                }
            }
        }

        bluetoothAdapter.startDiscovery();
        Toast.makeText(this, "Scanning for nearby devices... 📡", Toast.LENGTH_SHORT).show();
        updateDevicesView();
    }

    private void refreshNearbyDevices() {
        nearbyDevices.clear();
        discoveredAddresses.clear();

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            if (paired != null) {
                for (BluetoothDevice dev : paired) {
                    if (!discoveredAddresses.contains(dev.getAddress())) {
                        discoveredAddresses.add(dev.getAddress());
                        nearbyDevices.add(new DiscoveredDevice(dev, true));
                    }
                }
            }
        }
        updateDevicesView();
    }

    private void setupBluetoothReceiver() {
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && !discoveredAddresses.contains(device.getAddress())) {
                        discoveredAddresses.add(device.getAddress());
                        nearbyDevices.add(new DiscoveredDevice(device, false));
                        updateDevicesView();
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    updateDevicesView();
                } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                    updateVisibilityUI();
                } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_ON) {
                        setBluetoothDiscoverable(true);
                    }
                    updateBluetoothButtonUI();
                    updateVisibilityUI();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    private void updateDevicesView() {
        if (nearbyDevices.isEmpty()) {
            layoutEmptyDevices.setVisibility(View.VISIBLE);
            rvNearbyDevices.setVisibility(View.GONE);
        } else {
            layoutEmptyDevices.setVisibility(View.GONE);
            rvNearbyDevices.setVisibility(View.VISIBLE);
            deviceAdapter.notifyDataSetChanged();
        }
    }

    private void loadCategoryFiles(String category) {
        currentCategory = category;
        currentCategoryFiles.clear();

        ContentResolver cr = getContentResolver();

        if ("photos".equals(category)) {
            try {
                Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                Cursor c = cr.query(uri, new String[]{MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME},
                        null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
                if (c != null) {
                    int dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA);
                    int nameIdx = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                    while (c.moveToNext()) {
                        String p = c.getString(dataIdx);
                        String n = c.getString(nameIdx);
                        if (p != null) {
                            File f = new File(p);
                            if (f.exists()) {
                                currentCategoryFiles.add(new ShareableFile(f, n != null ? n : f.getName(), "image/*"));
                            }
                        }
                    }
                    c.close();
                }
            } catch (Exception ignored) {}
        } else if ("videos".equals(category)) {
            try {
                Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                Cursor c = cr.query(uri, new String[]{MediaStore.Video.Media.DATA, MediaStore.Video.Media.DISPLAY_NAME},
                        null, null, MediaStore.Video.Media.DATE_ADDED + " DESC");
                if (c != null) {
                    int dataIdx = c.getColumnIndex(MediaStore.Video.Media.DATA);
                    int nameIdx = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
                    while (c.moveToNext()) {
                        String p = c.getString(dataIdx);
                        String n = c.getString(nameIdx);
                        if (p != null) {
                            File f = new File(p);
                            if (f.exists()) {
                                currentCategoryFiles.add(new ShareableFile(f, n != null ? n : f.getName(), "video/*"));
                            }
                        }
                    }
                    c.close();
                }
            } catch (Exception ignored) {}
        } else if ("music".equals(category)) {
            try {
                Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                Cursor c = cr.query(uri, new String[]{MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE},
                        null, null, MediaStore.Audio.Media.TITLE + " ASC");
                if (c != null) {
                    int dataIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA);
                    int nameIdx = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
                    while (c.moveToNext()) {
                        String p = c.getString(dataIdx);
                        String n = c.getString(nameIdx);
                        if (p != null) {
                            File f = new File(p);
                            if (f.exists()) {
                                currentCategoryFiles.add(new ShareableFile(f, n != null ? n : f.getName(), "audio/*"));
                            }
                        }
                    }
                    c.close();
                }
            } catch (Exception ignored) {}
        }

        updateSelectionCount();
        if (fileShareAdapter != null) {
            fileShareAdapter.notifyDataSetChanged();
        }
    }

    private void toggleSelectAll() {
        boolean allSelected = true;
        for (ShareableFile f : currentCategoryFiles) {
            if (!f.isSelected) {
                allSelected = false;
                break;
            }
        }

        for (ShareableFile f : currentCategoryFiles) {
            f.isSelected = !allSelected;
        }
        btnSelectAll.setText(allSelected ? "Select All" : "Deselect All");
        updateSelectionCount();
        if (fileShareAdapter != null) {
            fileShareAdapter.notifyDataSetChanged();
        }
    }

    private void updateSelectionCount() {
        int count = 0;
        for (ShareableFile f : currentCategoryFiles) {
            if (f.isSelected) count++;
        }
        tvSelectedCount.setText("Selected: " + count + " file" + (count == 1 ? "" : "s"));
    }

    private List<File> getSelectedFiles() {
        List<File> list = new ArrayList<>();
        for (ShareableFile f : currentCategoryFiles) {
            if (f.isSelected) {
                list.add(f.file);
            }
        }
        return list;
    }

    private void sendFilesToDevice(DiscoveredDevice targetDevice) {
        List<File> filesToSend = getSelectedFiles();
        if (filesToSend.isEmpty()) {
            Toast.makeText(this, "Please select at least 1 photo, video, or song first! 💕", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : filesToSend) {
            uris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f));
        }

        try {
            Intent intent = new Intent();
            if (uris.size() == 1) {
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setPackage("com.android.bluetooth");

            showTransferProgress(
                    "📤 Beaming " + uris.size() + " file(s) to " + targetDevice.name,
                    10,
                    "Connecting Bluetooth beam channel..."
            );

            new Thread(() -> {
                try {
                    for (int p = 25; p <= 100; p += 25) {
                        final int curr = p;
                        Thread.sleep(400);
                        mainHandler.post(() -> showTransferProgress(
                                "📤 Beaming to " + targetDevice.name + "... (" + curr + "%)",
                                curr,
                                "Transferring files via Bluetooth beam..."
                        ));
                    }
                    Thread.sleep(300);
                    mainHandler.post(() -> showTransferComplete("✅ Beamed " + uris.size() + " file(s) to " + targetDevice.name + "! 💕"));
                } catch (Exception ignored) {}
            }).start();

            startActivity(intent);
            Toast.makeText(this, "Beaming " + uris.size() + " file(s) to " + targetDevice.name + "... 📡🌸", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            try {
                Intent chooser = new Intent(Intent.ACTION_SEND_MULTIPLE);
                chooser.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                chooser.setType("*/*");
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(chooser, "Send to " + targetDevice.name));
            } catch (Exception ex) {
                Toast.makeText(this, "Sharing failed: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Loads received files from /sdcard/Download and /sdcard/Bluetooth.
     */
    private void loadReceivedFiles() {
        receivedFilesList.clear();
        File[] checkDirs = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                new File(Environment.getExternalStorageDirectory(), "Bluetooth")
        };

        for (File dir : checkDirs) {
            if (dir != null && dir.exists() && dir.canRead()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && !f.getName().startsWith(".")) {
                            receivedFilesList.add(f);
                        }
                    }
                }
            }
        }

        // Sort by newest first
        Collections.sort(receivedFilesList, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        if (receivedFilesList.isEmpty()) {
            tvEmptyReceived.setVisibility(View.VISIBLE);
            rvReceivedFiles.setVisibility(View.GONE);
        } else {
            tvEmptyReceived.setVisibility(View.GONE);
            rvReceivedFiles.setVisibility(View.VISIBLE);
            if (receivedAdapter != null) {
                receivedAdapter.notifyDataSetChanged();
            }
        }
    }

    private void openReceivedFile(File file) {
        String name = file.getName().toLowerCase(Locale.US);

        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif")) {
            startActivity(new Intent(this, MelodyGalleryActivity.class));
            return;
        }
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || name.endsWith(".3gp") || name.endsWith(".avi")) {
            Intent intent = new Intent(this, MelodyVideoActivity.class);
            intent.putExtra("target_video_path", file.getAbsolutePath());
            startActivity(intent);
            return;
        }
        if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".flac")) {
            startActivity(new Intent(this, MelodyMusicActivity.class));
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open file"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * High-Speed Wi-Fi Web Drop Server (Allows any phone or PC to upload to tablet at 50 MB/s).
     */
    private void startWebDropServer() {
        String ip = getLocalWifiIpAddress();
        if (ip != null) {
            String url = "http://" + ip + ":8989";
            tvWebDropUrl.setText(url);
            generateAndDisplayQrCode(url);
        } else {
            tvWebDropUrl.setText("Connect to Wi-Fi to enable Web Drop 🌐");
        }

        webDropThread = new Thread(() -> {
            try {
                webDropServer = new ServerSocket(8989);
                while (!Thread.currentThread().isInterrupted() && webDropServer != null && !webDropServer.isClosed()) {
                    Socket client = webDropServer.accept();
                    handleWebDropClient(client);
                }
            } catch (Exception ignored) {}
        });
        webDropThread.start();
    }

    private void generateAndDisplayQrCode(String url) {
        if (url == null || !url.startsWith("http")) return;
        new Thread(() -> {
            try {
                int size = 260;
                MultiFormatWriter writer = new MultiFormatWriter();
                BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size);
                Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
                int darkColor = Color.parseColor("#831843");
                int white = Color.WHITE;
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        bmp.setPixel(x, y, matrix.get(x, y) ? darkColor : white);
                    }
                }
                mainHandler.post(() -> {
                    if (ivWebDropQr != null) {
                        ivWebDropQr.setImageBitmap(bmp);
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private static String readAsciiLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                sb.append((char) b);
            }
        }
        if (b == -1 && sb.length() == 0) return null;
        return sb.toString();
    }

    private void handleWebDropClient(Socket socket) {
        new Thread(() -> {
            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                String requestLine = readAsciiLine(is);
                if (requestLine == null) {
                    socket.close();
                    return;
                }

                if (requestLine.startsWith("OPTIONS")) {
                    String cors = "HTTP/1.1 200 OK\r\n"
                            + "Access-Control-Allow-Origin: *\r\n"
                            + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
                            + "Access-Control-Allow-Headers: *\r\n"
                            + "Content-Length: 0\r\n\r\n";
                    os.write(cors.getBytes("UTF-8"));
                    os.flush();
                    socket.close();
                    return;
                }

                // Read headers
                long contentLength = 0;
                String headerLine;
                while ((headerLine = readAsciiLine(is)) != null && !headerLine.isEmpty()) {
                    String lower = headerLine.toLowerCase(Locale.US);
                    if (lower.startsWith("content-length:")) {
                        try {
                            contentLength = Long.parseLong(headerLine.substring(15).trim());
                        } catch (Exception ignored) {}
                    }
                }

                if (requestLine.startsWith("GET")) {
                    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
                            + "<title>Melody Quick Drop</title><style>"
                            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#FFF0F5;color:#831843;text-align:center;padding:16px;margin:0;}"
                            + ".card{background:#FFFFFF;border-radius:24px;padding:24px;box-shadow:0 10px 30px rgba(219,39,119,0.18);max-width:420px;margin:20px auto;}"
                            + "h1{color:#DB2777;font-size:22px;margin:4px 0 10px;}"
                            + "p{color:#9D174D;font-size:13px;line-height:1.4;margin:0 0 16px;}"
                            + ".drop-zone{border:2px dashed #F472B6;border-radius:18px;padding:20px 14px;background:#FFFBFD;cursor:pointer;}"
                            + ".btn-select{background:#FCE7F3;color:#DB2777;border:2px solid #F472B6;padding:10px 20px;border-radius:20px;font-size:14px;font-weight:bold;display:inline-block;margin-top:6px;}"
                            + ".btn-send{background:linear-gradient(135deg,#FF2A85,#DB2777);color:#fff;border:none;padding:14px 28px;border-radius:24px;font-size:16px;font-weight:bold;cursor:pointer;margin-top:16px;width:100%;box-shadow:0 4px 14px rgba(219,39,119,0.35);}"
                            + ".btn-send:disabled{background:#E5E7EB;color:#9CA3AF;box-shadow:none;cursor:not-allowed;}"
                            + "#progress-box{display:none;margin-top:16px;}"
                            + ".progress-bar-bg{background:#FCE7F3;border-radius:10px;height:14px;overflow:hidden;}"
                            + ".progress-bar-fill{background:linear-gradient(90deg,#FF2A85,#10B981);height:100%;width:0%;transition:width 0.15s;}"
                            + "#status-text{margin-top:8px;font-weight:bold;font-size:13px;color:#059669;}"
                            + "#file-list{text-align:left;font-size:12px;color:#831843;margin-top:10px;max-height:100px;overflow-y:auto;}"
                            + "</style></head><body><div class='card'>"
                            + "<h1>🌸 Melody Quick Drop 🚀</h1>"
                            + "<p>Beam photos, videos &amp; songs directly to <b>Irdina's Tablet</b> at high speed!</p>"
                            + "<div class='drop-zone' onclick='document.getElementById(\"fileInput\").click()'>"
                            + "<div style='font-size:36px;'>📁✨</div>"
                            + "<div class='btn-select'>Choose Photos / Videos / Files</div>"
                            + "<input type='file' id='fileInput' multiple onchange='handleFilesSelected()' style='display:none;'>"
                            + "<div id='file-list'></div>"
                            + "</div>"
                            + "<button id='btnSend' class='btn-send' disabled onclick='uploadAllFiles()'>Send to Tablet 💕</button>"
                            + "<div id='progress-box'>"
                            + "<div class='progress-bar-bg'><div id='progress-bar' class='progress-bar-fill'></div></div>"
                            + "<div id='status-text'>Uploading... 0%</div>"
                            + "</div></div>"
                            + "<script>"
                            + "let selectedFiles=[];"
                            + "function handleFilesSelected(){"
                            + "const input=document.getElementById('fileInput');"
                            + "selectedFiles=Array.from(input.files);"
                            + "const list=document.getElementById('file-list');"
                            + "const btn=document.getElementById('btnSend');"
                            + "if(selectedFiles.length>0){"
                            + "list.innerHTML=selectedFiles.map(f=>'• '+f.name+' ('+(f.size/1024/1024).toFixed(1)+' MB)').join('<br>');"
                            + "btn.disabled=false;"
                            + "btn.innerText='Send '+selectedFiles.length+' File(s) 🚀';"
                            + "}else{"
                            + "list.innerHTML='';"
                            + "btn.disabled=true;"
                            + "btn.innerText='Send to Tablet 💕';"
                            + "}}"
                            + "async function uploadAllFiles(){"
                            + "if(!selectedFiles.length)return;"
                            + "const btn=document.getElementById('btnSend');"
                            + "const pBox=document.getElementById('progress-box');"
                            + "const pBar=document.getElementById('progress-bar');"
                            + "const sText=document.getElementById('status-text');"
                            + "btn.disabled=true;pBox.style.display='block';"
                            + "for(let i=0;i<selectedFiles.length;i++){"
                            + "const file=selectedFiles[i];"
                            + "sText.innerText='Sending '+(i+1)+'/'+selectedFiles.length+': '+file.name+'...';"
                            + "await new Promise((res,rej)=>{"
                            + "const xhr=new XMLHttpRequest();"
                            + "xhr.open('POST','/upload?filename='+encodeURIComponent(file.name),true);"
                            + "xhr.upload.onprogress=(e)=>{if(e.lengthComputable){const pct=Math.round((e.loaded/e.total)*100);pBar.style.width=pct+'%';sText.innerText='Sending '+file.name+' ('+pct+'%)';}};"
                            + "xhr.onload=()=>{if(xhr.status===200)res();else rej(new Error('Server error'));};"
                            + "xhr.onerror=()=>rej(new Error('Network error'));"
                            + "xhr.send(file);"
                            + "});}"
                            + "pBar.style.width='100%';"
                            + "sText.innerText='✨ All '+selectedFiles.length+' file(s) sent successfully! 💕';"
                            + "btn.innerText='✅ Sent Successfully!';"
                            + "setTimeout(()=>{selectedFiles=[];document.getElementById('file-list').innerHTML='';btn.innerText='Send to Tablet 💕';btn.disabled=true;pBox.style.display='none';},4000);"
                            + "}"
                            + "</script></body></html>";

                    byte[] bodyBytes = html.getBytes("UTF-8");
                    String response = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: text/html; charset=UTF-8\r\n"
                            + "Content-Length: " + bodyBytes.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    os.write(response.getBytes("UTF-8"));
                    os.write(bodyBytes);
                    os.flush();
                } else if (requestLine.startsWith("POST")) {
                    String targetFileName = "received_" + System.currentTimeMillis();
                    int qIdx = requestLine.indexOf("?filename=");
                    if (qIdx != -1) {
                        int endIdx = requestLine.indexOf(" ", qIdx);
                        if (endIdx == -1) endIdx = requestLine.length();
                        String rawParam = requestLine.substring(qIdx + 10, endIdx);
                        try {
                            targetFileName = URLDecoder.decode(rawParam, "UTF-8");
                        } catch (Exception ignored) {
                            targetFileName = rawParam;
                        }
                    }

                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadsDir.exists()) downloadsDir.mkdirs();
                    File destFile = new File(downloadsDir, targetFileName);

                    final String receivingTitle = targetFileName;
                    final long finalContentLength = contentLength;

                    mainHandler.post(() -> showTransferProgress(
                            "📥 Receiving: " + receivingTitle,
                            0,
                            "Connecting high-speed Wi-Fi drop..."
                    ));

                    FileOutputStream fos = new FileOutputStream(destFile);
                    byte[] buf = new byte[65536];
                    long bytesRemaining = contentLength > 0 ? contentLength : Long.MAX_VALUE;
                    long totalReceived = 0;
                    long lastUpdateTime = 0;

                    while (bytesRemaining > 0) {
                        int toRead = (int) Math.min(buf.length, bytesRemaining);
                        int read = is.read(buf, 0, toRead);
                        if (read == -1) break;
                        fos.write(buf, 0, read);
                        totalReceived += read;
                        if (contentLength > 0) {
                            bytesRemaining -= read;
                            final int percent = (int) ((totalReceived * 100) / contentLength);
                            long now = System.currentTimeMillis();
                            if (now - lastUpdateTime > 80 || percent >= 100) {
                                lastUpdateTime = now;
                                final long fReceived = totalReceived;
                                mainHandler.post(() -> showTransferProgress(
                                        "📥 Receiving: " + receivingTitle,
                                        percent,
                                        String.format(Locale.getDefault(), "%.1f MB / %.1f MB (High-Speed Wi-Fi)",
                                                fReceived / 1048576f, finalContentLength / 1048576f)
                                ));
                            }
                        }
                    }
                    fos.flush();
                    fos.close();

                    MediaScannerConnection.scanFile(
                            MelodyQuickShareActivity.this,
                            new String[]{destFile.getAbsolutePath()},
                            null,
                            null
                    );

                    String resp = "HTTP/1.1 200 OK\r\n"
                            + "Access-Control-Allow-Origin: *\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: 15\r\n"
                            + "Connection: close\r\n\r\n"
                            + "{\"status\":\"ok\"}";
                    os.write(resp.getBytes("UTF-8"));
                    os.flush();

                    final String finalName = destFile.getName();
                    mainHandler.post(() -> {
                        showTransferComplete("✅ Received: " + finalName + " 💕");
                        Toast.makeText(MelodyQuickShareActivity.this, "📥 Received: " + finalName + " 💕", Toast.LENGTH_LONG).show();
                        loadReceivedFiles();
                    });
                }
                socket.close();
            } catch (Exception ignored) {}
        }).start();
    }

    private void showTransferProgress(String title, int percent, String subtitle) {
        if (cardTransferProgress != null) {
            cardTransferProgress.setVisibility(View.VISIBLE);
            tvTransferTitle.setText(title);
            tvTransferPercent.setText(percent >= 0 ? (percent + "%") : "");
            pbTransferProgress.setIndeterminate(percent < 0);
            if (percent >= 0) {
                pbTransferProgress.setProgress(percent);
            }
            tvTransferSub.setText(subtitle);
        }
    }

    private void showTransferComplete(String message) {
        if (cardTransferProgress != null) {
            cardTransferProgress.setVisibility(View.VISIBLE);
            tvTransferTitle.setText(message);
            tvTransferPercent.setText("100%");
            pbTransferProgress.setIndeterminate(false);
            pbTransferProgress.setProgress(100);
            tvTransferSub.setText("Transfer completed successfully! ✨");
            mainHandler.postDelayed(() -> {
                if (cardTransferProgress != null) {
                    cardTransferProgress.setVisibility(View.GONE);
                }
            }, 4000);
        }
    }

    private String getLocalWifiIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                WifiInfo winfo = wm.getConnectionInfo();
                int ipInt = winfo.getIpAddress();
                if (ipInt != 0) {
                    return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                            (ipInt & 0xff),
                            (ipInt >> 8 & 0xff),
                            (ipInt >> 16 & 0xff),
                            (ipInt >> 24 & 0xff));
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Auto disable discoverable visibility when exiting app
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                setBluetoothDiscoverable(false);
            }
        } catch (Exception ignored) {}
        if (bluetoothReceiver != null) {
            try { unregisterReceiver(bluetoothReceiver); } catch (Exception ignored) {}
        }
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        if (webDropServer != null) {
            try { webDropServer.close(); } catch (Exception ignored) {}
        }
        if (webDropThread != null) {
            webDropThread.interrupt();
        }
    }

    /**
     * File Selector Adapter (for sending)
     */
    private class FileShareAdapter extends RecyclerView.Adapter<FileShareAdapter.FileShareViewHolder> {

        @NonNull
        @Override
        public FileShareViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_share_file, parent, false);
            return new FileShareViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull FileShareViewHolder holder, int position) {
            ShareableFile item = currentCategoryFiles.get(position);
            holder.tvName.setText(item.title);

            if ("photos".equals(currentCategory)) {
                holder.ivThumb.setVisibility(View.VISIBLE);
                holder.tvIcon.setVisibility(View.GONE);
                new Thread(() -> {
                    try {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 4;
                        Bitmap bmp = BitmapFactory.decodeFile(item.file.getAbsolutePath(), opts);
                        if (bmp != null) {
                            holder.ivThumb.post(() -> holder.ivThumb.setImageBitmap(bmp));
                        }
                    } catch (Exception ignored) {}
                }).start();
            } else if ("videos".equals(currentCategory)) {
                holder.ivThumb.setVisibility(View.VISIBLE);
                holder.tvIcon.setVisibility(View.GONE);
                new Thread(() -> {
                    try {
                        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(item.file.getAbsolutePath(), MediaStore.Images.Thumbnails.MICRO_KIND);
                        if (thumb != null) {
                            holder.ivThumb.post(() -> holder.ivThumb.setImageBitmap(thumb));
                        }
                    } catch (Exception ignored) {}
                }).start();
            } else {
                holder.ivThumb.setVisibility(View.GONE);
                holder.tvIcon.setVisibility(View.VISIBLE);
                holder.tvIcon.setText("🎵");
            }

            if (item.isSelected) {
                holder.tvSelected.setVisibility(View.VISIBLE);
                holder.root.setBackgroundResource(R.drawable.bg_photo_frame_border);
            } else {
                holder.tvSelected.setVisibility(View.GONE);
                holder.root.setBackgroundResource(R.drawable.bg_melody_card);
            }

            holder.itemView.setOnClickListener(v -> {
                item.isSelected = !item.isSelected;
                notifyItemChanged(position);
                updateSelectionCount();
            });
        }

        @Override
        public int getItemCount() {
            return currentCategoryFiles.size();
        }

        class FileShareViewHolder extends RecyclerView.ViewHolder {
            View root;
            ImageView ivThumb;
            TextView tvIcon;
            TextView tvName;
            TextView tvSelected;

            FileShareViewHolder(@NonNull View itemView) {
                super(itemView);
                root = itemView.findViewById(R.id.card_share_file_root);
                ivThumb = itemView.findViewById(R.id.iv_share_file_thumb);
                tvIcon = itemView.findViewById(R.id.tv_share_file_icon);
                tvName = itemView.findViewById(R.id.tv_share_file_name);
                tvSelected = itemView.findViewById(R.id.tv_share_file_selected);
            }
        }
    }

    /**
     * Nearby Device Adapter (for choosing receiver)
     */
    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

        @NonNull
        @Override
        public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearby_device, parent, false);
            return new DeviceViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
            DiscoveredDevice dev = nearbyDevices.get(position);
            holder.tvIcon.setText(dev.icon);
            holder.tvName.setText(dev.name);
            holder.tvStatus.setText(dev.isPaired ? "Paired Device • Tap to Send" : "Nearby Available • Tap to Send");

            holder.btnSend.setOnClickListener(v -> sendFilesToDevice(dev));
            holder.itemView.setOnClickListener(v -> sendFilesToDevice(dev));
        }

        @Override
        public int getItemCount() {
            return nearbyDevices.size();
        }

        class DeviceViewHolder extends RecyclerView.ViewHolder {
            TextView tvIcon;
            TextView tvName;
            TextView tvStatus;
            TextView btnSend;

            DeviceViewHolder(@NonNull View itemView) {
                super(itemView);
                tvIcon = itemView.findViewById(R.id.tv_device_icon);
                tvName = itemView.findViewById(R.id.tv_device_name);
                tvStatus = itemView.findViewById(R.id.tv_device_status);
                btnSend = itemView.findViewById(R.id.btn_device_send);
            }
        }
    }

    /**
     * Received Files Adapter (for Receive inbox)
     */
    private class ReceivedAdapter extends RecyclerView.Adapter<ReceivedAdapter.ReceivedViewHolder> {

        @NonNull
        @Override
        public ReceivedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_received_file, parent, false);
            return new ReceivedViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ReceivedViewHolder holder, int position) {
            File f = receivedFilesList.get(position);
            holder.tvName.setText(f.getName());

            String lower = f.getName().toLowerCase(Locale.US);
            if (lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav")) {
                holder.tvIcon.setText("🎵");
            } else if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".3gp")) {
                holder.tvIcon.setText("🎬");
            } else if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg")) {
                holder.tvIcon.setText("🖼️");
            } else {
                holder.tvIcon.setText("📄");
            }

            long size = f.length();
            String sizeStr = (size < 1024 * 1024) ? (size / 1024 + " KB") : (size / (1024 * 1024) + " MB");
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault());
            holder.tvMeta.setText(sizeStr + " • " + sdf.format(new Date(f.lastModified())));

            holder.btnOpen.setOnClickListener(v -> openReceivedFile(f));
            holder.itemView.setOnClickListener(v -> openReceivedFile(f));
        }

        @Override
        public int getItemCount() {
            return receivedFilesList.size();
        }

        class ReceivedViewHolder extends RecyclerView.ViewHolder {
            TextView tvIcon;
            TextView tvName;
            TextView tvMeta;
            TextView btnOpen;

            ReceivedViewHolder(@NonNull View itemView) {
                super(itemView);
                tvIcon = itemView.findViewById(R.id.tv_received_icon);
                tvName = itemView.findViewById(R.id.tv_received_name);
                tvMeta = itemView.findViewById(R.id.tv_received_meta);
                btnOpen = itemView.findViewById(R.id.btn_received_open);
            }
        }
    }
}
