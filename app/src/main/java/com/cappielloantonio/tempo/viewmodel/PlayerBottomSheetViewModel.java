package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.LyricsCache;
import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.FavoriteRepository;
import com.cappielloantonio.tempo.repository.LrcGetRepository;
import com.cappielloantonio.tempo.repository.LyricsRepository;
import com.cappielloantonio.tempo.repository.OpenRepository;
import com.cappielloantonio.tempo.repository.QueueRepository;
import com.cappielloantonio.tempo.repository.SongRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.LyricsList;
import com.cappielloantonio.tempo.subsonic.models.PlayQueue;
import com.cappielloantonio.tempo.lastfm.LastFm;
import com.cappielloantonio.tempo.lastfm.models.LastFmTrackResponse;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.cappielloantonio.tempo.util.OpenSubsonicExtensionsUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@OptIn(markerClass = UnstableApi.class)
public class PlayerBottomSheetViewModel extends AndroidViewModel {
    private static final String TAG = "PlayerBottomSheetViewModel";
    public static final int LYRICS_SOURCE_SERVER = 0;
    public static final int LYRICS_SOURCE_LRCLIB = 1;

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final QueueRepository queueRepository;
    private final FavoriteRepository favoriteRepository;
    private final OpenRepository openRepository;
    private final LrcGetRepository lrcGetRepository;
    private final LyricsRepository lyricsRepository;
    private final MutableLiveData<String> lyricsLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<LyricsList> lyricsListLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> lyricsCachedLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> lyricsSourceLiveData = new MutableLiveData<>(LYRICS_SOURCE_SERVER);
    private final MutableLiveData<Boolean> lyricsSourceSwitchAvailableLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> descriptionLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Child> liveMedia = new MutableLiveData<>(null);
    private final MutableLiveData<AlbumID3> liveAlbum = new MutableLiveData<>(null);
    private final MutableLiveData<ArtistID3> liveArtist = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> instantMix = new MutableLiveData<>(null);
    private final MutableLiveData<Long> lastFmScrobbleCount = new MutableLiveData<>(null);
    private final Gson gson = new Gson();
    private boolean lyricsSyncState = true;
    private LiveData<LyricsCache> cachedLyricsSource;
    private String currentSongId;
    private final Observer<LyricsCache> cachedLyricsObserver = this::onCachedLyricsChanged;
    private int preferredLyricsSource = LYRICS_SOURCE_SERVER;
    private String serverLyrics;
    private LyricsList serverLyricsList;
    private String lrcGetLyrics;
    private LyricsList lrcGetLyricsList;


    public PlayerBottomSheetViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        queueRepository = new QueueRepository();
        favoriteRepository = new FavoriteRepository();
        openRepository = new OpenRepository();
        lrcGetRepository = new LrcGetRepository();
        lyricsRepository = new LyricsRepository();
    }

    public LiveData<List<Queue>> getQueueSong() {
        return queueRepository.getLiveQueue();
    }

    public void setFavorite(Context context, Child media) {
        if (media != null) {
            if (media.getStarred() != null) {
                if (NetworkUtil.isOffline()) {
                    removeFavoriteOffline(media);
                } else {
                    removeFavoriteOnline(media);
                }
            } else {
                if (NetworkUtil.isOffline()) {
                    setFavoriteOffline(media);
                } else {
                    setFavoriteOnline(context, media);
                }
            }
        }
    }

    private void removeFavoriteOffline(Child media) {
        favoriteRepository.starLater(media.getId(), null, null, false);
        media.setStarred(null);
        liveMedia.postValue(media);
        MediaManager.postFavoriteEvent(media.getId(), null);
        songRepository.updateDownloadStarred(media.getId(), null);
    }

    private void removeFavoriteOnline(Child media) {
        favoriteRepository.unstar(media.getId(), null, null, new StarCallback() {
            @Override
            public void onError() {
                favoriteRepository.starLater(media.getId(), null, null, false);
            }
        });
        media.setStarred(null);
        liveMedia.postValue(media);
        MediaManager.postFavoriteEvent(media.getId(), null);
        songRepository.updateDownloadStarred(media.getId(), null);
    }

    private void setFavoriteOffline(Child media) {
        favoriteRepository.starLater(media.getId(), null, null, true);
        media.setStarred(new Date());
        liveMedia.postValue(media);
        MediaManager.postFavoriteEvent(media.getId(), media.getStarred());
        songRepository.updateDownloadStarred(media.getId(), media.getStarred());
    }

    private void setFavoriteOnline(Context context, Child media) {
        favoriteRepository.star(media.getId(), null, null, new StarCallback() {
            @Override
            public void onError() {
                favoriteRepository.starLater(media.getId(), null, null, true);
            }
        });

        media.setStarred(new Date());
        liveMedia.postValue(media);
        MediaManager.postFavoriteEvent(media.getId(), media.getStarred());
        songRepository.updateDownloadStarred(media.getId(), media.getStarred());

        if (Preferences.isStarredSyncEnabled() && Preferences.getDownloadDirectoryUri() == null) {
            DownloadUtil.getDownloadTracker(context).download(
                    MappingUtil.mapDownload(media),
                    new Download(media)
            );
        }
    }

     public LiveData<String> getLiveLyrics() {
        return lyricsLiveData;
    }

    public LiveData<LyricsList> getLiveLyricsList() {
        return lyricsListLiveData;
    }

    public LiveData<Integer> getLyricsSource() {
        return lyricsSourceLiveData;
    }

    public LiveData<Boolean> getLyricsSourceSwitchAvailable() {
        return lyricsSourceSwitchAvailableLiveData;
    }

    public boolean switchLyricsSource() {
        if (!canSwitchLyricsSource()) {
            return false;
        }

        preferredLyricsSource = preferredLyricsSource == LYRICS_SOURCE_SERVER
                ? LYRICS_SOURCE_LRCLIB
                : LYRICS_SOURCE_SERVER;

        publishLyricsByPreferredSource();
        return true;
    }

    public void refreshMediaInfo(LifecycleOwner owner, Child media) {
        resetLyricsSources();
        lyricsCachedLiveData.postValue(false);

        clearCachedLyricsObserver();

        String songId = media != null ? media.getId() : currentSongId;

        if (TextUtils.isEmpty(songId) || owner == null) {
            return;
        }

        currentSongId = songId;

        observeCachedLyrics(owner, songId);

        LyricsCache cachedLyrics = lyricsRepository.getLyrics(songId);
        if (cachedLyrics != null) {
            onCachedLyricsChanged(cachedLyrics);
        }

        if (NetworkUtil.isOffline() || media == null) {
            return;
        }

        if (OpenSubsonicExtensionsUtil.isSongLyricsExtensionAvailable()) {
            openRepository.getLyricsBySongId(media.getId()).observe(owner, lyricsList -> {
                serverLyricsList = hasStructuredLyrics(lyricsList) ? lyricsList : null;
                serverLyrics = null;
                publishLyricsByPreferredSource();

                if (shouldAutoDownloadLyrics() && hasStructuredLyrics(serverLyricsList)) {
                    saveLyricsToCache(media, null, lyricsList);
                }
            });
        } else {
            songRepository.getSongLyrics(media).observe(owner, lyrics -> {
                serverLyrics = lyrics;
                serverLyricsList = null;
                publishLyricsByPreferredSource();

                if (shouldAutoDownloadLyrics() && !TextUtils.isEmpty(lyrics)) {
                    saveLyricsToCache(media, lyrics, null);
                }
            });
        }

        lrcGetRepository.getLyrics(media).observe(owner, result -> {
            if (result == null) {
                return;
            }

            lrcGetLyrics = result.getPlainLyrics();
            lrcGetLyricsList = hasStructuredLyrics(result.getSyncedLyrics()) ? result.getSyncedLyrics() : null;
            publishLyricsByPreferredSource();

            if (shouldAutoDownloadLyrics() && !hasAnyServerLyrics()) {
                saveLyricsToCache(media, lrcGetLyrics, lrcGetLyricsList);
            }
        });
    }

    public LiveData<Child> getLiveMedia() {
        return liveMedia;
    }

    public void setLiveMedia(LifecycleOwner owner, String mediaType, String mediaId) {
        currentSongId = mediaId;

        if (!TextUtils.isEmpty(mediaId)) {
            refreshMediaInfo(owner, null);
        } else {
            clearCachedLyricsObserver();
            resetLyricsSources();
            lyricsCachedLiveData.postValue(false);
        }

        if (mediaType != null) {
            switch (mediaType) {
                case Constants.MEDIA_TYPE_MUSIC:
                    songRepository.getSong(mediaId).observe(owner, liveMedia::postValue);
                    descriptionLiveData.postValue(null);
                    break;
                case Constants.MEDIA_TYPE_PODCAST:
                    liveMedia.postValue(null);
                    break;
                default:
                    liveMedia.postValue(null);
                    break;
            }
        } else {
            liveMedia.postValue(null);
        }
    }

    public LiveData<AlbumID3> getLiveAlbum() {
        return liveAlbum;
    }

    public void setLiveAlbum(LifecycleOwner owner, String mediaType, String AlbumId) {
        if (mediaType != null) {
            switch (mediaType) {
                case Constants.MEDIA_TYPE_MUSIC:
                    albumRepository.getAlbum(AlbumId).observe(owner, liveAlbum::postValue);
                    break;
                case Constants.MEDIA_TYPE_PODCAST:
                    liveAlbum.postValue(null);
                    break;
            }
        }
    }

    public LiveData<ArtistID3> getLiveArtist() {
        return liveArtist;
    }

    public void setLiveArtist(LifecycleOwner owner, String mediaType, String ArtistId) {
        if (mediaType != null) {
            switch (mediaType) {
                case Constants.MEDIA_TYPE_MUSIC:
                    artistRepository.getArtist(ArtistId).observe(owner, liveArtist::postValue);
                    break;
                case Constants.MEDIA_TYPE_PODCAST:
                    liveArtist.postValue(null);
                    break;
            }
        }
    }

    public void setLiveDescription(String description) {
        descriptionLiveData.postValue(description);
    }

    public LiveData<String> getLiveDescription() {
        return descriptionLiveData;
    }

    public LiveData<List<Child>> getMediaInstantMix(LifecycleOwner owner, Child media) {
        instantMix.setValue(Collections.emptyList());

        songRepository.getInstantMix(media.getId(), Constants.SeedType.TRACK, 20).observe(owner, instantMix::postValue);

        return instantMix;
    }

    public LiveData<PlayQueue> getPlayQueue() {
        return queueRepository.getPlayQueue();
    }

    public boolean savePlayQueue() {
        Child media = getLiveMedia().getValue();
        List<Child> queue = queueRepository.getMedia();
        List<String> ids = queue.stream().map(Child::getId).collect(Collectors.toList());

        if (media != null) {
            // TODO: We need to get the actual playback position here
            Log.d(TAG, "Saving play queue - Current: " + media.getId() + ", Items: " + ids.size());
            queueRepository.savePlayQueue(ids, media.getId(), 0); // Still hardcoded to 0 for now
            return true;
        }
        return false;
    }
    private void observeCachedLyrics(LifecycleOwner owner, String songId) {
        if (TextUtils.isEmpty(songId)) {
            return;
        }

        cachedLyricsSource = lyricsRepository.observeLyrics(songId);
        cachedLyricsSource.observe(owner, cachedLyricsObserver);
    }

    private void clearCachedLyricsObserver() {
        if (cachedLyricsSource != null) {
            cachedLyricsSource.removeObserver(cachedLyricsObserver);
            cachedLyricsSource = null;
        }
    }

    private void onCachedLyricsChanged(LyricsCache lyricsCache) {
        if (lyricsCache == null) {
            lyricsCachedLiveData.postValue(false);
            return;
        }

        lyricsCachedLiveData.postValue(true);

        if (!TextUtils.isEmpty(lyricsCache.getStructuredLyrics())) {
            try {
                LyricsList cachedList = gson.fromJson(lyricsCache.getStructuredLyrics(), LyricsList.class);
                serverLyricsList = cachedList;
                serverLyrics = null;
            } catch (Exception exception) {
                serverLyricsList = null;
                serverLyrics = lyricsCache.getLyrics();
            }
        } else {
            serverLyricsList = null;
            serverLyrics = lyricsCache.getLyrics();
        }

        publishLyricsByPreferredSource();
    }

    private void resetLyricsSources() {
        preferredLyricsSource = LYRICS_SOURCE_SERVER;
        serverLyrics = null;
        serverLyricsList = null;
        lrcGetLyrics = null;
        lrcGetLyricsList = null;

        lyricsSourceLiveData.postValue(LYRICS_SOURCE_SERVER);
        lyricsSourceSwitchAvailableLiveData.postValue(false);
        lyricsLiveData.postValue(null);
        lyricsListLiveData.postValue(null);
    }

    private void publishLyricsByPreferredSource() {
        boolean hasServer = hasAnyServerLyrics();
        boolean hasLrcGet = hasAnyLrcGetLyrics();

        lyricsSourceSwitchAvailableLiveData.postValue(hasServer && hasLrcGet);

        int effectiveSource = preferredLyricsSource;
        if (effectiveSource == LYRICS_SOURCE_SERVER && !hasServer && hasLrcGet) {
            effectiveSource = LYRICS_SOURCE_LRCLIB;
        } else if (effectiveSource == LYRICS_SOURCE_LRCLIB && !hasLrcGet && hasServer) {
            effectiveSource = LYRICS_SOURCE_SERVER;
        }

        lyricsSourceLiveData.postValue(effectiveSource);

        if (effectiveSource == LYRICS_SOURCE_LRCLIB && hasLrcGet) {
            lyricsListLiveData.postValue(lrcGetLyricsList);
            lyricsLiveData.postValue(lrcGetLyrics);
            return;
        }

        if (hasServer) {
            lyricsListLiveData.postValue(serverLyricsList);
            lyricsLiveData.postValue(serverLyrics);
            return;
        }

        if (hasLrcGet) {
            lyricsListLiveData.postValue(lrcGetLyricsList);
            lyricsLiveData.postValue(lrcGetLyrics);
            return;
        }

        lyricsListLiveData.postValue(null);
        lyricsLiveData.postValue(null);
    }

    private boolean canSwitchLyricsSource() {
        return hasAnyServerLyrics() && hasAnyLrcGetLyrics();
    }

    private boolean hasAnyServerLyrics() {
        return hasStructuredLyrics(serverLyricsList) || hasText(serverLyrics);
    }

    private boolean hasAnyLrcGetLyrics() {
        return hasStructuredLyrics(lrcGetLyricsList) || hasText(lrcGetLyrics);
    }

    private boolean hasText(String value) {
        return !TextUtils.isEmpty(value) && !value.trim().isEmpty();
    }

    private void saveLyricsToCache(Child media, String lyrics, LyricsList lyricsList) {
        if (media == null) {
            return;
        }

        if ((lyricsList == null || !hasStructuredLyrics(lyricsList)) && TextUtils.isEmpty(lyrics)) {
            return;
        }

        LyricsCache lyricsCache = new LyricsCache(media.getId());
        lyricsCache.setArtist(media.getArtist());
        lyricsCache.setTitle(media.getTitle());
        lyricsCache.setUpdatedAt(System.currentTimeMillis());

        if (lyricsList != null && hasStructuredLyrics(lyricsList)) {
            lyricsCache.setStructuredLyrics(gson.toJson(lyricsList));
            lyricsCache.setLyrics(null);
        } else {
            lyricsCache.setLyrics(lyrics);
            lyricsCache.setStructuredLyrics(null);
        }

        lyricsRepository.insert(lyricsCache);
        lyricsCachedLiveData.postValue(true);
    }

    private boolean hasStructuredLyrics(LyricsList lyricsList) {
        return lyricsList != null
                && lyricsList.getStructuredLyrics() != null
                && !lyricsList.getStructuredLyrics().isEmpty()
                && lyricsList.getStructuredLyrics().get(0) != null
                && lyricsList.getStructuredLyrics().get(0).getLine() != null
                && !lyricsList.getStructuredLyrics().get(0).getLine().isEmpty();
    }

    private boolean shouldAutoDownloadLyrics() {
        return Preferences.isAutoDownloadLyricsEnabled();
    }

    public boolean downloadCurrentLyrics() {
        Child media = getLiveMedia().getValue();
        if (media == null) {
            return false;
        }

        LyricsList lyricsList = lyricsListLiveData.getValue();
        String lyrics = lyricsLiveData.getValue();

        if ((lyricsList == null || !hasStructuredLyrics(lyricsList)) && TextUtils.isEmpty(lyrics)) {
            return false;
        }

        saveLyricsToCache(media, lyrics, lyricsList);
        return true;
    }

    public LiveData<Boolean> getLyricsCachedState() {
        return lyricsCachedLiveData;
    }

    public void changeSyncLyricsState() {
        lyricsSyncState = !lyricsSyncState;
    }

    public boolean getSyncLyricsState() {
        return lyricsSyncState;
    }

    public LiveData<Long> getLastFmScrobbleCount() {
        return lastFmScrobbleCount;
    }

    public void fetchLastFmScrobbleCount(String artist, String track) {
        lastFmScrobbleCount.postValue(null);

        String username = Preferences.getLastFmUser();
        String apiKey = Preferences.getLastFmApiKey();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(artist) || TextUtils.isEmpty(track)) {
            return;
        }

        String firstArtist = artist.split("\\s*[,/;&\u2022]\\s*|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+")[0].trim();
        if (firstArtist.isEmpty()) {
            return;
        }

        LastFm.INSTANCE.getTrackClient().getTrackInfo(firstArtist, track, username, apiKey).enqueue(new Callback<LastFmTrackResponse>() {
            @Override
            public void onResponse(Call<LastFmTrackResponse> call, Response<LastFmTrackResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getTrack() != null) {
                    String countStr = response.body().getTrack().getUserPlayCount();
                    if (countStr != null) {
                        try {
                            lastFmScrobbleCount.postValue(Long.parseLong(countStr));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<LastFmTrackResponse> call, Throwable t) {
                Log.e(TAG, "Last.fm API error", t);
            }
        });
    }
}
