package com.fbclient.app.features;

import android.content.Context;
import android.content.SharedPreferences;

public class AppLock {
    private static final String PREF = "app_lock";
    private static final String KEY_ENABLED = "lock_enabled";
    private static final String KEY_PIN = "lock_pin";
    private final SharedPreferences prefs;

    public AppLock(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() { return prefs.getBoolean(KEY_ENABLED, false); }
    public String getPin() { return prefs.getString(KEY_PIN, ""); }

    public void setPin(String pin) {
        prefs.edit().putString(KEY_PIN, pin).putBoolean(KEY_ENABLED, !pin.isEmpty()).apply();
    }

    public void disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).remove(KEY_PIN).apply();
    }

    public boolean checkPin(String input) {
        return getPin().equals(input);
    }
}
