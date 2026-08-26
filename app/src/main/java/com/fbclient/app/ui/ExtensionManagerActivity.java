package com.fbclient.app.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.fbclient.app.R;

public class ExtensionManagerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);

        Toolbar toolbar = findViewById(R.id.extensions_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Content Filters");
        }

        setupToggle(R.id.sw_adblock,  "adblock",       true);
        setupToggle(R.id.sw_pymk,     "block_pymk",    true);
        setupToggle(R.id.sw_stories,  "block_stories", true);
        setupToggle(R.id.sw_status,   "block_status",  true);
    }

    private void setupToggle(int switchId, String key, boolean defaultVal) {
        Switch sw = findViewById(switchId);
        if (sw == null) return;
        SharedPreferences cp = getSharedPreferences("fbclient_content", MODE_PRIVATE);
        sw.setChecked(cp.getBoolean(key, defaultVal));
        sw.setOnCheckedChangeListener((btn, checked) -> {
            cp.edit().putBoolean(key, checked).apply();
            // Inject immediately into active WebView
            MainActivity.injectFilters();
            Toast.makeText(this, "Applied!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
