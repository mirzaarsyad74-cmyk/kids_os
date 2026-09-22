package com.kids.launcher;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Kid-Friendly File Manager for Kids Launcher.
 * Features:
 * - Clean visual explorer for internal tablet storage.
 * - Quick category tabs: All, Downloads, Pictures, Music, Videos, Documents.
 * - Folder navigation with breadcrumb and Up button.
 * - Integrated launch into MelodyGalleryActivity, MelodyMusicActivity, and MelodyVideoActivity.
 * - File options: Open, Share, Delete.
 * - Live search/filtering.
 */
public class MelodyFileManagerActivity extends AppCompatActivity {

    public static class FileItem {
        public final File file;
        public final boolean isDirectory;
        public final String name;
        public final long size;
        public final long lastModified;

        public FileItem(File file) {
            this.file = file;
            this.isDirectory = file.isDirectory();
            this.name = file.getName();
            this.size = isDirectory ? 0 : file.length();
            this.lastModified = file.lastModified();
        }

        public String getFormattedSize() {
            if (isDirectory) {
                File[] children = file.listFiles();
                int count = (children != null) ? children.length : 0;
                return count + " item" + (count == 1 ? "" : "s");
            }
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
            return String.format(Locale.getDefault(), "%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }

        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return sdf.format(new Date(lastModified));
        }

        public String getIconEmoji() {
            if (isDirectory) return "📁";
            String lower = name.toLowerCase(Locale.US);
            if (lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".flac")) return "🎵";
            if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".3gp") || lower.endsWith(".avi")) return "🎬";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")) return "🖼️";
            if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt")) return "📄";
            if (lower.endsWith(".apk")) return "📦";
            if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "🗜️";
            return "📄";
        }
    }

    private File currentDirectory;
    private final File rootDirectory = Environment.getExternalStorageDirectory();

    private TextView tvCurrentPath;
    private TextView tvItemsCount;
    private TextView tvStorageSummary;
    private TextView btnFolderUp;
    private EditText etSearchFiles;
    private View layoutEmptyFolder;
    private RecyclerView rvFilesList;
    private FileAdapter fileAdapter;

    private TextView chipAll, chipDownloads, chipPictures, chipMusic, chipVideos, chipDocs;

    private final List<FileItem> allCurrentFiles = new ArrayList<>();
    private final List<FileItem> displayedFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DeviceBooster.boost(this);
        setWindowUiFlags();
        setContentView(R.layout.activity_melody_file_manager);

        initViews();
        setupCategoryChips();
        setupSearch();
        updateStorageSummary();

        navigateTo(rootDirectory);
    }

    private void setWindowUiFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void initViews() {
        tvCurrentPath = findViewById(R.id.tv_current_path);
        tvItemsCount = findViewById(R.id.tv_items_count);
        tvStorageSummary = findViewById(R.id.tv_storage_summary);
        btnFolderUp = findViewById(R.id.btn_folder_up);
        etSearchFiles = findViewById(R.id.et_search_files);
        layoutEmptyFolder = findViewById(R.id.layout_empty_folder);
        rvFilesList = findViewById(R.id.rv_files_list);

        findViewById(R.id.btn_files_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_files_refresh).setOnClickListener(v -> {
            loadDirectory(currentDirectory);
            updateStorageSummary();
            Toast.makeText(this, "Refreshed 🌸", Toast.LENGTH_SHORT).show();
        });

        btnFolderUp.setOnClickListener(v -> {
            if (currentDirectory != null && !currentDirectory.equals(rootDirectory)) {
                File parent = currentDirectory.getParentFile();
                if (parent != null && parent.canRead()) {
                    navigateTo(parent);
                }
            }
        });

        rvFilesList.setLayoutManager(new LinearLayoutManager(this));
        fileAdapter = new FileAdapter();
        rvFilesList.setAdapter(fileAdapter);
    }

    private void setupCategoryChips() {
        chipAll = findViewById(R.id.chip_cat_all);
        chipDownloads = findViewById(R.id.chip_cat_downloads);
        chipPictures = findViewById(R.id.chip_cat_pictures);
        chipMusic = findViewById(R.id.chip_cat_music);
        chipVideos = findViewById(R.id.chip_cat_videos);
        chipDocs = findViewById(R.id.chip_cat_docs);

        chipAll.setOnClickListener(v -> {
            highlightChip(chipAll);
            navigateTo(rootDirectory);
        });

        chipDownloads.setOnClickListener(v -> {
            highlightChip(chipDownloads);
            navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        });

        chipPictures.setOnClickListener(v -> {
            highlightChip(chipPictures);
            File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File pics = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            navigateTo(pics != null && pics.exists() ? pics : dcim);
        });

        chipMusic.setOnClickListener(v -> {
            highlightChip(chipMusic);
            navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));
        });

        chipVideos.setOnClickListener(v -> {
            highlightChip(chipVideos);
            navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES));
        });

        chipDocs.setOnClickListener(v -> {
            highlightChip(chipDocs);
            navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS));
        });
    }

    private void highlightChip(TextView selected) {
        TextView[] chips = {chipAll, chipDownloads, chipPictures, chipMusic, chipVideos, chipDocs};
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

    private void setupSearch() {
        etSearchFiles.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFiles(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void navigateTo(File dir) {
        if (dir == null || !dir.exists()) {
            dir = rootDirectory;
        }
        currentDirectory = dir;
        tvCurrentPath.setText(dir.getAbsolutePath());

        boolean canGoUp = !currentDirectory.equals(rootDirectory) && currentDirectory.getParentFile() != null;
        btnFolderUp.setVisibility(canGoUp ? View.VISIBLE : View.GONE);

        loadDirectory(currentDirectory);
    }

    private void loadDirectory(File dir) {
        allCurrentFiles.clear();
        if (dir != null && dir.exists() && dir.canRead()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().startsWith(".")) {
                        allCurrentFiles.add(new FileItem(f));
                    }
                }
                // Sort: folders first, then files alphabetically
                Collections.sort(allCurrentFiles, (a, b) -> {
                    if (a.isDirectory && !b.isDirectory) return -1;
                    if (!a.isDirectory && b.isDirectory) return 1;
                    return a.name.compareToIgnoreCase(b.name);
                });
            }
        }
        filterFiles(etSearchFiles.getText().toString());
    }

    private void filterFiles(String query) {
        displayedFiles.clear();
        String q = (query == null) ? "" : query.trim().toLowerCase();

        if (q.isEmpty()) {
            displayedFiles.addAll(allCurrentFiles);
        } else {
            for (FileItem item : allCurrentFiles) {
                if (item.name.toLowerCase().contains(q)) {
                    displayedFiles.add(item);
                }
            }
        }

        tvItemsCount.setText(displayedFiles.size() + " items");

        if (displayedFiles.isEmpty()) {
            layoutEmptyFolder.setVisibility(View.VISIBLE);
            rvFilesList.setVisibility(View.GONE);
        } else {
            layoutEmptyFolder.setVisibility(View.GONE);
            rvFilesList.setVisibility(View.VISIBLE);
        }

        if (fileAdapter != null) {
            fileAdapter.notifyDataSetChanged();
        }
    }

    private void updateStorageSummary() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();

            long totalBytes = totalBlocks * blockSize;
            long freeBytes = availableBlocks * blockSize;
            long usedBytes = totalBytes - freeBytes;

            double totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0);
            double freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0);
            double usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0);

            tvStorageSummary.setText(String.format(Locale.getDefault(),
                    "💾 Storage: %.1f GB Free of %.1f GB (%.1f GB used) 🌸", freeGb, totalGb, usedGb));
        } catch (Exception e) {
            tvStorageSummary.setText("💾 Internal Storage 🌸");
        }
    }

    private void openFile(FileItem item) {
        if (item.isDirectory) {
            navigateTo(item.file);
            return;
        }

        String lower = item.name.toLowerCase(Locale.US);

        // Music files -> MelodyMusicActivity
        if (lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".flac")) {
            Intent intent = new Intent(this, MelodyMusicActivity.class);
            startActivity(intent);
            return;
        }

        // Video files -> MelodyVideoActivity
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".3gp") || lower.endsWith(".avi")) {
            Intent intent = new Intent(this, MelodyVideoActivity.class);
            intent.putExtra("target_video_path", item.file.getAbsolutePath());
            startActivity(intent);
            return;
        }

        // Image files -> MelodyGalleryActivity
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            Intent intent = new Intent(this, MelodyGalleryActivity.class);
            startActivity(intent);
            return;
        }

        // Generic Intent viewer
        try {
            Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", item.file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, getMimeType(item.file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open this file type directly", Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "*/*";
    }

    private void showFileMenu(View anchor, FileItem item) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "📂 Open");
        popup.getMenu().add(0, 2, 1, "📤 Share");
        popup.getMenu().add(0, 3, 2, "🗑️ Delete");

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == 1) {
                openFile(item);
                return true;
            } else if (id == 2) {
                shareFile(item);
                return true;
            } else if (id == 3) {
                confirmDelete(item);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void shareFile(FileItem item) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", item.file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(getMimeType(item.file));
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share " + item.name));
        } catch (Exception e) {
            Toast.makeText(this, "Could not share file", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(FileItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + (item.isDirectory ? "Folder" : "File") + "?")
                .setMessage("Are you sure you want to delete '" + item.name + "'? 🗑️")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean deleted = deleteRecursive(item.file);
                    if (deleted) {
                        Toast.makeText(this, "Deleted: " + item.name + " 🗑️", Toast.LENGTH_SHORT).show();
                        loadDirectory(currentDirectory);
                        updateStorageSummary();
                    } else {
                        Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return f.delete();
    }

    @Override
    public void onBackPressed() {
        if (currentDirectory != null && !currentDirectory.equals(rootDirectory)) {
            File parent = currentDirectory.getParentFile();
            if (parent != null && parent.canRead()) {
                navigateTo(parent);
                return;
            }
        }
        super.onBackPressed();
    }

    /**
     * RecyclerView Adapter for File Items
     */
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_entry, parent, false);
            return new FileViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            FileItem item = displayedFiles.get(position);
            holder.tvIcon.setText(item.getIconEmoji());
            holder.tvName.setText(item.name);
            holder.tvDetails.setText(item.getFormattedSize() + " • " + item.getFormattedDate());

            holder.itemView.setOnClickListener(v -> openFile(item));
            holder.btnAction.setOnClickListener(v -> showFileMenu(holder.btnAction, item));
        }

        @Override
        public int getItemCount() {
            return displayedFiles.size();
        }

        class FileViewHolder extends RecyclerView.ViewHolder {
            TextView tvIcon;
            TextView tvName;
            TextView tvDetails;
            TextView btnAction;

            FileViewHolder(@NonNull View itemView) {
                super(itemView);
                tvIcon = itemView.findViewById(R.id.tv_file_icon);
                tvName = itemView.findViewById(R.id.tv_file_name);
                tvDetails = itemView.findViewById(R.id.tv_file_details);
                btnAction = itemView.findViewById(R.id.btn_file_action);
            }
        }
    }
}
