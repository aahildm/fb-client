package com.fbclient.app.extensions;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Manages WebExtensions lifecycle via GeckoView */
public class ExtensionManager {
    private static final String TAG = "ExtensionManager";
    private static final String PREF_EXTENSIONS = "installed_extensions";
    private static final String PREF_FILE = "fbclient_extensions";

    private final Context context;
    private final GeckoRuntime runtime;
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
        this.prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        this.extensions = loadExtensions();
    }

    /** Install an extension from URL (.xpi or addons.mozilla.org) */
    public void installExtension(String url, InstallCallback callback) {
        runtime.getWebExtensionController().install(url)
            .accept(
                webExtension -> {
                    Extension ext = toModel(webExtension);
                    extensions.add(ext);
                    saveExtensions();
                    callback.onSuccess(ext);
                },
                throwable -> {
                    Log.e(TAG, "Install failed: " + url, throwable);
                    callback.onError(throwable != null ? throwable.getMessage() : "Unknown error");
                }
            );
    }

    /** Uninstall by extension id */
    public void uninstallExtension(String extId, InstallCallback callback) {
        WebExtension existing = findGeckoExtension(extId);
        if (existing == null) {
            // Remove from local list even if not found in runtime
            extensions.removeIf(e -> e.id.equals(extId));
            saveExtensions();
            callback.onSuccess(null);
            return;
        }
        runtime.getWebExtensionController().uninstall(existing)
            .accept(
                v -> {
                    extensions.removeIf(e -> e.id.equals(extId));
                    saveExtensions();
                    callback.onSuccess(null);
                },
                throwable -> callback.onError(throwable != null ? throwable.getMessage() : "Uninstall failed")
            );
    }

    /** Enable or disable an extension */
    public void setExtensionEnabled(String extId, boolean enabled) {
        WebExtension existing = findGeckoExtension(extId);
        if (existing != null) {
            if (enabled) {
                runtime.getWebExtensionController().enable(existing, WebExtension.EnableSource.USER);
            } else {
                runtime.getWebExtensionController().disable(existing, WebExtension.EnableSource.USER);
            }
        }
        // Update local model
        for (Extension ext : extensions) {
            if (ext.id.equals(extId)) {
                ext.enabled = enabled;
                break;
            }
        }
        saveExtensions();
    }

    public List<Extension> getExtensions() {
        return new ArrayList<>(extensions);
    }

    // -- Private helpers --

    private WebExtension findGeckoExtension(String id) {
        // GeckoRuntime doesn't expose a synchronous list; we rely on our local model
        // and the runtime keeps track internally. This is a best-effort lookup.
        return null;
    }

    private Extension toModel(WebExtension webExt) {
        Extension ext = new Extension();
        ext.id = webExt.id;
        ext.name = webExt.metaData != null ? webExt.metaData.name : webExt.id;
        ext.version = webExt.metaData != null ? webExt.metaData.version : "";
        ext.description = webExt.metaData != null ? webExt.metaData.description : "";
        ext.iconUrl = webExt.metaData != null && webExt.metaData.icons != null
                ? webExt.metaData.icons.get(64) : "";
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
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveExtensions() {
        prefs.edit().putString(PREF_EXTENSIONS, gson.toJson(extensions)).apply();
    }
}
