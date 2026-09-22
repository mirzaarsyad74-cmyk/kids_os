package com.kids.launcher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.List;

public class ParentAppAdapter extends RecyclerView.Adapter<ParentAppAdapter.ViewHolder> {
    public interface OnAppToggleListener {
        void onToggle(AppModel app, boolean isAllowed);
    }

    private final Context context;
    private final List<AppModel> appList;
    private final OnAppToggleListener listener;
    private final PreferencesManager prefs;

    public ParentAppAdapter(Context context, List<AppModel> appList, OnAppToggleListener listener) {
        this.context = context;
        this.appList = appList;
        this.listener = listener;
        this.prefs = new PreferencesManager(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_parent_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppModel app = appList.get(position);
        holder.tvName.setText(app.getLabel());
        holder.tvPackage.setText(app.getPackageName());
        holder.ivIcon.setImageDrawable(app.getIcon());

        updateCategoryBadgeText(holder.tvCategoryBadge, app.getCategory());

        holder.tvCategoryBadge.setOnClickListener(v -> showCategoryPickerDialog(app, holder));

        boolean isLocked = prefs.isPackageLocked(app.getPackageName());
        updateLockBadge(holder.btnLockApp, isLocked);
        holder.btnLockApp.setOnClickListener(v -> {
            boolean newLock = !prefs.isPackageLocked(app.getPackageName());
            prefs.setPackageLocked(app.getPackageName(), newLock);
            updateLockBadge(holder.btnLockApp, newLock);
        });

        // Uninstall button
        if (app.getPackageName().equals(context.getPackageName())) {
            holder.btnUninstallApp.setVisibility(View.GONE);
        } else {
            holder.btnUninstallApp.setVisibility(View.VISIBLE);
            holder.btnUninstallApp.setOnClickListener(v -> promptUninstallApp(app));
        }

        // Avoid triggering listener when binding
        holder.switchAllow.setOnCheckedChangeListener(null);
        holder.switchAllow.setChecked(app.isAllowed());

        holder.switchAllow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.setAllowed(isChecked);
            if (listener != null) {
                listener.onToggle(app, isChecked);
            }
        });

        holder.itemView.setOnClickListener(v -> holder.switchAllow.toggle());
    }

    private void promptUninstallApp(AppModel app) {
        new AlertDialog.Builder(context)
                .setTitle("Uninstall " + app.getLabel() + "?")
                .setMessage("Are you sure you want to remove and uninstall " + app.getLabel() + " (" + app.getPackageName() + ") from this tablet?")
                .setIcon(app.getIcon())
                .setPositiveButton("🗑️ Uninstall", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_DELETE);
                        intent.setData(Uri.parse("package:" + app.getPackageName()));
                        context.startActivity(intent);
                    } catch (Exception e) {
                        try {
                            Intent fallback = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                            fallback.setData(Uri.parse("package:" + app.getPackageName()));
                            fallback.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                            context.startActivity(fallback);
                        } catch (Exception ex) {
                            Toast.makeText(context, "Could not open system uninstaller: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateLockBadge(TextView btn, boolean locked) {
        if (btn == null) return;
        if (locked) {
            btn.setText("🔒 Locked");
            btn.setBackgroundResource(R.drawable.bg_melody_chip_selected);
            btn.setTextColor(android.graphics.Color.WHITE);
        } else {
            btn.setText("🔓 Free");
            btn.setBackgroundResource(R.drawable.bg_melody_chip_unselected);
            btn.setTextColor(android.graphics.Color.parseColor("#9D174D"));
        }
    }

    private void updateCategoryBadgeText(TextView tv, String cat) {
        if (AppModel.CAT_GAMES.equals(cat)) {
            tv.setText("🎮 Games ▾");
        } else if (AppModel.CAT_CREATIVE.equals(cat)) {
            tv.setText("🎨 Creative ▾");
        } else if (AppModel.CAT_MEDIA.equals(cat)) {
            tv.setText("🎵 Media ▾");
        } else if (AppModel.CAT_LEARNING.equals(cat)) {
            tv.setText("📚 Learning ▾");
        } else {
            tv.setText("🎮 Games ▾");
        }
    }

    private void showCategoryPickerDialog(AppModel app, ViewHolder holder) {
        final String[] categories = {"🎮 Games", "🎨 Art & Creative", "🎵 Music & Media", "📚 Learn & Study"};
        final String[] catKeys = {AppModel.CAT_GAMES, AppModel.CAT_CREATIVE, AppModel.CAT_MEDIA, AppModel.CAT_LEARNING};

        int selectedIdx = 0;
        for (int i = 0; i < catKeys.length; i++) {
            if (catKeys[i].equals(app.getCategory())) {
                selectedIdx = i;
                break;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle("Select Category for " + app.getLabel())
                .setSingleChoiceItems(categories, selectedIdx, (dialog, which) -> {
                    String chosenCat = catKeys[which];
                    app.setCategory(chosenCat);
                    prefs.setAppCategory(app.getPackageName(), chosenCat);
                    updateCategoryBadgeText(holder.tvCategoryBadge, chosenCat);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvName;
        final TextView tvPackage;
        final TextView tvCategoryBadge;
        final TextView btnLockApp;
        final TextView btnUninstallApp;
        final SwitchCompat switchAllow;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_parent_app_icon);
            tvName = itemView.findViewById(R.id.tv_parent_app_name);
            tvPackage = itemView.findViewById(R.id.tv_parent_package_name);
            tvCategoryBadge = itemView.findViewById(R.id.tv_parent_app_category_badge);
            btnLockApp = itemView.findViewById(R.id.btn_lock_app);
            btnUninstallApp = itemView.findViewById(R.id.btn_uninstall_app);
            switchAllow = itemView.findViewById(R.id.switch_allow_app);
        }
    }
}
