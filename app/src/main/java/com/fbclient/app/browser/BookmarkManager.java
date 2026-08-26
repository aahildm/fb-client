package com.fbclient.app.browser;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Manages bookmarks stored in SharedPreferences */
public class BookmarkManager {
    private static final String PREF_FILE = "fbclient_bookmarks";
    private static final String KEY = "bookmarks";

    public static class Bookmark {
        public String url;
        public String title;
        public long createdAt;

        public Bookmark(String url, String title) {
            this.url = url;
            this.title = title;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public BookmarkManager(Context context) {
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public void add(String url, String title) {
        List<Bookmark> list = getAll();
        if (list.stream().noneMatch(b -> b.url.equals(url))) {
            list.add(0, new Bookmark(url, title));
            save(list);
        }
    }

    public void remove(String url) {
        List<Bookmark> list = getAll();
        list.removeIf(b -> b.url.equals(url));
        save(list);
    }

    public boolean isBookmarked(String url) {
        return getAll().stream().anyMatch(b -> b.url.equals(url));
    }

    public List<Bookmark> getAll() {
        String json = prefs.getString(KEY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<Bookmark>>() {}.getType();
        try {
            List<Bookmark> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void save(List<Bookmark> list) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply();
    }
}
