package com.kids.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
    public interface OnAppClickListener {
        void onAppClick(AppModel app);
    }

    private final Context context;
    private final List<AppModel> appList;
    private final OnAppClickListener listener;

    // Sweet My Melody macaron pastels that harmonize with the pink theme
    private static final int[] CARD_COLORS = new int[]{
            Color.parseColor("#FFF0F5"), // Lavender Blush / Strawberry Milk
            Color.parseColor("#FFE4E6"), // Misty Rose / Soft Petal
            Color.parseColor("#FCE7F3"), // Melody Pink
            Color.parseColor("#EDE9FE"), // Soft Lavender
            Color.parseColor("#E0F2FE"), // Pastel Sky
            Color.parseColor("#FEF3C7"), // Gentle Vanilla
            Color.parseColor("#F3E8FF"), // Sweet Lilac
            Color.parseColor("#DCFCE7"), // Mint Macaron
            Color.parseColor("#FFEDD5"), // Warm Peach
            Color.parseColor("#F1F5F9")  // Soft Marshmallow
    };

    public AppAdapter(Context context, List<AppModel> appList, OnAppClickListener listener) {
        this.context = context;
        this.appList = appList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_app_card, parent, false);

        // Dynamically size cards so exactly 5 columns fit horizontally without cutoff
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int horizontalPadding = (int) (32 * context.getResources().getDisplayMetrics().density);
        int columnWidth = Math.max(140, (screenWidth - horizontalPadding) / 5);

        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.width = columnWidth;
            view.setLayoutParams(lp);
        } else {
            view.setLayoutParams(new ViewGroup.LayoutParams(columnWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppModel app = appList.get(position);

        // Clean app name (remove trailing emoji symbols for pristine iWawa look)
        String rawLabel = app.getLabel();
        String cleanLabel = rawLabel.replaceAll("[\\p{So}\\p{Cn}]", "").trim();
        holder.tvAppName.setText(cleanLabel.isEmpty() ? rawLabel : cleanLabel);

        holder.ivAppIcon.setImageDrawable(app.getIcon());

        // Assign curated macaron pastel color tile matching My Melody theme
        int color = CARD_COLORS[Math.abs(position) % CARD_COLORS.length];
        holder.cardTile.setCardBackgroundColor(color);

        holder.itemView.setOnClickListener(v -> {
            // Tactile bouncy scale animation
            v.animate()
                    .scaleX(0.90f)
                    .scaleY(0.90f)
                    .setDuration(90)
                    .withEndAction(() -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                        if (listener != null) {
                            listener.onAppClick(app);
                        } else {
                            try {
                                Intent intent = new Intent(Intent.ACTION_MAIN);
                                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                                intent.setComponent(new ComponentName(app.getPackageName(), app.getActivityName()));
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                                context.startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(context, "Could not open " + app.getLabel(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .start();
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivAppIcon;
        final TextView tvAppName;
        final CardView cardTile;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.iv_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            cardTile = itemView.findViewById(R.id.cv_app_tile);
        }
    }
}
