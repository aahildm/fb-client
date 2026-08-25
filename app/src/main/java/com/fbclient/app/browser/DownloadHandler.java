package com.fbclient.app.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import com.fbclient.app.R;

import org.mozilla.geckoview.GeckoSession;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;

public class DownloadHandler {

    /** Handle normal http/https downloads */
    public static void download(Context context, String url, String contentDisposition,
                                String mimeType, String cookies, String userAgent) {
        if (url == null) return;

        // blob: URLs can't be handled by DownloadManager — need JS extraction
        if (url.startsWith("blob:")) {
            Toast.makeText(context,
                "Use long-press → Save on the image instead", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null) request.setMimeType(mimeType);
            if (cookies != null && !cookies.isEmpty())
                request.addRequestHeader("Cookie", cookies);
            if (userAgent != null)
                request.addRequestHeader("User-Agent", userAgent);
            String fileName = guessFileName(url, contentDisposition, mimeType);
            request.setDescription("Downloading...");
            request.setTitle(fileName);
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
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

    /** Save base64 data (from blob JS extraction) directly to Downloads folder */
    public static void saveBase64(Context context, String base64Data,
                                  String mimeType, String fileName) {
        new Thread(() -> {
            try {
                // Strip data URI prefix if present: data:image/jpeg;base64,....
                String data = base64Data;
                if (data.contains(",")) data = data.substring(data.indexOf(",") + 1);

                byte[] bytes = Base64.getDecoder().decode(data);
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(bytes);
                }

                // Notify DownloadManager so file appears in gallery
                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.fromFile(file));
                    req.setTitle(fileName);
                    req.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                }

                android.os.Handler mainHandler = new android.os.Handler(
                    android.os.Looper.getMainLooper());
                mainHandler.post(() ->
                    Toast.makeText(context, "Saved: " + fileName, Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                android.os.Handler mainHandler = new android.os.Handler(
                    android.os.Looper.getMainLooper());
                mainHandler.post(() ->
                    Toast.makeText(context, "Save failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static String guessFileName(String url, String contentDisposition, String mimeType) {
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split("filename=");
            if (parts.length > 1) return parts[1].replace("\"", "").trim();
        }
        String path = Uri.parse(url).getLastPathSegment();
        if (path != null && !path.isEmpty() && path.contains(".")) return path;

        // Guess extension from mime type
        String ext = ".bin";
        if (mimeType != null) {
            if (mimeType.contains("jpeg") || mimeType.contains("jpg")) ext = ".jpg";
            else if (mimeType.contains("png")) ext = ".png";
            else if (mimeType.contains("gif")) ext = ".gif";
            else if (mimeType.contains("mp4")) ext = ".mp4";
            else if (mimeType.contains("pdf")) ext = ".pdf";
        }
        return "download_" + System.currentTimeMillis() + ext;
    }
}
