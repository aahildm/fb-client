package com.fbclient.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fbclient.app.R;
import com.fbclient.app.browser.GeckoProvider;
import com.fbclient.app.extensions.Extension;
import com.fbclient.app.extensions.ExtensionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class ExtensionManagerActivity extends AppCompatActivity {

    private static final int PICK_XPI = 200;
    private ExtensionManager extensionManager;
    private ExtensionAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        extensionManager = new ExtensionManager(this, GeckoProvider.getRuntime(this));

        RecyclerView rv = findViewById(R.id.rv_extensions);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<Extension> extensions = extensionManager.getExtensions();
        adapter = new ExtensionAdapter(extensions, extensionManager);
        rv.setAdapter(adapter);

        tvEmpty = findViewById(R.id.tv_empty);
        tvEmpty.setVisibility(extensions.isEmpty() ? View.VISIBLE : View.GONE);

        Button btnAdd = findViewById(R.id.btn_add_extension);
        btnAdd.setOnClickListener(v -> showInstallOptions());
    }

    private void showInstallOptions() {
        new AlertDialog.Builder(this)
            .setTitle("Install Extension")
            .setItems(new String[]{
                "📂 From local file (.xpi)",
                "🔗 From URL"
            }, (d, which) -> {
                if (which == 0) pickLocalFile();
                else showUrlDialog();
            })
            .show();
    }

    /** Open file picker to select a .xpi file from storage */
    private void pickLocalFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select .xpi file"), PICK_XPI);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_XPI && resultCode == RESULT_OK && data != null && data.getData() != null) {
            installFromUri(data.getData());
        }
    }

    /** Copy the picked file to app cache, then install via file:// URI */
    private void installFromUri(Uri uri) {
        Toast.makeText(this, "Installing...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // Copy to cache dir so GeckoView can access it
                File cacheFile = new File(getCacheDir(), "extension_install.xpi");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(cacheFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                }

                // Get file:// URI via FileProvider
                Uri fileUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", cacheFile);
                String installUrl = "file://" + cacheFile.getAbsolutePath();

                extensionManager.installExtension(installUrl, new ExtensionManager.InstallCallback() {
                    @Override public void onSuccess(Extension ext) {
                        runOnUiThread(() -> {
                            adapter.addExtension(ext);
                            tvEmpty.setVisibility(View.GONE);
                            Toast.makeText(ExtensionManagerActivity.this,
                                (ext.name.isEmpty() ? "Extension" : ext.name) + " installed!",
                                Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override public void onError(String message) {
                        runOnUiThread(() -> showError(message));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void showUrlDialog() {
        EditText input = new EditText(this);
        input.setHint("https://.../.xpi");

        new AlertDialog.Builder(this)
            .setTitle("Install from URL")
            .setMessage("Enter a direct .xpi download URL.\n\nTip: On addons.mozilla.org, tap the extension → copy the download link.")
            .setView(input)
            .setPositiveButton("Install", (d, w) -> {
                String url = input.getText().toString().trim();
                if (url.isEmpty()) return;
                Toast.makeText(this, "Installing...", Toast.LENGTH_SHORT).show();
                extensionManager.installExtension(url, new ExtensionManager.InstallCallback() {
                    @Override public void onSuccess(Extension ext) {
                        runOnUiThread(() -> {
                            adapter.addExtension(ext);
                            tvEmpty.setVisibility(View.GONE);
                            Toast.makeText(ExtensionManagerActivity.this,
                                (ext.name.isEmpty() ? "Extension" : ext.name) + " installed!",
                                Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override public void onError(String message) {
                        runOnUiThread(() -> showError(message));
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("Install Failed")
            .setMessage("Error: " + message
                + "\n\nTry using the local file option instead:\n"
                + "1. Download the .xpi file in your browser\n"
                + "2. Use '📂 From local file' option\n"
                + "3. Select the downloaded .xpi")
            .setPositiveButton("OK", null)
            .show();
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }

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
                    @Override public void onSuccess(Extension e) {
                        int pos = holder.getAdapterPosition();
                        items.remove(pos);
                        notifyItemRemoved(pos);
                    }
                    @Override public void onError(String msg) {
                        Toast.makeText(v.getContext(), "Error: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        @Override public int getItemCount() { return items.size(); }

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
