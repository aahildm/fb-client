package com.fbclient.app.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.fbclient.app.R;

/** Handles file downloads triggered from GeckoView */
public class DownloadHandler {

    /** Start a download using Android's DownloadManager */
    public static void download(Context context, String url, String contentDisposition,
                                String mimeType, String cookies, String userAgent) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setMimeType(mimeType);
        request.addRequestHeader("Cookie", cookies);
        request.addRequestHeader("User-Agent", userAgent);
        request.setDescription("Downloading file...");
        request.setTitle(guessFileName(url, contentDisposition));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessFileName(url, contentDisposition));

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null) {
            dm.enqueue(request);
            Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show();
        }
    }

    private static String guessFileName(String url, String contentDisposition) {
        // Extract filename from content-disposition header or URL
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split("filename=");
            if (parts.length > 1) {
                return parts[1].replace("\"", "").trim();
            }
        }
        String path = Uri.parse(url).getLastPathSegment();
        return (path != null && !path.isEmpty()) ? path : "download";
    }
}
