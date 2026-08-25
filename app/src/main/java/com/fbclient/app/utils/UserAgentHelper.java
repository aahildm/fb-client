package com.fbclient.app.utils;

/** User-agent strings for different modes */
public class UserAgentHelper {
    // Desktop UA for full Facebook desktop experience
    public static final String DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0";

    // Mobile UA (default – Facebook mobile site)
    public static final String MOBILE_UA =
        "Mozilla/5.0 (Android 13; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0";

    // Facebook app UA (bypasses some restrictions)
    public static final String FB_APP_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
        + "Chrome/120.0.0.0 Mobile Safari/537.36 [FBAN/FB4A;FBDV/Pixel 7;FBMD/Google;FBSN/Android;FBSV/13;FBSS/3;FBID/phone;FBLC/en_US;FBOP/1]";

    public static String get(int mode) {
        switch (mode) {
            case 1: return DESKTOP_UA;
            case 2: return FB_APP_UA;
            default: return MOBILE_UA;
        }
    }
}
