package com.kids.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

public class ThemeManager {

    public static class ThemeColors {
        public final String name;
        public final int primary;
        public final int primaryDark;
        public final int accent;
        public final int cardBg;
        public final int chipSelected;
        public final int textPrimary;
        public final int textSecondary;
        public final int gradientStart;
        public final int gradientEnd;

        public ThemeColors(String name, int primary, int primaryDark, int accent, int cardBg,
                           int chipSelected, int textPrimary, int textSecondary,
                           int gradientStart, int gradientEnd) {
            this.name = name;
            this.primary = primary;
            this.primaryDark = primaryDark;
            this.accent = accent;
            this.cardBg = cardBg;
            this.chipSelected = chipSelected;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.gradientStart = gradientStart;
            this.gradientEnd = gradientEnd;
        }
    }

    public static ThemeColors getTheme(Context context) {
        PreferencesManager prefs = new PreferencesManager(context);
        return getThemeByName(prefs.getSelectedTheme());
    }

    public static ThemeColors getThemeByName(String themeKey) {
        if ("lavender".equalsIgnoreCase(themeKey)) {
            return new ThemeColors(
                    "Magical Lavender 💜",
                    Color.parseColor("#9D4EDD"),
                    Color.parseColor("#7B2CBF"),
                    Color.parseColor("#C77DFF"),
                    Color.parseColor("#F5F3FF"),
                    Color.parseColor("#9D4EDD"),
                    Color.parseColor("#3C096C"),
                    Color.parseColor("#5A189A"),
                    Color.parseColor("#FAF5FF"),
                    Color.parseColor("#E9D5FF")
            );
        } else if ("mint".equalsIgnoreCase(themeKey)) {
            return new ThemeColors(
                    "Fresh Mint 🍃",
                    Color.parseColor("#10B981"),
                    Color.parseColor("#059669"),
                    Color.parseColor("#34D399"),
                    Color.parseColor("#ECFDF5"),
                    Color.parseColor("#10B981"),
                    Color.parseColor("#064E3B"),
                    Color.parseColor("#047857"),
                    Color.parseColor("#F0FDF4"),
                    Color.parseColor("#D1FAE5")
            );
        } else if ("peach".equalsIgnoreCase(themeKey)) {
            return new ThemeColors(
                    "Juicy Peach 🍑",
                    Color.parseColor("#F97316"),
                    Color.parseColor("#EA580C"),
                    Color.parseColor("#FB923C"),
                    Color.parseColor("#FFF7ED"),
                    Color.parseColor("#F97316"),
                    Color.parseColor("#7C2D12"),
                    Color.parseColor("#9A3412"),
                    Color.parseColor("#FFFBEB"),
                    Color.parseColor("#FFEDD5")
            );
        } else {
            // Default Sweet Pink 🌸
            return new ThemeColors(
                    "Sweet Pink 🌸",
                    Color.parseColor("#FF4D8D"),
                    Color.parseColor("#E11D48"),
                    Color.parseColor("#FF85A2"),
                    Color.parseColor("#FFF0F5"),
                    Color.parseColor("#FF4D8D"),
                    Color.parseColor("#831843"),
                    Color.parseColor("#9D174D"),
                    Color.parseColor("#FFF1F2"),
                    Color.parseColor("#FFE4E6")
            );
        }
    }

    public static GradientDrawable createThemeBackground(ThemeColors theme) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{theme.gradientStart, theme.gradientEnd}
        );
        gd.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return gd;
    }
}
