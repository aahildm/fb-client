package com.fbclient.app.browser;

import android.content.Context;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

/** Singleton GeckoRuntime – must only be created once per process */
public class GeckoProvider {
    private static GeckoRuntime runtime;

    public static synchronized GeckoRuntime getRuntime(Context context) {
        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(false)
                .remoteDebuggingEnabled(false)
                .webManifest(true)
                .crashHandler(null) // use default
                .build();
            runtime = GeckoRuntime.create(context.getApplicationContext(), settings);
        }
        return runtime;
    }
}
