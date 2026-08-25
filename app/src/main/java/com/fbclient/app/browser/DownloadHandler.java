package com.fbclient.app.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.fbclient.app.R;

/** Handles file downloads triggered from GeckoView onExternalResponse */
public class DownloadHandler {

    public static void download(Context context, String url, String contentDisposition,
                                String mimeType, String cookies, String userAgent) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null) request.setMimeType(mimeType);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            String fileName = guessFileName(url, contentDisposition);
            request.setDescription("Downloading...");
            request.setTitle(fileName);
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static String guessFileName(String url, String contentDisposition) {
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split("filename=");
            if (parts.length > 1) return parts[1].replace("\"", "").trim();
        }
        String path = Uri.parse(url).getLastPathSegment();
        return (path != null && !path.isEmpty()) ? path : "download";
    }
}
