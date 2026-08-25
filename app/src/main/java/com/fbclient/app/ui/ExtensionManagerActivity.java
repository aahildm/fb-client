package com.fbclient.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fbclient.app.R;
import com.fbclient.app.browser.GeckoProvider;
import com.fbclient.app.extensions.Extension;
import com.fbclient.app.extensions.ExtensionManager;

import java.util.List;

public class ExtensionManagerActivity extends AppCompatActivity {

    private ExtensionManager extensionManager;
    private ExtensionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);

        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        extensionManager = new ExtensionManager(this, GeckoProvider.getRuntime(this));

        RecyclerView recyclerView = findViewById(R.id.rv_extensions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<Extension> extensions = extensionManager.getExtensions();
        adapter = new ExtensionAdapter(extensions, extensionManager);
        recyclerView.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btn_add_extension);
        btnAdd.setOnClickListener(v -> showInstallDialog());

        // Show installed count
        TextView tvEmpty = findViewById(R.id.tv_empty);
        tvEmpty.setVisibility(extensions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showInstallDialog() {
        EditText input = new EditText(this);
        input.setHint("https://addons.mozilla.org/firefox/addon/...");
        new AlertDialog.Builder(this)
            .setTitle("Install Extension")
            .setMessage("Enter extension URL (AMO or .xpi):")
            .setView(input)
            .setPositiveButton("Install", (d, w) -> {
                String url = input.getText().toString().trim();
                if (url.isEmpty()) return;
                Toast.makeText(this, "Installing...", Toast.LENGTH_SHORT).show();
                extensionManager.installExtension(url, new ExtensionManager.InstallCallback() {
                    @Override
                    public void onSuccess(Extension ext) {
                        runOnUiThread(() -> {
                            adapter.addExtension(ext);
                            Toast.makeText(ExtensionManagerActivity.this,
                                ext.name + " installed", Toast.LENGTH_SHORT).show();
                            findViewById(R.id.tv_empty).setVisibility(View.GONE);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() ->
                            Toast.makeText(ExtensionManagerActivity.this,
                                "Install failed: " + message, Toast.LENGTH_LONG).show());
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // -- Adapter --

    static class ExtensionAdapter extends RecyclerView.Adapter<ExtensionAdapter.VH> {
        private final List<Extension> items;
        private final ExtensionManager manager;

        ExtensionAdapter(List<Extension> items, ExtensionManager manager) {
            this.items = items;
            this.manager = manager;
        }

        void addExtension(Extension ext) {
            items.add(0, ext);
            notifyItemInserted(0);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_extension, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Extension ext = items.get(position);
            holder.tvName.setText(ext.name.isEmpty() ? ext.id : ext.name);
            holder.tvVersion.setText("v" + ext.version);
            holder.swEnabled.setChecked(ext.enabled);
            holder.swEnabled.setOnCheckedChangeListener((btn, checked) -> {
                ext.enabled = checked;
                manager.setExtensionEnabled(ext.id, checked);
            });
            holder.btnRemove.setOnClickListener(v -> {
                manager.uninstallExtension(ext.id, new ExtensionManager.InstallCallback() {
                    @Override
                    public void onSuccess(Extension e) {
                        int pos = holder.getAdapterPosition();
                        items.remove(pos);
                        notifyItemRemoved(pos);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(v.getContext(), "Error: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvVersion;
            Switch swEnabled;
            Button btnRemove;

            VH(@NonNull View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_ext_name);
                tvVersion = v.findViewById(R.id.tv_ext_version);
                swEnabled = v.findViewById(R.id.sw_ext_enabled);
                btnRemove = v.findViewById(R.id.btn_ext_remove);
            }
        }
    }
}
