package com.luoyilin.focusguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class FocusTaskStore {
    static final String COUNTDOWN = "countdown";
    static final String COUNTUP = "countup";
    static final String POMODORO = "pomodoro";
    private final SharedPreferences prefs;

    FocusTaskStore(Context context) {
        prefs = context.getSharedPreferences("focus_tasks", Context.MODE_PRIVATE);
    }

    static final class Task {
        final String id;
        final String name;
        final String mode;
        final int value;
        Task(String id, String name, String mode, int value) {
            this.id = id; this.name = name; this.mode = mode; this.value = value;
        }
        String detail() {
            if (COUNTUP.equals(mode)) return "正计时";
            if (POMODORO.equals(mode)) return "番茄钟 · " + value + " 轮";
            return "倒计时 · " + value + " 分钟";
        }
    }

    List<Task> all() {
        List<Task> tasks = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(prefs.getString("tasks", "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String mode = item.getString("mode");
                int value = item.getInt("value");
                if ((COUNTUP.equals(mode) && value == 0)
                        || (COUNTDOWN.equals(mode) && value >= 1 && value <= 720)
                        || (POMODORO.equals(mode) && value >= 1 && value <= 24)) {
                    tasks.add(new Task(item.getString("id"), item.getString("name"), mode, value));
                }
            }
        } catch (JSONException error) { android.util.Log.e("FocusTasks", "Cannot read tasks", error); }
        return tasks;
    }

    Task add(String name, String mode, int value) {
        Task task = new Task(UUID.randomUUID().toString(), name.trim(), mode, value);
        try {
            JSONArray items = new JSONArray(prefs.getString("tasks", "[]"));
            JSONObject item = new JSONObject();
            item.put("id", task.id); item.put("name", task.name);
            item.put("mode", task.mode); item.put("value", task.value);
            items.put(item);
            return prefs.edit().putString("tasks", items.toString()).commit() ? task : null;
        } catch (JSONException error) { return null; }
    }

    boolean update(Task task) {
        JSONArray changed = new JSONArray();
        try {
            JSONArray items = new JSONArray(prefs.getString("tasks", "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (task.id.equals(item.getString("id"))) {
                    item.put("name", task.name); item.put("mode", task.mode); item.put("value", task.value);
                }
                changed.put(item);
            }
            return prefs.edit().putString("tasks", changed.toString()).commit();
        } catch (JSONException error) { return false; }
    }

    boolean delete(String id) {
        JSONArray remaining = new JSONArray();
        try {
            JSONArray items = new JSONArray(prefs.getString("tasks", "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (!id.equals(item.getString("id"))) remaining.put(item);
            }
            return prefs.edit().putString("tasks", remaining.toString()).commit();
        } catch (JSONException error) { return false; }
    }
}
