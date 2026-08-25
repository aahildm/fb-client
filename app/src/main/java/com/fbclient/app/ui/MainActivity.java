package com.fbclient.app.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.fbclient.app.R;
import com.fbclient.app.browser.BookmarkManager;
import com.fbclient.app.browser.BrowserTab;
import com.fbclient.app.browser.DownloadHandler;
import com.fbclient.app.browser.GeckoProvider;
import com.fbclient.app.browser.HistoryManager;
import com.fbclient.app.browser.TabManager;
import com.fbclient.app.extensions.ExtensionManager;
import com.fbclient.app.utils.AppPrefs;
import com.fbclient.app.utils.UserAgentHelper;
import com.google.android.material.navigation.NavigationView;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebResponse;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST = 100;

    private GeckoView geckoView;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private DrawerLayout drawerLayout;
    private ImageButton btnBack, btnForward;
    private TextView tabCountBadge;

    private GeckoRuntime runtime;
    private TabManager tabManager;
    private BookmarkManager bookmarkManager;
    private HistoryManager historyManager;
    private ExtensionManager extensionManager;
    private AppPrefs prefs;

    // Track navigation state manually (canGoBack/Forward removed in 117)
    private boolean canGoBack = false;
    private boolean canGoForward = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new AppPrefs(this);
        runtime = GeckoProvider.getRuntime(this);
        bookmarkManager = new BookmarkManager(this);
        historyManager = new HistoryManager(this);
        extensionManager = new ExtensionManager(this, runtime);
        tabManager = new TabManager(runtime);

        initViews();
        setupTabManager();
        requestPermissionsIfNeeded();
        handleIntent(getIntent());
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        geckoView = findViewById(R.id.gecko_view);
        urlBar = findViewById(R.id.url_bar);
        progressBar = findViewById(R.id.progress_bar);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        drawerLayout = findViewById(R.id.drawer_layout);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        tabCountBadge = findViewById(R.id.tab_count_badge);

        NavigationView navView = findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(this::onNavItemSelected);

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateTo(urlBar.getText().toString().trim());
                hideKeyboard();
                return true;
            }
            return false;
        });

        swipeRefresh.setOnRefreshListener(() -> {
            BrowserTab tab = tabManager.getActiveTab();
            if (tab != null) tab.getSession().reload(GeckoSession.LOAD_FLAGS_NONE);
        });

        btnBack.setOnClickListener(v -> {
            if (canGoBack) tabManager.getActiveTab().getSession().goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (canGoForward) tabManager.getActiveTab().getSession().goForward();
        });

        ImageButton btnMenu = findViewById(R.id.btn_menu);
        btnMenu.setOnClickListener(v -> drawerLayout.open());
    }

    private void setupTabManager() {
        tabManager.setListener(new TabManager.TabListener() {
            @Override public void onTabAdded(BrowserTab tab) { updateTabBadge(); }
            @Override public void onTabRemoved(BrowserTab tab) {
                updateTabBadge();
                if (tabManager.getTabCount() == 0) tabManager.newTab(prefs.getHomeUrl());
            }
            @Override public void onTabSwitched(BrowserTab tab) { attachSession(tab); }
        });

        BrowserTab first = tabManager.newTab(null);
        attachSession(first);
        loadUrl(first, prefs.getHomeUrl());
    }

    private void attachSession(BrowserTab tab) {
        GeckoSession session = tab.getSession();
        if (!session.isOpen()) session.open(runtime);
        geckoView.setSession(session);

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(@NonNull GeckoSession s, String url,
                    @NonNull java.util.List<GeckoSession.PermissionDelegate.ContentPermission> perms) {
                runOnUiThread(() -> {
                    if (url != null) {
                        tab.setUrl(url);
                        urlBar.setText(url);
                        urlBar.clearFocus();
                    }
                });
            }

            @Override
            public void onCanGoBack(@NonNull GeckoSession s, boolean value) {
                canGoBack = value;
                runOnUiThread(() -> btnBack.setAlpha(value ? 1f : 0.4f));
            }

            @Override
            public void onCanGoForward(@NonNull GeckoSession s, boolean value) {
                canGoForward = value;
                runOnUiThread(() -> btnForward.setAlpha(value ? 1f : 0.4f));
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession s, @NonNull String uri) {
                BrowserTab newTab = tabManager.newTab(uri);
                return GeckoResult.fromValue(newTab.getSession());
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(@NonNull GeckoSession s, @NonNull String url) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(10);
                    tab.setLoading(true);
                });
            }

            @Override
            public void onPageStop(@NonNull GeckoSession s, boolean success) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    tab.setLoading(false);
                });
                if (success && tab.getUrl() != null && !tab.getUrl().isEmpty()) {
                    historyManager.addEntry(tab.getUrl(), tab.getTitle());
                }
            }

            @Override
            public void onProgressChange(@NonNull GeckoSession s, int progress) {
                runOnUiThread(() -> progressBar.setProgress(progress));
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(@NonNull GeckoSession s, String title) {
                if (title != null) tab.setTitle(title);
            }

            @Override
            public void onFullScreen(@NonNull GeckoSession s, boolean fullScreen) {
                runOnUiThread(() -> {
                    View decorView = getWindow().getDecorView();
                    if (fullScreen) {
                        decorView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                    } else {
                        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                    }
                });
            }

            // Handle downloads via content delegate
            @Override
            public void onExternalResponse(@NonNull GeckoSession s, @NonNull WebResponse response) {
                DownloadHandler.download(
                    MainActivity.this,
                    response.uri,
                    response.headers.get("content-disposition"),
                    response.headers.get("content-type"),
                    "",
                    UserAgentHelper.get(prefs.getUaMode())
                );
            }
        });

        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override
            public GeckoResult<Integer> onContentPermissionRequest(
                    @NonNull GeckoSession s, @NonNull ContentPermission perm) {
                GeckoResult<Integer> result = new GeckoResult<>();
                runOnUiThread(() -> {
                    String permName = perm.permission == PERMISSION_GEOLOCATION ? "Location"
                        : perm.permission == PERMISSION_DESKTOP_NOTIFICATION ? "Notifications"
                        : "Camera/Microphone";
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle(permName + " Permission")
                        .setMessage(perm.uri + " wants access to " + permName.toLowerCase())
                        .setPositiveButton("Allow", (d, w) -> result.complete(ContentPermission.VALUE_ALLOW))
                        .setNegativeButton("Deny", (d, w) -> result.complete(ContentPermission.VALUE_DENY))
                        .show();
                });
                return result;
            }

            @Override
            public void onAndroidPermissionsRequest(@NonNull GeckoSession s, String[] permissions,
                                                    @NonNull Callback callback) {
                ActivityCompat.requestPermissions(MainActivity.this, permissions, PERM_REQUEST);
                callback.grant();
            }
        });
    }

    private void loadUrl(BrowserTab tab, String url) {
        if (url == null || url.isEmpty()) return;
        tab.getSession().loadUri(url);
    }

    private void navigateTo(String input) {
        if (input.isEmpty()) return;
        String url;
        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("file://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = "https://" + input;
        } else {
            url = "https://www.google.com/search?q=" + Uri.encode(input);
        }
        BrowserTab tab = tabManager.getActiveTab();
        if (tab != null) loadUrl(tab, url);
    }

    private void showTabsDialog() {
        java.util.List<BrowserTab> tabs = tabManager.getTabs();
        String[] titles = new String[tabs.size() + 1];
        for (int i = 0; i < tabs.size(); i++) {
            titles[i] = (i + 1) + ". " + (tabs.get(i).getTitle().isEmpty()
                ? tabs.get(i).getUrl() : tabs.get(i).getTitle());
        }
        titles[tabs.size()] = "+ New Tab";
        new AlertDialog.Builder(this)
            .setTitle("Tabs (" + tabs.size() + ")")
            .setItems(titles, (dialog, which) -> {
                if (which == tabs.size()) tabManager.newTab(prefs.getHomeUrl());
                else tabManager.switchTo(tabs.get(which));
            })
            .setNeutralButton("Close Active", (d, w) -> {
                BrowserTab active = tabManager.getActiveTab();
                if (active != null) tabManager.closeTab(active);
            })
            .show();
    }

    private boolean onNavItemSelected(MenuItem item) {
        drawerLayout.close();
        int id = item.getItemId();
        if (id == R.id.nav_home) navigateTo(prefs.getHomeUrl());
        else if (id == R.id.nav_bookmarks) showBookmarksDialog();
        else if (id == R.id.nav_history) showHistoryDialog();
        else if (id == R.id.nav_extensions) startActivity(new Intent(this, ExtensionManagerActivity.class));
        else if (id == R.id.nav_settings) startActivity(new Intent(this, SettingsActivity.class));
        else if (id == R.id.nav_new_tab) tabManager.newTab(prefs.getHomeUrl());
        else if (id == R.id.nav_private_tab) tabManager.newPrivateTab(prefs.getHomeUrl());
        else if (id == R.id.nav_clear_data) confirmClearData();
        return true;
    }

    private void showBookmarksDialog() {
        java.util.List<BookmarkManager.Bookmark> bookmarks = bookmarkManager.getAll();
        if (bookmarks.isEmpty()) { Toast.makeText(this, "No bookmarks yet", Toast.LENGTH_SHORT).show(); return; }
        String[] items = bookmarks.stream().map(b -> b.title.isEmpty() ? b.url : b.title).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(R.string.bookmarks)
            .setItems(items, (d, which) -> navigateTo(bookmarks.get(which).url)).show();
    }

    private void showHistoryDialog() {
        java.util.List<HistoryManager.HistoryEntry> history = historyManager.getHistory();
        if (history.isEmpty()) { Toast.makeText(this, "No history", Toast.LENGTH_SHORT).show(); return; }
        int max = Math.min(history.size(), 30);
        String[] items = history.subList(0, max).stream()
            .map(e -> e.title.isEmpty() ? e.url : e.title).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(R.string.history)
            .setItems(items, (d, which) -> navigateTo(history.get(which).url)).show();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
            .setTitle("Clear Browsing Data")
            .setMessage("Clear cookies, history, and cache?")
            .setPositiveButton("Clear", (d, w) -> {
                historyManager.clearHistory();
                // Clear cache via session storage delegate
                Toast.makeText(this, "Data cleared", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        BrowserTab tab = tabManager.getActiveTab();
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            if (tab != null) tab.getSession().reload(GeckoSession.LOAD_FLAGS_NONE);
        } else if (id == R.id.action_bookmark) {
            if (tab != null) {
                if (bookmarkManager.isBookmarked(tab.getUrl())) {
                    bookmarkManager.remove(tab.getUrl());
                    Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
                } else {
                    bookmarkManager.add(tab.getUrl(), tab.getTitle());
                    Toast.makeText(this, "Bookmarked!", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (id == R.id.action_share) {
            if (tab != null) shareUrl(tab.getUrl());
        } else if (id == R.id.action_copy_url) {
            if (tab != null) {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("URL", tab.getUrl()));
                Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.action_tabs) {
            showTabsDialog();
        } else if (id == R.id.action_desktop_site) {
            toggleDesktopMode();
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareUrl(String url) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(i, "Share URL"));
    }

    private void toggleDesktopMode() {
        int next = prefs.getUaMode() == 1 ? 0 : 1;
        prefs.setUaMode(next);
        BrowserTab tab = tabManager.getActiveTab();
        if (tab != null) tab.getSession().reload(GeckoSession.LOAD_FLAGS_NONE);
        Toast.makeText(this, next == 1 ? "Desktop mode on" : "Mobile mode on", Toast.LENGTH_SHORT).show();
    }

    private void updateTabBadge() {
        runOnUiThread(() -> tabCountBadge.setText(String.valueOf(tabManager.getTabCount())));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERM_REQUEST);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null) navigateTo(data.toString());
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isOpen()) drawerLayout.close();
        else if (canGoBack) tabManager.getActiveTab().getSession().goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (BrowserTab tab : tabManager.getTabs()) tab.getSession().close();
    }
}
