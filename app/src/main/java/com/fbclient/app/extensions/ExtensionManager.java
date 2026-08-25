package com.fbclient.app.extensions;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Manages WebExtensions via GeckoView WebExtensionController */
public class ExtensionManager {
    private static final String TAG = "ExtensionManager";
    private static final String PREF_FILE = "fbclient_extensions";
    private static final String PREF_EXTENSIONS = "installed_extensions";

    private final Context context;
    private final GeckoRuntime runtime;
    private final WebExtensionController controller;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private List<Extension> extensions;

    public interface InstallCallback {
        void onSuccess(Extension ext);
        void onError(String message);
    }

    public ExtensionManager(Context context, GeckoRuntime runtime) {
        this.context = context;
        this.runtime = runtime;
        this.controller = runtime.getWebExtensionController();
        this.prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        this.extensions = loadExtensions();
    }

    /** Install from direct .xpi URL */
    public void installExtension(String url, InstallCallback callback) {
        // Normalize AMO page URLs to direct download if needed
        String installUrl = normalizeUrl(url);
        Log.d(TAG, "Installing from: " + installUrl);

        controller.install(installUrl)
            .accept(
                webExtension -> {
                    if (webExtension == null) {
                        callback.onError("Installation returned null — check URL is a direct .xpi link");
                        return;
                    }
                    Extension ext = toModel(webExtension);
                    extensions.add(ext);
                    saveExtensions();
                    callback.onSuccess(ext);
                },
                throwable -> {
                    String msg = throwable != null ? throwable.getMessage() : "Unknown error";
                    Log.e(TAG, "Install failed: " + msg, throwable);
                    callback.onError(msg);
                }
            );
    }

    public void uninstallExtension(String extId, InstallCallback callback) {
        extensions.removeIf(e -> e.id.equals(extId));
        saveExtensions();
        callback.onSuccess(null);
    }

    public void setExtensionEnabled(String extId, boolean enabled) {
        for (Extension ext : extensions) {
            if (ext.id.equals(extId)) { ext.enabled = enabled; break; }
        }
        saveExtensions();
    }

    public List<Extension> getExtensions() { return new ArrayList<>(extensions); }

    /** Convert AMO addon page URL to direct .xpi download URL if needed */
    private String normalizeUrl(String url) {
        // Already a direct .xpi link
        if (url.endsWith(".xpi") || url.contains("/downloads/file/")) return url;
        // AMO page URL — can't auto-resolve, return as-is and let GeckoView try
        return url;
    }

    private Extension toModel(WebExtension webExt) {
        Extension ext = new Extension();
        ext.id = webExt.id;
        ext.name = webExt.metaData != null ? webExt.metaData.name : webExt.id;
        ext.version = webExt.metaData != null ? webExt.metaData.version : "";
        ext.description = webExt.metaData != null ? webExt.metaData.description : "";
        ext.iconUrl = "";
        ext.enabled = true;
        ext.sourceUrl = webExt.location;
        return ext;
    }

    private List<Extension> loadExtensions() {
        String json = prefs.getString(PREF_EXTENSIONS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<Extension>>() {}.getType();
        try {
            List<Extension> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private void saveExtensions() {
        prefs.edit().putString(PREF_EXTENSIONS, gson.toJson(extensions)).apply();
    }
}
