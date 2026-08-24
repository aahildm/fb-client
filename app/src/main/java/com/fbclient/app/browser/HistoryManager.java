package com.fbclient.app.browser;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Simple browsing history backed by SharedPreferences */
public class HistoryManager {
    private static final String PREF_FILE = "fbclient_history";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_ENTRIES = 200;

    public static class HistoryEntry {
        public String url;
        public String title;
        public long timestamp;

        public HistoryEntry(String url, String title, long timestamp) {
            this.url = url;
            this.title = title;
            this.timestamp = timestamp;
        }
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public HistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public void addEntry(String url, String title) {
        List<HistoryEntry> history = getHistory();
        // Remove duplicate
        history.removeIf(e -> e.url.equals(url));
        history.add(0, new HistoryEntry(url, title, System.currentTimeMillis()));
        // Trim
        if (history.size() > MAX_ENTRIES) {
            history = history.subList(0, MAX_ENTRIES);
        }
        prefs.edit().putString(KEY_HISTORY, gson.toJson(history)).apply();
    }

    public List<HistoryEntry> getHistory() {
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<HistoryEntry>>() {}.getType();
        try {
            List<HistoryEntry> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }
}
