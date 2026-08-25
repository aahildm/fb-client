package com.fbclient.app.browser;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

import java.util.ArrayList;
import java.util.List;

/** Manages browser tabs (GeckoSession instances) */
public class TabManager {
    private final List<BrowserTab> tabs = new ArrayList<>();
    private BrowserTab activeTab;
    private final GeckoRuntime runtime;

    public interface TabListener {
        void onTabAdded(BrowserTab tab);
        void onTabRemoved(BrowserTab tab);
        void onTabSwitched(BrowserTab tab);
    }

    private TabListener listener;

    public TabManager(GeckoRuntime runtime) {
        this.runtime = runtime;
    }

    public void setListener(TabListener listener) {
        this.listener = listener;
    }

    /** Create and activate a new tab, optionally load a URL */
    public BrowserTab newTab(String url) {
        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .suspendMediaWhenInactive(true)
            .build();
        GeckoSession session = new GeckoSession(settings);
        BrowserTab tab = new BrowserTab(session);
        tabs.add(tab);
        if (url != null && !url.isEmpty()) {
            tab.setUrl(url);
        }
        if (listener != null) listener.onTabAdded(tab);
        switchTo(tab);
        return tab;
    }

    /** Create a new private/incognito tab */
    public BrowserTab newPrivateTab(String url) {
        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
            .usePrivateMode(true)
            .build();
        GeckoSession session = new GeckoSession(settings);
        BrowserTab tab = new BrowserTab(session);
        tabs.add(tab);
        if (url != null && !url.isEmpty()) {
            tab.setUrl(url);
        }
        if (listener != null) listener.onTabAdded(tab);
        switchTo(tab);
        return tab;
    }

    public void switchTo(BrowserTab tab) {
        activeTab = tab;
        if (listener != null) listener.onTabSwitched(tab);
    }

    public void closeTab(BrowserTab tab) {
        tab.getSession().close();
        tabs.remove(tab);
        if (listener != null) listener.onTabRemoved(tab);
        if (activeTab == tab) {
            activeTab = tabs.isEmpty() ? null : tabs.get(tabs.size() - 1);
            if (activeTab != null && listener != null) listener.onTabSwitched(activeTab);
        }
    }

    public BrowserTab getActiveTab() { return activeTab; }
    public List<BrowserTab> getTabs() { return new ArrayList<>(tabs); }
    public int getTabCount() { return tabs.size(); }
}
