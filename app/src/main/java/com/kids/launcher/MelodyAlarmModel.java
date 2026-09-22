package com.kids.launcher;

import org.json.JSONException;
import org.json.JSONObject;

public class MelodyAlarmModel {
    private int id;
    private int hour;    // 0-23
    private int minute;  // 0-59
    private String label;
    private String sound;
    private boolean enabled;

    public MelodyAlarmModel(int id, int hour, int minute, String label, String sound, boolean enabled) {
        this.id = id;
        this.hour = hour;
        this.minute = minute;
        this.label = label;
        this.sound = sound;
        this.enabled = enabled;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getHour() { return hour; }
    public void setHour(int hour) { this.hour = hour; }

    public int getMinute() { return minute; }
    public void setMinute(int minute) { this.minute = minute; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getSound() { return sound; }
    public void setSound(String sound) { this.sound = sound; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("hour", hour);
            obj.put("minute", minute);
            obj.put("label", label);
            obj.put("sound", sound);
            obj.put("enabled", enabled);
        } catch (JSONException ignored) {}
        return obj;
    }

    public static MelodyAlarmModel fromJson(JSONObject obj) {
        if (obj == null) return null;
        int id = obj.optInt("id", (int) (System.currentTimeMillis() % 100000));
        int hour = obj.optInt("hour", 7);
        int minute = obj.optInt("minute", 0);
        String label = obj.optString("label", "Wake Up ☀️");
        String sound = obj.optString("sound", "🌸 Sweet Melody");
        boolean enabled = obj.optBoolean("enabled", false);
        return new MelodyAlarmModel(id, hour, minute, label, sound, enabled);
    }
}
