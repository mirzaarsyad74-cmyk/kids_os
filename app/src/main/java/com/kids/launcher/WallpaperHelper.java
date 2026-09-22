package com.kids.launcher;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WallpaperHelper {

    public static final String[] WALLPAPER_NAMES = {
            "🌸 Sweet Melody Pink",
            "💜 Lavender Starry Sky",
            "🍃 Minty Daisy Meadow",
            "🍑 Sunset Peach Dream",
            "🌈 Pastel Rainbow Dream"
    };

    public static Drawable getWallpaperDrawable(int index) {
        GradientDrawable gd;
        switch (index) {
            case 1:
                // Lavender Starry Sky
                gd = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{Color.parseColor("#E9D5FF"), Color.parseColor("#C084FC"), Color.parseColor("#7E22CE")}
                );
                break;
            case 2:
                // Minty Daisy Meadow
                gd = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{Color.parseColor("#D1FAE5"), Color.parseColor("#6EE7B7"), Color.parseColor("#059669")}
                );
                break;
            case 3:
                // Sunset Peach Dream
                gd = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{Color.parseColor("#FFEDD5"), Color.parseColor("#FDBA74"), Color.parseColor("#EA580C")}
                );
                break;
            case 4:
                // Pastel Rainbow Dream
                gd = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{Color.parseColor("#FBCFE8"), Color.parseColor("#FEF08A"), Color.parseColor("#BAE6FD")}
                );
                break;
            case 0:
            default:
                // Sweet Melody Pink
                gd = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{Color.parseColor("#FFF1F2"), Color.parseColor("#FECDD3"), Color.parseColor("#FB7185")}
                );
                break;
        }
        gd.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return gd;
    }

    public interface OnWallpaperSelectedListener {
        void onWallpaperSelected(int index);
    }

    public static void showWallpaperPicker(Context context, OnWallpaperSelectedListener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1B1336"));
        layout.setPadding(32, 28, 32, 28);

        TextView title = new TextView(context);
        title.setText("🖼️ Pick a Cute Wallpaper ✨");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        PreferencesManager prefs = new PreferencesManager(context);
        int current = prefs.getSelectedWallpaper();

        for (int i = 0; i < WALLPAPER_NAMES.length; i++) {
            final int wallpaperIdx = i;
            Button btn = new Button(context);
            btn.setText(WALLPAPER_NAMES[i]);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(13);
            if (i == current) {
                btn.setBackgroundColor(Color.parseColor("#FF4D8D"));
            } else {
                btn.setBackgroundColor(Color.parseColor("#2D1B69"));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 6, 0, 6);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                prefs.setSelectedWallpaper(wallpaperIdx);
                if (listener != null) {
                    listener.onWallpaperSelected(wallpaperIdx);
                }
                Toast.makeText(context, "Wallpaper applied! ✨", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            layout.addView(btn);
        }

        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }
}
