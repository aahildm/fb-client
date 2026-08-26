package com.fbclient.app.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
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

import com.fbclient.app.R;
import com.fbclient.app.browser.BookmarkManager;
import com.fbclient.app.browser.HistoryManager;
import com.fbclient.app.features.AccountManager;
import com.fbclient.app.features.AppLock;
import com.fbclient.app.features.KeywordFilter;
import com.fbclient.app.utils.AppPrefs;
import com.google.android.material.navigation.NavigationView;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST = 100;
    private static final int FILE_CHOOSER_REQUEST = 101;
    private static final String FB_HOME = "https://www.facebook.com";
    private static final String MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    public static WebView webView;

    private EditText urlBar;
    private ProgressBar progressBar;
    private DrawerLayout drawerLayout;
    private TextView tabCountBadge;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> filePathCallback;

    private BookmarkManager bookmarkManager;
    private HistoryManager historyManager;
    private AppPrefs prefs;
    private AccountManager accountManager;
    private KeywordFilter keywordFilter;
    private AppLock appLock;
    private boolean appUnlocked = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = new AppPrefs(this);
        appLock = new AppLock(this);
        accountManager = new AccountManager(this);
        keywordFilter = new KeywordFilter(this);

        // Apply theme before setContentView
        applyTheme();

        setContentView(R.layout.activity_main);

        bookmarkManager = new BookmarkManager(this);
        historyManager = new HistoryManager(this);

        initViews();
        setupWebView();
        requestPermissionsIfNeeded();

        // App lock check
        if (appLock.isEnabled() && !appUnlocked) {
            showLockScreen();
        } else {
            handleIntent(getIntent());
        }
    }

    private void applyTheme() {
        switch (prefs.getUaMode()) { // reuse field for theme
            case 1: setTheme(R.style.Theme_FBClient_Dark); break;
            case 2: setTheme(R.style.Theme_FBClient_Amoled); break;
            default: setTheme(R.style.Theme_FBClient); break;
        }
    }

    private void showLockScreen() {
        View lock = getLayoutInflater().inflate(R.layout.layout_lock, null);
        EditText pinInput = lock.findViewById(R.id.et_pin);
        AlertDialog dlg = new AlertDialog.Builder(this)
            .setTitle("🔒 FB Client")
            .setView(lock)
            .setCancelable(false)
            .setPositiveButton("Unlock", (d, w) -> {
                if (appLock.checkPin(pinInput.getText().toString())) {
                    appUnlocked = true;
                    handleIntent(getIntent());
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
                    showLockScreen();
                }
            })
            .create();
        dlg.show();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        webView = findViewById(R.id.gecko_view);
        urlBar = findViewById(R.id.url_bar);
        progressBar = findViewById(R.id.progress_bar);
        drawerLayout = findViewById(R.id.drawer_layout);
        tabCountBadge = findViewById(R.id.tab_count_badge);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        tabCountBadge.setText("1");

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

        findViewById(R.id.btn_menu).setOnClickListener(v -> drawerLayout.open());
        tabCountBadge.setOnClickListener(v -> showAccountSwitcher());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(MOBILE_UA);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setBackgroundColor(0xFF000000);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(10);
                urlBar.setText(url);
                injectFilters();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                urlBar.setText(url);
                urlBar.clearFocus();
                historyManager.addEntry(url, view.getTitle() != null ? view.getTitle() : "");
                injectFilters();
                // Re-inject after delays for dynamic content
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                for (int d : new int[]{1000, 3000, 8000}) h.postDelayed(() -> injectFilters(), d);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("tel:")) { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url))); return true; }
                if (url.startsWith("mailto:")) { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(url))); return true; }
                // Messages wall — just let it load, nothing we can do
                if (url.contains("facebook.com/messages") || url.contains("messenger.com")) {
                    return false;
                }
                if (url.startsWith("intent://")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        String vcuid = intent.getStringExtra("vcuid");
                        view.loadUrl(vcuid != null
                            ? "https://mbasic.facebook.com/messages/thread/" + vcuid + "/"
                            : "https://mbasic.facebook.com/messages/");
                    } catch (Exception e) { view.loadUrl("https://mbasic.facebook.com/messages/"); }
                    return true;
                }
                if (url.startsWith("fb://") || url.startsWith("fbmessenger://")) return true;
                if (!url.startsWith("http://") && !url.startsWith("https://")
                        && !url.startsWith("javascript:") && !url.startsWith("data:")
                        && !url.startsWith("blob:")) return true;
                return false;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                filePathCallback = callback;
                try { startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST); }
                catch (Exception e) { filePathCallback = null; return false; }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(view);
                fullscreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                if (customView != null) {
                    fullscreenContainer.removeView(customView);
                    fullscreenContainer.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    customView = null;
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView temp = new WebView(MainActivity.this);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        webView.loadUrl(req.getUrl().toString()); return true;
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }
        });

        webView.loadUrl(prefs.getHomeUrl());
    }

    public static void injectFilters() {
        if (webView == null) return;
        webView.post(() -> {
            try {
                Context ctx = webView.getContext();
                SharedPreferences cp = ctx.getSharedPreferences("fbclient_content", Context.MODE_PRIVATE);
                boolean adblock = cp.getBoolean("adblock", true);
                boolean pymk    = cp.getBoolean("block_pymk", true);
                boolean stories = cp.getBoolean("block_stories", true);
                boolean status  = cp.getBoolean("block_status", true);
                boolean reels   = cp.getBoolean("block_reels", true);

                InputStream is = ctx.getAssets().open("adblock/hide.js");
                byte[] buf = new byte[is.available()]; is.read(buf); is.close();
                String js = new String(buf, "UTF-8");
                String prefsJs = "window._fbc={adblock:" + adblock + ",block_pymk:" + pymk +
                    ",block_stories:" + stories + ",block_status:" + status +
                    ",block_reels:" + reels + "};";

                webView.evaluateJavascript(prefsJs + js, null);

                // Also inject keyword filter
                KeywordFilter kf = new KeywordFilter(ctx);
                String kwJs = kf.generateJS();
                if (!kwJs.isEmpty()) webView.evaluateJavascript(kwJs, null);

            } catch (IOException e) { /* ignore */ }
        });
    }

    private void showAccountSwitcher() {
        java.util.List<AccountManager.Account> accounts = accountManager.getAccounts();
        AccountManager.Account active = accountManager.getActive();

        String[] items = new String[accounts.size() + 2];
        for (int i = 0; i < accounts.size(); i++) {
            items[i] = (active != null && active.id.equals(accounts.get(i).id) ? "✓ " : "   ") + accounts.get(i).name;
        }
        items[accounts.size()] = "+ Add Account";
        items[accounts.size() + 1] = "✕ Remove Current";

        new AlertDialog.Builder(this)
            .setTitle("Accounts")
            .setItems(items, (d, which) -> {
                if (which < accounts.size()) {
                    accountManager.setActive(accounts.get(which).id);
                    CookieManager.getInstance().removeAllCookies(null);
                    webView.clearCache(true);
                    webView.loadUrl(FB_HOME);
                    Toast.makeText(this, "Switched to " + accounts.get(which).name, Toast.LENGTH_SHORT).show();
                } else if (which == accounts.size()) {
                    addAccount();
                } else {
                    if (active != null) {
                        accountManager.removeAccount(active.id);
                        CookieManager.getInstance().removeAllCookies(null);
                        webView.loadUrl(FB_HOME);
                    }
                }
            }).show();
    }

    private void addAccount() {
        EditText input = new EditText(this);
        input.setHint("Account name (e.g. Work, Personal)");
        new AlertDialog.Builder(this)
            .setTitle("Add Account")
            .setMessage("Clear cookies and login with new account")
            .setView(input)
            .setPositiveButton("Add", (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) name = "Account " + (accountManager.getAccounts().size() + 1);
                AccountManager.Account acc = new AccountManager.Account(name, FB_HOME);
                accountManager.addAccount(acc);
                CookieManager.getInstance().removeAllCookies(null);
                webView.clearCache(true);
                webView.loadUrl(FB_HOME);
                Toast.makeText(this, "Login with " + name, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void navigateTo(String input) {
        if (input == null || input.isEmpty()) return;
        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) url = input;
        else if (input.contains(".") && !input.contains(" ")) url = "https://" + input;
        else url = "https://www.google.com/search?q=" + Uri.encode(input);
        webView.loadUrl(url);
    }

    private boolean onNavItemSelected(MenuItem item) {
        drawerLayout.close();
        int id = item.getItemId();
        if (id == R.id.nav_home) webView.loadUrl(prefs.getHomeUrl());
        else if (id == R.id.nav_bookmarks) showBookmarksDialog();
        else if (id == R.id.nav_history) showHistoryDialog();
        else if (id == R.id.nav_extensions) startActivity(new Intent(this, ExtensionManagerActivity.class));
        else if (id == R.id.nav_settings) startActivity(new Intent(this, SettingsActivity.class));
        else if (id == R.id.nav_clear_data) confirmClearData();
        else if (id == R.id.nav_keywords) showKeywordDialog();
        return true;
    }

    private void showKeywordDialog() {
        java.util.List<String> keywords = keywordFilter.getKeywords();
        String[] display = keywords.isEmpty()
            ? new String[]{"No keywords yet"}
            : keywords.toArray(new String[0]);

        new AlertDialog.Builder(this)
            .setTitle("🚫 Keyword Filters")
            .setItems(display, (d, which) -> {
                if (!keywords.isEmpty()) {
                    new AlertDialog.Builder(this)
                        .setTitle("Remove keyword?")
                        .setMessage("\"" + keywords.get(which) + "\"")
                        .setPositiveButton("Remove", (dd, ww) -> {
                            keywordFilter.removeKeyword(keywords.get(which));
                            Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null).show();
                }
            })
            .setPositiveButton("+ Add Keyword", (d, w) -> addKeywordDialog())
            .setNegativeButton("Close", null).show();
    }

    private void addKeywordDialog() {
        EditText input = new EditText(this);
        input.setHint("e.g. politics, crypto, gossip");
        new AlertDialog.Builder(this)
            .setTitle("Add Keyword Filter")
            .setMessage("Posts containing this word will be hidden")
            .setView(input)
            .setPositiveButton("Add", (d, w) -> {
                String kw = input.getText().toString().trim();
                if (!kw.isEmpty()) {
                    keywordFilter.addKeyword(kw);
                    Toast.makeText(this, "\"" + kw + "\" blocked", Toast.LENGTH_SHORT).show();
                    injectFilters();
                }
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void showBookmarksDialog() {
        java.util.List<BookmarkManager.Bookmark> list = bookmarkManager.getAll();
        if (list.isEmpty()) { Toast.makeText(this, "No bookmarks", Toast.LENGTH_SHORT).show(); return; }
        String[] items = list.stream().map(b -> b.title.isEmpty() ? b.url : b.title).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("⭐ Bookmarks")
            .setItems(items, (d, i) -> navigateTo(list.get(i).url))
            .setNegativeButton("Close", null).show();
    }

    private void showHistoryDialog() {
        java.util.List<HistoryManager.HistoryEntry> list = historyManager.getHistory();
        if (list.isEmpty()) { Toast.makeText(this, "No history", Toast.LENGTH_SHORT).show(); return; }
        int max = Math.min(list.size(), 30);
        String[] items = list.subList(0, max).stream()
            .map(e -> e.title.isEmpty() ? e.url : e.title).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("🕐 History")
            .setItems(items, (d, i) -> navigateTo(list.get(i).url))
            .setNegativeButton("Close", null).show();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this).setTitle("Clear Data")
            .setMessage("Clear history, cookies and cache?")
            .setPositiveButton("Clear", (d, w) -> {
                webView.clearHistory(); webView.clearCache(true);
                CookieManager.getInstance().removeAllCookies(null);
                historyManager.clearHistory();
                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
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
        int id = item.getItemId();
        if (id == R.id.action_refresh) webView.reload();
        else if (id == R.id.action_bookmark) {
            String url = webView.getUrl();
            String title = webView.getTitle() != null ? webView.getTitle() : "";
            if (bookmarkManager.isBookmarked(url)) {
                bookmarkManager.remove(url);
                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
            } else {
                bookmarkManager.add(url, title);
                Toast.makeText(this, "⭐ Bookmarked!", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.action_share) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
            startActivity(Intent.createChooser(i, "Share"));
        } else if (id == R.id.action_copy_url) {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("URL", webView.getUrl()));
            Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_desktop_site) {
            WebSettings ws = webView.getSettings();
            boolean isDesktop = !ws.getUserAgentString().contains("Mobile");
            ws.setUserAgentString(isDesktop ? MOBILE_UA :
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            webView.reload();
            Toast.makeText(this, isDesktop ? "Mobile mode" : "Desktop mode", Toast.LENGTH_SHORT).show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePathCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appLock.isEnabled() && !appUnlocked) showLockScreen();
        webView.onResume(); webView.resumeTimers();
    }

    @Override
    protected void onPause() {
        super.onPause();
        appUnlocked = false; // re-lock on next resume
        webView.onPause(); webView.pauseTimers();
    }

    @Override
    protected void onStop() { super.onStop(); appUnlocked = false; }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
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
    protected void onNewIntent(Intent intent) { super.onNewIntent(intent); handleIntent(intent); }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null) navigateTo(data.toString());
    }

    @Override
    public void onBackPressed() {
        if (customView != null) { if (webView.getWebChromeClient() != null) { /* handled */ } return; }
        if (drawerLayout.isOpen()) { drawerLayout.close(); return; }
        if (webView.canGoBack()) { webView.goBack(); return; }
        super.onBackPressed();
    }
}
