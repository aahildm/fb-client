package com.fbclient.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** Centralized app preferences */
public class AppPrefs {
    private static final String PREF_FILE = "fbclient_prefs";

    // Keys
    public static final String KEY_UA_MODE = "ua_mode"; // 0=mobile,1=desktop,2=fb-app
    public static final String KEY_DARK_MODE = "dark_mode"; // true/false
    public static final String KEY_ADBLOCK = "adblock_enabled";
    public static final String KEY_JS_ENABLED = "js_enabled";
    public static final String KEY_SAVE_COOKIES = "save_cookies";
    public static final String KEY_NOTIFICATIONS = "notifications_enabled";
    public static final String KEY_HOME_URL = "home_url";
    public static final String KEY_FONT_SIZE = "font_size"; // percent: 50-200

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public int getUaMode() { return prefs.getInt(KEY_UA_MODE, 0); }
    public void setUaMode(int mode) { prefs.edit().putInt(KEY_UA_MODE, mode).apply(); }

    public boolean isDarkMode() { return prefs.getBoolean(KEY_DARK_MODE, true); }
    public void setDarkMode(boolean v) { prefs.edit().putBoolean(KEY_DARK_MODE, v).apply(); }

    public boolean isAdblockEnabled() { return prefs.getBoolean(KEY_ADBLOCK, true); }
    public void setAdblockEnabled(boolean v) { prefs.edit().putBoolean(KEY_ADBLOCK, v).apply(); }

    public boolean isJsEnabled() { return prefs.getBoolean(KEY_JS_ENABLED, true); }
    public void setJsEnabled(boolean v) { prefs.edit().putBoolean(KEY_JS_ENABLED, v).apply(); }

    public boolean isSaveCookies() { return prefs.getBoolean(KEY_SAVE_COOKIES, true); }
    public void setSaveCookies(boolean v) { prefs.edit().putBoolean(KEY_SAVE_COOKIES, v).apply(); }

    public boolean isNotificationsEnabled() { return prefs.getBoolean(KEY_NOTIFICATIONS, true); }
    public void setNotificationsEnabled(boolean v) { prefs.edit().putBoolean(KEY_NOTIFICATIONS, v).apply(); }

    public String getHomeUrl() { return prefs.getString(KEY_HOME_URL, "https://www.facebook.com"); }
    public void setHomeUrl(String url) { prefs.edit().putString(KEY_HOME_URL, url).apply(); }

    public int getFontSize() { return prefs.getInt(KEY_FONT_SIZE, 100); }
    public void setFontSize(int size) { prefs.edit().putInt(KEY_FONT_SIZE, size).apply(); }

    public void clearAll() { prefs.edit().clear().apply(); }
}
