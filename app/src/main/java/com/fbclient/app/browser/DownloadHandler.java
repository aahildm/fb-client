package com.fbclient.app.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.fbclient.app.R;

/** Handles file downloads from WebView */
public class DownloadHandler {

    public static void download(Context context, String url, String contentDisposition,
                                String mimeType, String cookies, String userAgent) {
        if (url == null || url.startsWith("blob:")) {
            Toast.makeText(context, "Long-press the image to save it", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null) request.setMimeType(mimeType);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            String fileName = guessFileName(url, contentDisposition, mimeType);
            request.setTitle(fileName);
            request.setDescription("Downloading...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(context, "Download started: " + fileName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String guessFileName(String url, String contentDisposition, String mimeType) {
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split("filename=");
            if (parts.length > 1) return parts[1].replace("\"", "").trim();
        }
        String path = Uri.parse(url).getLastPathSegment();
        if (path != null && path.contains(".")) return path;
        String ext = ".bin";
        if (mimeType != null) {
            if (mimeType.contains("jpeg") || mimeType.contains("jpg")) ext = ".jpg";
            else if (mimeType.contains("png")) ext = ".png";
            else if (mimeType.contains("mp4")) ext = ".mp4";
            else if (mimeType.contains("pdf")) ext = ".pdf";
        }
        return "download_" + System.currentTimeMillis() + ext;
    }
}
