package com.kids.launcher;

import android.graphics.drawable.Drawable;

public class AppModel {
    public static final String CAT_ALL = "ALL";
    public static final String CAT_GAMES = "GAMES";
    public static final String CAT_ENTERTAINMENT = "ENTERTAINMENT";
    public static final String CAT_TOOLS = "TOOLS";
    public static final String CAT_CREATIVE = "CREATIVE";
    public static final String CAT_MEDIA = "ENTERTAINMENT";
    public static final String CAT_LEARNING = "TOOLS";

    private final String label;
    private final String packageName;
    private final String activityName;
    private final Drawable icon;
    private boolean isAllowed;
    private String category;

    public AppModel(String label, String packageName, String activityName, Drawable icon, boolean isAllowed) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
        this.icon = icon;
        this.isAllowed = isAllowed;
        this.category = detectCategory(packageName, label);
    }

    public static String detectCategory(String pkg, String name) {
        String lower = (pkg + " " + name).toLowerCase();
        // Entertainment / Media
        if (lower.contains("youtube") || lower.contains("morphe") || lower.contains("video")
                || lower.contains("music") || lower.contains("audio") || lower.contains("song")
                || lower.contains("player") || lower.contains("tv") || lower.contains("media")) {
            return CAT_ENTERTAINMENT;
        }
        // Creative
        if (lower.contains("draw") || lower.contains("color") || lower.contains("paint") 
                || lower.contains("art") || lower.contains("photo") || lower.contains("gallery") 
                || lower.contains("camera") || lower.contains("sparkle")) {
            return CAT_CREATIVE;
        }
        // Tools & Utilities
        if (lower.contains("calc") || lower.contains("math") || lower.contains("clock") 
                || lower.contains("timer") || lower.contains("file") || lower.contains("battery")
                || lower.contains("share") || lower.contains("update") || lower.contains("setting")
                || lower.contains("browser") || lower.contains("tool") || lower.contains("boost")
                || lower.contains("clean") || lower.contains("note") || lower.contains("finder")) {
            return CAT_TOOLS;
        }
        // Everything else is Games
        return CAT_GAMES;
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getActivityName() {
        return activityName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isAllowed() {
        return isAllowed;
    }

    public void setAllowed(boolean allowed) {
        isAllowed = allowed;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean matchesCategory(String cat) {
        if (CAT_ALL.equals(cat)) return true;
        if (CAT_ENTERTAINMENT.equals(cat)) {
            return "ENTERTAINMENT".equals(category) || "MEDIA".equals(category);
        }
        if (CAT_TOOLS.equals(cat)) {
            return "TOOLS".equals(category) || "LEARNING".equals(category);
        }
        return category != null && category.equals(cat);
    }
}
