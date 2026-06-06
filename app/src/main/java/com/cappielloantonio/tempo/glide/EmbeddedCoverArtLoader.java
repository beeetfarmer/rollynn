package com.cappielloantonio.tempo.glide;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.model.Download;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class EmbeddedCoverArtLoader implements ModelLoader<EmbeddedCoverArtModel, InputStream> {

    private final Context context;

    public EmbeddedCoverArtLoader(Context context) {
        this.context = context;
    }

    @Nullable
    @Override
    public LoadData<InputStream> buildLoadData(@NonNull EmbeddedCoverArtModel model,
                                                int width, int height, @NonNull Options options) {
        return new LoadData<>(
                new ObjectKey("embedded_art_" + model.getCoverArtId()),
                new EmbeddedArtFetcher(context, model.getCoverArtId())
        );
    }

    @Override
    public boolean handles(@NonNull EmbeddedCoverArtModel model) {
        return true;
    }

    @androidx.media3.common.util.UnstableApi
    private static class EmbeddedArtFetcher implements DataFetcher<InputStream> {
        private final Context context;
        private final String coverArtId;

        EmbeddedArtFetcher(Context context, String coverArtId) {
            this.context = context;
            this.coverArtId = coverArtId;
        }

        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
            try {
                java.io.File cached = CoverArtCache.getCachedFile(coverArtId);
                if (cached != null) {
                    callback.onDataReady(new java.io.FileInputStream(cached));
                    return;
                }

                DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();
                Download download = downloadDao.getOne(coverArtId);
                if (download == null) {
                    download = downloadDao.getByCoverArtId(coverArtId);
                }

                if (download == null || download.getDownloadUri() == null || download.getDownloadUri().isEmpty()) {
                    callback.onLoadFailed(new Exception("No download found for " + coverArtId));
                    return;
                }

                byte[] art = extractEmbeddedArt(download.getDownloadUri());
                if (art != null) {
                    callback.onDataReady(new ByteArrayInputStream(art));
                } else {
                    callback.onLoadFailed(new Exception("No embedded art in " + coverArtId));
                }
            } catch (Exception e) {
                callback.onLoadFailed(e);
            }
        }

        private byte[] extractEmbeddedArt(String uriString) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                Uri uri = Uri.parse(uriString);
                if ("content".equals(uri.getScheme())) {
                    retriever.setDataSource(context, uri);
                } else {
                    retriever.setDataSource(uriString);
                }
                return retriever.getEmbeddedPicture();
            } catch (Exception e) {
                return null;
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
            }
        }

        @Override
        public void cleanup() {}

        @Override
        public void cancel() {}

        @NonNull
        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.LOCAL;
        }
    }

    public static class Factory implements ModelLoaderFactory<EmbeddedCoverArtModel, InputStream> {
        private final Context context;

        public Factory(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public ModelLoader<EmbeddedCoverArtModel, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new EmbeddedCoverArtLoader(context);
        }

        @Override
        public void teardown() {}
    }
}
