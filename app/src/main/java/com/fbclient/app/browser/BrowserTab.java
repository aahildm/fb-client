package com.fbclient.app.browser;

import org.mozilla.geckoview.GeckoSession;

/** Represents a single browser tab */
public class BrowserTab {
    private final GeckoSession session;
    private String title;
    private String url;
    private boolean loading;
    private final int id;
    private static int idCounter = 0;

    public BrowserTab(GeckoSession session) {
        this.session = session;
        this.id = ++idCounter;
        this.title = "New Tab";
        this.url = "";
        this.loading = false;
    }

    public GeckoSession getSession() { return session; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public boolean isLoading() { return loading; }
    public void setLoading(boolean loading) { this.loading = loading; }
    public int getId() { return id; }
}
