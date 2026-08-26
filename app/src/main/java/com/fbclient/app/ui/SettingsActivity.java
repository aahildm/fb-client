package com.fbclient.app.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.fbclient.app.R;
import com.fbclient.app.features.AppLock;
import com.fbclient.app.utils.AppPrefs;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.settings_container, new SettingsFragment()).commit();
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private AppPrefs appPrefs;
        private AppLock appLock;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            appPrefs = new AppPrefs(requireContext());
            appLock = new AppLock(requireContext());

            // Home URL
            EditTextPreference homeUrl = findPreference("home_url");
            if (homeUrl != null) {
                homeUrl.setText(appPrefs.getHomeUrl());
                homeUrl.setSummary(appPrefs.getHomeUrl());
                homeUrl.setOnPreferenceChangeListener((p, v) -> {
                    appPrefs.setHomeUrl((String) v); p.setSummary((String) v); return true;
                });
            }

            // Theme
            ListPreference theme = findPreference("theme");
            if (theme != null) {
                theme.setOnPreferenceChangeListener((p, v) -> {
                    Toast.makeText(requireContext(), "Restart app to apply theme", Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            // App Lock
            Preference lockPref = findPreference("app_lock_pin");
            if (lockPref != null) {
                lockPref.setSummary(appLock.isEnabled() ? "PIN set — tap to change" : "Not set");
                lockPref.setOnPreferenceClickListener(p -> {
                    showPinDialog();
                    return true;
                });
            }

            // JavaScript
            SwitchPreferenceCompat js = findPreference("js_enabled");
            if (js != null) {
                js.setChecked(appPrefs.isJsEnabled());
                js.setOnPreferenceChangeListener((p, v) -> { appPrefs.setJsEnabled((Boolean) v); return true; });
            }

            // Clear data
            Preference clear = findPreference("clear_data");
            if (clear != null) {
                clear.setOnPreferenceClickListener(p -> {
                    appPrefs.clearAll();
                    Toast.makeText(requireContext(), "Cleared", Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        }

        private void showPinDialog() {
            android.widget.EditText input = new android.widget.EditText(requireContext());
            input.setHint("Enter 4-6 digit PIN (empty to disable)");
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            new android.app.AlertDialog.Builder(requireContext())
                .setTitle("App Lock PIN")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String pin = input.getText().toString().trim();
                    if (pin.isEmpty()) {
                        appLock.disable();
                        Toast.makeText(requireContext(), "Lock disabled", Toast.LENGTH_SHORT).show();
                    } else if (pin.length() >= 4) {
                        appLock.setPin(pin);
                        Toast.makeText(requireContext(), "PIN set", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "PIN must be 4+ digits", Toast.LENGTH_SHORT).show();
                    }
                    Preference p = findPreference("app_lock_pin");
                    if (p != null) p.setSummary(appLock.isEnabled() ? "PIN set — tap to change" : "Not set");
                })
                .setNegativeButton("Cancel", null).show();
        }
    }
}
