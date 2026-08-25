# GeckoView
-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.**

# Extension model
-keep class com.fbclient.app.extensions.** { *; }

# Gson - keep model classes
-keep class com.fbclient.app.browser.HistoryManager$HistoryEntry { *; }
-keep class com.fbclient.app.browser.BookmarkManager$Bookmark { *; }

# snakeyaml pulled in transitively - java.beans not on Android
-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.**

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
