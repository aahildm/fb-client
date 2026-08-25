package com.fbclient.app.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.fbclient.app.R;
import com.fbclient.app.utils.AppPrefs;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings_container, new SettingsFragment())
            .commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private AppPrefs appPrefs;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            appPrefs = new AppPrefs(requireContext());

            // UA mode
            ListPreference uaMode = findPreference("ua_mode");
            if (uaMode != null) {
                uaMode.setValue(String.valueOf(appPrefs.getUaMode()));
                uaMode.setOnPreferenceChangeListener((pref, val) -> {
                    appPrefs.setUaMode(Integer.parseInt((String) val));
                    return true;
                });
            }

            // Dark mode
            SwitchPreferenceCompat darkMode = findPreference("dark_mode");
            if (darkMode != null) {
                darkMode.setChecked(appPrefs.isDarkMode());
                darkMode.setOnPreferenceChangeListener((pref, val) -> {
                    appPrefs.setDarkMode((Boolean) val);
                    return true;
                });
            }

            // Ad block
            SwitchPreferenceCompat adblock = findPreference("adblock_enabled");
            if (adblock != null) {
                adblock.setChecked(appPrefs.isAdblockEnabled());
                adblock.setOnPreferenceChangeListener((pref, val) -> {
                    appPrefs.setAdblockEnabled((Boolean) val);
                    return true;
                });
            }

            // JS
            SwitchPreferenceCompat js = findPreference("js_enabled");
            if (js != null) {
                js.setChecked(appPrefs.isJsEnabled());
                js.setOnPreferenceChangeListener((pref, val) -> {
                    appPrefs.setJsEnabled((Boolean) val);
                    return true;
                });
            }

            // Cookies
            SwitchPreferenceCompat cookies = findPreference("save_cookies");
            if (cookies != null) {
                cookies.setChecked(appPrefs.isSaveCookies());
                cookies.setOnPreferenceChangeListener((pref, val) -> {
                    appPrefs.setSaveCookies((Boolean) val);
                    return true;
                });
            }

            // Home URL
            Preference homeUrl = findPreference("home_url");
            if (homeUrl != null) {
                homeUrl.setSummary(appPrefs.getHomeUrl());
                homeUrl.setOnPreferenceChangeListener((pref, val) -> {
                    appPrefs.setHomeUrl((String) val);
                    pref.setSummary((String) val);
                    return true;
                });
            }

            // Clear data
            Preference clearData = findPreference("clear_data");
            if (clearData != null) {
                clearData.setOnPreferenceClickListener(pref -> {
                    appPrefs.clearAll();
                    android.widget.Toast.makeText(requireContext(), "Data cleared", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        }
    }
}
