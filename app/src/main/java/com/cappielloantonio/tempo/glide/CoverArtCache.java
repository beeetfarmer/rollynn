package com.cappielloantonio.tempo.glide;

import android.content.Context;

import com.cappielloantonio.tempo.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoverArtCache {

    private static final String CACHE_DIR = "cover_art_cache";
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    public static File getCacheDir() {
        File dir = new File(App.getContext().getFilesDir(), CACHE_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getCachedFile(String coverArtId) {
        if (coverArtId == null) return null;
        File file = new File(getCacheDir(), coverArtId + ".jpg");
        return file.exists() ? file : null;
    }

    public static void cacheInBackground(String coverArtId) {
        if (coverArtId == null) return;
        File file = new File(getCacheDir(), coverArtId + ".jpg");
        if (file.exists()) return;

        String url = CustomGlideRequest.createUrl(coverArtId, 512);
        executor.execute(() -> downloadToFile(url, file));
    }

    private static void downloadToFile(String urlString, File target) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            if (connection.getResponseCode() != 200) return;

            File temp = new File(target.getParent(), target.getName() + ".tmp");
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            temp.renameTo(target);
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @androidx.media3.common.util.UnstableApi
    public static void cacheAllDownloads() {
        executor.execute(() -> {
            try {
                java.util.List<com.cappielloantonio.tempo.model.Download> downloads =
                        com.cappielloantonio.tempo.database.AppDatabase.getInstance().downloadDao().getAllSync();
                if (downloads == null) return;
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (com.cappielloantonio.tempo.model.Download d : downloads) {
                    String coverArtId = d.getCoverArtId();
                    if (coverArtId != null && seen.add(coverArtId)) {
                        File file = new File(getCacheDir(), coverArtId + ".jpg");
                        if (!file.exists()) {
                            String url = CustomGlideRequest.createUrl(coverArtId, 512);
                            downloadToFile(url, file);
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public static void delete(String coverArtId) {
        if (coverArtId == null) return;
        File file = new File(getCacheDir(), coverArtId + ".jpg");
        if (file.exists()) file.delete();
    }
}
