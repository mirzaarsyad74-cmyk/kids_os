package com.kids.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Melody Gallery with Multi-Select (Hold to select, Select All, and Action to Delete).
 */
public class MelodyGalleryActivity extends AppCompatActivity {

    private RecyclerView recyclerGallery;
    private GalleryAdapter adapter;
    private final List<PhotoItem> photoList = new ArrayList<>();
    private final Set<String> selectedPaths = new HashSet<>();
    private boolean isSelectionMode = false;

    private RelativeLayout layoutNormalHeader;
    private RelativeLayout layoutSelectionHeader;
    private TextView txtSelectedCount;
    private TextView btnSelectAll;
    private TextView btnDeleteSelected;

    private LinearLayout layoutEmptyState;
    private TextView txtPhotoCount;
    private FrameLayout overlayPreview;
    private ImageView imgFullPreview;
    private TextView txtPreviewTitle;
    private PhotoItem currentPreviewItem = null;

    public static class PhotoItem implements Comparable<PhotoItem> {
        public final String path;
        public final long timestamp;
        public final String dateLabel;

        public PhotoItem(String path, long timestamp) {
            this.path = path;
            this.timestamp = timestamp;
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
            this.dateLabel = sdf.format(new Date(timestamp));
        }

        @Override
        public int compareTo(PhotoItem other) {
            // Newest first
            return Long.compare(other.timestamp, this.timestamp);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_melody_gallery);

        // Keep system navigation bar visible
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        layoutNormalHeader = findViewById(R.id.layoutNormalHeader);
        layoutSelectionHeader = findViewById(R.id.layoutSelectionHeader);
        txtSelectedCount = findViewById(R.id.txtSelectedCount);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected);

        recyclerGallery = findViewById(R.id.recyclerGallery);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        txtPhotoCount = findViewById(R.id.txtPhotoCount);
        overlayPreview = findViewById(R.id.overlayPreview);
        imgFullPreview = findViewById(R.id.imgFullPreview);
        txtPreviewTitle = findViewById(R.id.txtPreviewTitle);

        View btnBack = findViewById(R.id.btnBack);
        View btnLaunchCamera = findViewById(R.id.btnLaunchCamera);
        View btnEmptyCamera = findViewById(R.id.btnEmptyCamera);
        View btnClosePreview = findViewById(R.id.btnClosePreview);
        View btnDeletePhoto = findViewById(R.id.btnDeletePhoto);
        View btnCancelSelection = findViewById(R.id.btnCancelSelection);

        btnBack.setOnClickListener(v -> finish());

        View.OnClickListener cameraOpener = v -> {
            Intent intent = new Intent(MelodyGalleryActivity.this, MelodyCameraActivity.class);
            startActivity(intent);
        };
        btnLaunchCamera.setOnClickListener(cameraOpener);
        btnEmptyCamera.setOnClickListener(cameraOpener);

        btnClosePreview.setOnClickListener(v -> closePreview());
        overlayPreview.setOnClickListener(v -> closePreview());

        btnDeletePhoto.setOnClickListener(v -> {
            if (currentPreviewItem != null) {
                confirmDeletePhoto(currentPreviewItem);
            }
        });

        // Selection actions
        btnCancelSelection.setOnClickListener(v -> exitSelectionMode());
        btnSelectAll.setOnClickListener(v -> toggleSelectAll());
        btnDeleteSelected.setOnClickListener(v -> confirmDeleteSelected());

        recyclerGallery.setLayoutManager(new GridLayoutManager(this, 4));
        adapter = new GalleryAdapter(this, photoList, selectedPaths, new GalleryAdapter.GalleryItemListener() {
            @Override
            public void onPhotoClick(PhotoItem item) {
                if (isSelectionMode) {
                    toggleSelection(item);
                } else {
                    openPreview(item);
                }
            }

            @Override
            public void onPhotoLongClick(PhotoItem item) {
                if (!isSelectionMode) {
                    enterSelectionMode(item);
                } else {
                    toggleSelection(item);
                }
            }
        });
        recyclerGallery.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPhotos();
    }

    private void enterSelectionMode(PhotoItem initialItem) {
        isSelectionMode = true;
        selectedPaths.clear();
        if (initialItem != null) {
            selectedPaths.add(initialItem.path);
        }

        layoutNormalHeader.setVisibility(View.GONE);
        layoutSelectionHeader.setVisibility(View.VISIBLE);
        updateSelectionUi();
        adapter.setSelectionMode(true);
        adapter.notifyDataSetChanged();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedPaths.clear();

        layoutSelectionHeader.setVisibility(View.GONE);
        layoutNormalHeader.setVisibility(View.VISIBLE);
        adapter.setSelectionMode(false);
        adapter.notifyDataSetChanged();
    }

    private void toggleSelection(PhotoItem item) {
        if (selectedPaths.contains(item.path)) {
            selectedPaths.remove(item.path);
        } else {
            selectedPaths.add(item.path);
        }

        if (selectedPaths.isEmpty()) {
            exitSelectionMode();
        } else {
            updateSelectionUi();
            adapter.notifyDataSetChanged();
        }
    }

    private void toggleSelectAll() {
        if (selectedPaths.size() == photoList.size()) {
            selectedPaths.clear();
            exitSelectionMode();
        } else {
            selectedPaths.clear();
            for (PhotoItem p : photoList) {
                selectedPaths.add(p.path);
            }
            updateSelectionUi();
            adapter.notifyDataSetChanged();
        }
    }

    private void updateSelectionUi() {
        int count = selectedPaths.size();
        txtSelectedCount.setText(count + " Selected");
        btnSelectAll.setText(count == photoList.size() && !photoList.isEmpty() ? "Deselect All" : "Select All");
        btnDeleteSelected.setText("🗑️ Delete (" + count + ")");
    }

    private void confirmDeleteSelected() {
        if (selectedPaths.isEmpty()) return;

        int count = selectedPaths.size();
        new AlertDialog.Builder(this)
                .setTitle("Delete " + count + " Photo" + (count > 1 ? "s" : "") + " 🗑️")
                .setMessage("Are you sure you want to delete the selected " + count + " cute photo(s)?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteSelectedPhotos();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSelectedPhotos() {
        int deletedCount = 0;
        for (String path : selectedPaths) {
            try {
                File file = new File(path);
                if (file.exists() && file.delete()) {
                    deletedCount++;
                }
                // MediaStore deletion
                try {
                    getContentResolver().delete(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            MediaStore.Images.Media.DATA + "=?",
                            new String[]{path}
                    );
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        }

        Toast.makeText(this, "Deleted " + deletedCount + " photo(s) 🌸", Toast.LENGTH_SHORT).show();
        exitSelectionMode();
        loadPhotos();
    }

    private void loadPhotos() {
        new AsyncTask<Void, Void, List<PhotoItem>>() {
            @Override
            protected List<PhotoItem> doInBackground(Void... voids) {
                List<PhotoItem> list = new ArrayList<>();
                Set<String> seenPaths = new HashSet<>();

                // 1. Scan Melody Camera dedicated directory
                File melodyDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MelodyCamera");
                if (melodyDir.exists() && melodyDir.isDirectory()) {
                    File[] files = melodyDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile() && isImageFile(f.getName())) {
                                list.add(new PhotoItem(f.getAbsolutePath(), f.lastModified()));
                                seenPaths.add(f.getAbsolutePath());
                            }
                        }
                    }
                }

                // 2. Scan DCIM/Camera directory
                File dcimDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
                if (dcimDir.exists() && dcimDir.isDirectory()) {
                    File[] files = dcimDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile() && isImageFile(f.getName()) && !seenPaths.contains(f.getAbsolutePath())) {
                                list.add(new PhotoItem(f.getAbsolutePath(), f.lastModified()));
                                seenPaths.add(f.getAbsolutePath());
                            }
                        }
                    }
                }

                // 3. Query MediaStore
                try {
                    String[] projection = {
                            MediaStore.Images.Media.DATA,
                            MediaStore.Images.Media.DATE_MODIFIED
                    };
                    Cursor cursor = getContentResolver().query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            null,
                            null,
                            MediaStore.Images.Media.DATE_MODIFIED + " DESC"
                    );

                    if (cursor != null) {
                        int dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                        int dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
                        while (cursor.moveToNext()) {
                            String p = cursor.getString(dataIdx);
                            long dateSec = cursor.getLong(dateIdx);
                            if (p != null && !seenPaths.contains(p)) {
                                File f = new File(p);
                                if (f.exists() && f.length() > 0) {
                                    list.add(new PhotoItem(p, dateSec * 1000L));
                                    seenPaths.add(p);
                                }
                            }
                        }
                        cursor.close();
                    }
                } catch (Exception ignored) {}

                Collections.sort(list);
                return list;
            }

            @Override
            protected void onPostExecute(List<PhotoItem> items) {
                photoList.clear();
                photoList.addAll(items);
                adapter.notifyDataSetChanged();

                if (photoList.isEmpty()) {
                    layoutEmptyState.setVisibility(View.VISIBLE);
                    recyclerGallery.setVisibility(View.GONE);
                    txtPhotoCount.setText("0 Photos");
                } else {
                    layoutEmptyState.setVisibility(View.GONE);
                    recyclerGallery.setVisibility(View.VISIBLE);
                    txtPhotoCount.setText(photoList.size() + " Photos");
                }
            }
        }.execute();
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private void openPreview(PhotoItem item) {
        currentPreviewItem = item;
        txtPreviewTitle.setText(item.dateLabel);

        Bitmap bitmap = decodeSampledBitmap(item.path, 1280, 800);
        if (bitmap != null) {
            imgFullPreview.setImageBitmap(bitmap);
        } else {
            imgFullPreview.setImageResource(R.drawable.ic_melody_gallery);
        }
        overlayPreview.setVisibility(View.VISIBLE);
    }

    private void closePreview() {
        overlayPreview.setVisibility(View.GONE);
        imgFullPreview.setImageDrawable(null);
        currentPreviewItem = null;
    }

    private void confirmDeletePhoto(PhotoItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Photo 🗑️")
                .setMessage("Are you sure you want to delete this cute photo?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        File file = new File(item.path);
                        if (file.exists() && file.delete()) {
                            Toast.makeText(this, "Photo deleted 🌸", Toast.LENGTH_SHORT).show();
                        }
                        try {
                            getContentResolver().delete(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    MediaStore.Images.Media.DATA + "=?",
                                    new String[]{item.path}
                            );
                        } catch (Exception ignored) {}
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not delete", Toast.LENGTH_SHORT).show();
                    }
                    closePreview();
                    loadPhotos();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (overlayPreview.getVisibility() == View.VISIBLE) {
            closePreview();
        } else if (isSelectionMode) {
            exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    public static Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, options);
        } catch (Exception e) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    // --- Gallery Adapter ---
    private static class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {
        private final Context context;
        private final List<PhotoItem> items;
        private final Set<String> selectedPaths;
        private final GalleryItemListener listener;
        private boolean isSelectionMode = false;

        interface GalleryItemListener {
            void onPhotoClick(PhotoItem item);
            void onPhotoLongClick(PhotoItem item);
        }

        public GalleryAdapter(Context context, List<PhotoItem> items, Set<String> selectedPaths, GalleryItemListener listener) {
            this.context = context;
            this.items = items;
            this.selectedPaths = selectedPaths;
            this.listener = listener;
        }

        public void setSelectionMode(boolean selectionMode) {
            this.isSelectionMode = selectionMode;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_gallery_photo, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PhotoItem item = items.get(position);
            holder.txtDate.setText(item.dateLabel);

            boolean isSelected = selectedPaths.contains(item.path);

            if (isSelectionMode) {
                holder.viewSelectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                holder.tvSelectionCheck.setVisibility(View.VISIBLE);
                holder.tvSelectionCheck.setText(isSelected ? "✓" : "");
                holder.tvSelectionCheck.setBackgroundResource(
                        isSelected ? R.drawable.bg_melody_chip_selected : R.drawable.bg_melody_pill
                );
            } else {
                holder.viewSelectionOverlay.setVisibility(View.GONE);
                holder.tvSelectionCheck.setVisibility(View.GONE);
            }

            // Load thumbnail async
            holder.imgThumbnail.setTag(item.path);
            new AsyncTask<Void, Void, Bitmap>() {
                @Override
                protected Bitmap doInBackground(Void... voids) {
                    return decodeSampledBitmap(item.path, 200, 200);
                }

                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    if (item.path.equals(holder.imgThumbnail.getTag())) {
                        if (bitmap != null) {
                            holder.imgThumbnail.setImageBitmap(bitmap);
                        } else {
                            holder.imgThumbnail.setImageResource(R.drawable.ic_melody_gallery);
                        }
                    }
                }
            }.execute();

            holder.itemView.setOnClickListener(v -> listener.onPhotoClick(item));
            holder.itemView.setOnLongClickListener(v -> {
                listener.onPhotoLongClick(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgThumbnail;
            TextView txtDate;
            View viewSelectionOverlay;
            TextView tvSelectionCheck;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
                txtDate = itemView.findViewById(R.id.txtPhotoDate);
                viewSelectionOverlay = itemView.findViewById(R.id.viewSelectionOverlay);
                tvSelectionCheck = itemView.findViewById(R.id.tvSelectionCheck);
            }
        }
    }
}
