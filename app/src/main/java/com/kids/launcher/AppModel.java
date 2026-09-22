package com.kids.launcher;

import android.graphics.drawable.Drawable;

public class AppModel {
    public static final String CAT_ALL = "ALL";
    public static final String CAT_GAMES = "GAMES";
    public static final String CAT_CREATIVE = "CREATIVE";
    public static final String CAT_MEDIA = "MEDIA";
    public static final String CAT_LEARNING = "LEARNING";

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
        if (lower.contains("game") || lower.contains("doodle") 
                || lower.contains("jump") || lower.contains("play") || lower.contains("fly") || lower.contains("turbo")) {
            return CAT_GAMES;
        } else if (lower.contains("draw") || lower.contains("color") || lower.contains("paint") || lower.contains("art") || lower.contains("photo") || lower.contains("gallery")) {
            return CAT_CREATIVE;
        } else if (lower.contains("piano") || lower.contains("music") || lower.contains("yt") || lower.contains("youtube") || lower.contains("sound") || lower.contains("audio")) {
            return CAT_MEDIA;
        } else if (lower.contains("calc") || lower.contains("math") || lower.contains("clock") || lower.contains("type") || lower.contains("typing") || lower.contains("tux") || lower.contains("gcompris")) {
            return CAT_LEARNING;
        }
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
        return category.equals(cat);
    }
}
