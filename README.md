# FB Client — GeckoView Facebook Browser

A privacy-respecting Facebook Android client built on Mozilla's GeckoView engine (Firefox), with full WebExtension support.

## Features

| Feature | Detail |
|---|---|
| Engine | GeckoView 119 (Firefox/Mozilla) |
| Extension support | Install any Firefox addon from addons.mozilla.org |
| Multi-tab | Full tab management with incognito/private tabs |
| Downloads | Android DownloadManager integration |
| History & Bookmarks | Persistent, stored locally |
| User Agent | Mobile / Desktop / FB-App switcher |
| Dark mode | System-aware dark theme |
| Privacy | Ad-block via uBlock Origin extension, cookie control |
| Deep links | Handles `facebook.com` share links |
| Find in page | GeckoSession.FinderFindFlags |
| Pull to refresh | SwipeRefreshLayout |
| Permissions | Camera, mic, notifications, geolocation (ask on demand) |

## Build locally

```bash
# Prerequisites: JDK 17, Android SDK (API 34)
git clone https://github.com/YOUR_USERNAME/fb-client.git
cd fb-client
chmod +x gradlew
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions — Auto Release APK

Push a version tag to trigger an automatic build + GitHub Release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

This runs `.github/workflows/release.yml` which:
1. Builds a release APK with `./gradlew assembleRelease`
2. Optionally signs it (see below)
3. Creates a GitHub Release with the APK attached

You can also trigger it manually from **Actions → Build & Release APK → Run workflow**.

### Optional: APK Signing

For a signed APK (required for Google Play), add these repository secrets under **Settings → Secrets → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 your.jks` output |
| `KEY_ALIAS` | your key alias |
| `KEY_PASSWORD` | key password |
| `STORE_PASSWORD` | keystore password |

Without these secrets, the unsigned release APK is still released and can be sideloaded.

## Install Extensions

1. Open the app → Menu (☰) → **Extensions**
2. Tap **+ Install Extension**
3. Paste an AMO URL e.g.:
   - `https://addons.mozilla.org/firefox/addon/ublock-origin/` (ad blocker)
   - `https://addons.mozilla.org/firefox/addon/privacy-badger17/`
   - Any `.xpi` direct download URL

## Architecture

```
app/
├── browser/
│   ├── GeckoProvider.java      # Singleton GeckoRuntime
│   ├── TabManager.java         # Multi-tab (GeckoSession per tab)
│   ├── BrowserTab.java         # Tab model
│   ├── BookmarkManager.java    # Bookmarks (SharedPrefs + Gson)
│   ├── HistoryManager.java     # History (SharedPrefs + Gson)
│   └── DownloadHandler.java    # Android DownloadManager
├── extensions/
│   ├── Extension.java          # Extension model
│   └── ExtensionManager.java   # WebExtension lifecycle
├── ui/
│   ├── MainActivity.java       # Browser UI + GeckoView
│   ├── ExtensionManagerActivity.java
│   └── SettingsActivity.java
└── utils/
    ├── AppPrefs.java           # Centralized SharedPreferences
    └── UserAgentHelper.java    # UA strings
```
