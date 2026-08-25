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
import android.text.TextUtils;
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

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST = 100;
    private static final String FB_HOME = "https://www.facebook.com";

    // Views
    private GeckoView geckoView;
    private EditText urlBar;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private DrawerLayout drawerLayout;
    private ImageButton btnBack, btnForward, btnTabs;
    private TextView tabCountBadge;

    // Core
    private GeckoRuntime runtime;
    private TabManager tabManager;
    private BookmarkManager bookmarkManager;
    private HistoryManager historyManager;
    private ExtensionManager extensionManager;
    private AppPrefs prefs;

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
        handleIntent(getIntent()); // handle deep-link intents
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
        btnTabs = findViewById(R.id.btn_tabs);
        tabCountBadge = findViewById(R.id.tab_count_badge);

        NavigationView navView = findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(this::onNavItemSelected);

        // URL bar – navigate on enter
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
            BrowserTab tab = tabManager.getActiveTab();
            if (tab != null && tab.getSession().canGoBack()) tab.getSession().goBack();
        });

        btnForward.setOnClickListener(v -> {
            BrowserTab tab = tabManager.getActiveTab();
            if (tab != null && tab.getSession().canGoForward()) tab.getSession().goForward();
        });

        btnTabs.setOnClickListener(v -> showTabsDialog());

        // Burger menu opens nav drawer
        ImageButton btnMenu = findViewById(R.id.btn_menu);
        btnMenu.setOnClickListener(v -> drawerLayout.open());
    }

    private void setupTabManager() {
        tabManager.setListener(new TabManager.TabListener() {
            @Override
            public void onTabAdded(BrowserTab tab) {
                updateTabBadge();
            }

            @Override
            public void onTabRemoved(BrowserTab tab) {
                updateTabBadge();
                if (tabManager.getTabCount() == 0) {
                    tabManager.newTab(prefs.getHomeUrl());
                }
            }

            @Override
            public void onTabSwitched(BrowserTab tab) {
                attachSession(tab);
            }
        });

        // Open first tab
        BrowserTab first = tabManager.newTab(null);
        attachSession(first);
        loadUrl(first, prefs.getHomeUrl());
    }

    /** Attach a GeckoSession to the GeckoView and configure delegates */
    private void attachSession(BrowserTab tab) {
        GeckoSession session = tab.getSession();

        if (!session.isOpen()) {
            session.open(runtime);
        }

        geckoView.setSession(session);

        // Navigation delegate
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(@NonNull GeckoSession s, String url, @NonNull java.util.List<GeckoSession.PermissionDelegate.ContentPermission> permissions) {
                runOnUiThread(() -> {
                    if (url != null) {
                        tab.setUrl(url);
                        urlBar.setText(url);
                        urlBar.clearFocus();
                        btnBack.setEnabled(session.canGoBack());
                        btnForward.setEnabled(session.canGoForward());
                    }
                });
            }

            @Override
            public void onCanGoBack(@NonNull GeckoSession s, boolean canGoBack) {
                runOnUiThread(() -> btnBack.setEnabled(canGoBack));
            }

            @Override
            public void onCanGoForward(@NonNull GeckoSession s, boolean canGoForward) {
                runOnUiThread(() -> btnForward.setEnabled(canGoForward));
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession s,
                    @NonNull GeckoSession.NavigationDelegate.LoadRequest request) {
                // Block non-Facebook domains if configured (optional strict mode)
                return GeckoResult.allow();
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession s, @NonNull String uri) {
                // Open popup/new window as a new tab
                BrowserTab newTab = tabManager.newTab(uri);
                return GeckoResult.fromValue(newTab.getSession());
            }
        });

        // Progress delegate
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
                // Add to history
                if (success && tab.getUrl() != null && !tab.getUrl().isEmpty()) {
                    historyManager.addEntry(tab.getUrl(), tab.getTitle());
                }
            }

            @Override
            public void onProgressChange(@NonNull GeckoSession s, int progress) {
                runOnUiThread(() -> progressBar.setProgress(progress));
            }

            @Override
            public void onSessionStateChange(@NonNull GeckoSession s, @NonNull GeckoSession.SessionState state) {}
        });

        // Title delegate
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
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        );
                    } else {
                        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                    }
                });
            }
        });

        // Permission delegate (camera, microphone, geolocation)
        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override
            public void onContentPermissionRequest(@NonNull GeckoSession s, @NonNull ContentPermission perm) {
                runOnUiThread(() -> showPermissionDialog(perm));
            }

            @Override
            public void onAndroidPermissionsRequest(@NonNull GeckoSession s, String[] permissions,
                                                    @NonNull Callback callback) {
                ActivityCompat.requestPermissions(MainActivity.this, permissions, PERM_REQUEST);
                callback.grant(); // grant after system prompt
            }
        });

        // Download delegate
        session.setNavigationDelegate(session.getNavigationDelegate()); // keep existing
        geckoView.setDownloadDelegate((geckoSession, downloadRequest) -> {
            DownloadHandler.download(
                MainActivity.this,
                downloadRequest.uri,
                downloadRequest.contentDisposition,
                downloadRequest.mimeType,
                "",
                UserAgentHelper.get(prefs.getUaMode())
            );
        });

        // Set user agent via GeckoSession settings
        GeckoSession.SessionState state = session.saveState();
    }

    private void loadUrl(BrowserTab tab, String url) {
        if (url == null || url.isEmpty()) return;
        tab.getSession().loadUri(url);
    }

    /** Navigate current tab to URL or search */
    private void navigateTo(String input) {
        if (input.isEmpty()) return;
        String url;
        if (input.startsWith("http://") || input.startsWith("https://") || input.startsWith("file://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = "https://" + input;
        } else {
            // Google search fallback
            url = "https://www.google.com/search?q=" + Uri.encode(input);
        }
        BrowserTab tab = tabManager.getActiveTab();
        if (tab != null) loadUrl(tab, url);
    }

    private void showPermissionDialog(GeckoSession.PermissionDelegate.ContentPermission perm) {
        String permName = perm.permission == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION
            ? "Location" : perm.permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION
            ? "Notifications" : "Camera/Microphone";
        new AlertDialog.Builder(this)
            .setTitle(permName + " Permission")
            .setMessage(perm.uri + " wants to access your " + permName.toLowerCase())
            .setPositiveButton("Allow", (d, w) -> perm.grant())
            .setNegativeButton("Deny", (d, w) -> perm.deny())
            .show();
    }

    private void showTabsDialog() {
        // Build tab titles list
        java.util.List<BrowserTab> tabs = tabManager.getTabs();
        String[] titles = new String[tabs.size() + 1];
        for (int i = 0; i < tabs.size(); i++) {
            titles[i] = (i + 1) + ". " + (tabs.get(i).getTitle().isEmpty() ? tabs.get(i).getUrl() : tabs.get(i).getTitle());
        }
        titles[tabs.size()] = "+ New Tab";

        new AlertDialog.Builder(this)
            .setTitle("Tabs (" + tabs.size() + ")")
            .setItems(titles, (dialog, which) -> {
                if (which == tabs.size()) {
                    tabManager.newTab(prefs.getHomeUrl());
                } else {
                    tabManager.switchTo(tabs.get(which));
                }
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
        if (id == R.id.nav_home) {
            navigateTo(prefs.getHomeUrl());
        } else if (id == R.id.nav_bookmarks) {
            showBookmarksDialog();
        } else if (id == R.id.nav_history) {
            showHistoryDialog();
        } else if (id == R.id.nav_extensions) {
            startActivity(new Intent(this, ExtensionManagerActivity.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.nav_new_tab) {
            tabManager.newTab(prefs.getHomeUrl());
        } else if (id == R.id.nav_private_tab) {
            tabManager.newPrivateTab(prefs.getHomeUrl());
        } else if (id == R.id.nav_clear_data) {
            confirmClearData();
        }
        return true;
    }

    private void showBookmarksDialog() {
        java.util.List<BookmarkManager.Bookmark> bookmarks = bookmarkManager.getAll();
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "No bookmarks yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = bookmarks.stream().map(b -> b.title.isEmpty() ? b.url : b.title).toArray(String[]::new);
        new AlertDialog.Builder(this)
            .setTitle(R.string.bookmarks)
            .setItems(items, (d, which) -> navigateTo(bookmarks.get(which).url))
            .show();
    }

    private void showHistoryDialog() {
        java.util.List<HistoryManager.HistoryEntry> history = historyManager.getHistory();
        if (history.isEmpty()) {
            Toast.makeText(this, "No history", Toast.LENGTH_SHORT).show();
            return;
        }
        int max = Math.min(history.size(), 30);
        String[] items = history.subList(0, max).stream()
            .map(e -> e.title.isEmpty() ? e.url : e.title).toArray(String[]::new);
        new AlertDialog.Builder(this)
            .setTitle(R.string.history)
            .setItems(items, (d, which) -> navigateTo(history.get(which).url))
            .show();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
            .setTitle("Clear Browsing Data")
            .setMessage("Clear cookies, history, and cache?")
            .setPositiveButton("Clear", (d, w) -> {
                historyManager.clearHistory();
                runtime.clearCache(true);
                Toast.makeText(this, "Data cleared", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
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
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("URL", tab.getUrl()));
                Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.action_find_in_page) {
            showFindInPage();
        } else if (id == R.id.action_desktop_site) {
            toggleDesktopMode();
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareUrl(String url) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(shareIntent, "Share URL"));
    }

    private void showFindInPage() {
        // Simple find-in-page via GeckoSession finder
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Search text...");
        new AlertDialog.Builder(this)
            .setTitle("Find in Page")
            .setView(input)
            .setPositiveButton("Find", (d, w) -> {
                BrowserTab tab = tabManager.getActiveTab();
                if (tab != null) {
                    tab.getSession().getFinder().find(input.getText().toString(),
                        GeckoSession.FinderFindFlags.FIND_BACKWARDS);
                }
            })
            .setNegativeButton("Close", (d, w) -> {
                BrowserTab tab = tabManager.getActiveTab();
                if (tab != null) tab.getSession().getFinder().clear();
            })
            .show();
    }

    private void toggleDesktopMode() {
        int current = prefs.getUaMode();
        int next = current == 1 ? 0 : 1;
        prefs.setUaMode(next);
        // Reload to apply UA change
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
        if (data != null) {
            String url = data.toString();
            if (tabManager.getActiveTab() != null) {
                navigateTo(url);
            } else {
                tabManager.newTab(url);
            }
        }
    }

    @Override
    public void onBackPressed() {
        BrowserTab tab = tabManager.getActiveTab();
        if (drawerLayout.isOpen()) {
            drawerLayout.close();
        } else if (tab != null && tab.getSession().canGoBack()) {
            tab.getSession().goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close all sessions
        for (BrowserTab tab : tabManager.getTabs()) {
            tab.getSession().close();
        }
    }
}
