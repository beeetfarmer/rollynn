package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.model.Chronology;
import com.cappielloantonio.tempo.model.Favorite;
import com.cappielloantonio.tempo.model.HomeSector;
import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.ChronologyRepository;
import com.cappielloantonio.tempo.repository.FavoriteRepository;
import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.repository.SharingRepository;
import com.cappielloantonio.tempo.repository.SongRepository;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.subsonic.models.Share;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.Constants.SeedType;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class HomeViewModel extends AndroidViewModel {
    private static final String TAG = "HomeViewModel";

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final ChronologyRepository chronologyRepository;
    private final FavoriteRepository favoriteRepository;
    private final PlaylistRepository playlistRepository;
    private final SharingRepository sharingRepository;

    private final StarredAlbumsSyncViewModel albumsSyncViewModel;
    private final StarredArtistsSyncViewModel artistSyncViewModel;

    private final MutableLiveData<List<Child>> dicoverSongSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> newReleasedAlbum = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> starredTracksSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> starredArtistsSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> bestOfArtists = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> starredTracks = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> starredAlbums = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> starredArtists = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> mostPlayedAlbumSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> recentlyPlayedAlbumSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<Integer>> years = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> recentlyAddedAlbumSample = new MutableLiveData<>(null);

    private final MutableLiveData<List<Chronology>> thisGridTopSong = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> history = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> mediaInstantMix = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> artistInstantMix = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> artistBestOf = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> recentlyPlayedArtists = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> topPlayedArtists = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> topPlayedSongs = new MutableLiveData<>(null);
    private final MutableLiveData<List<Playlist>> pinnedPlaylists = new MutableLiveData<>(null);
    private final MutableLiveData<List<Share>> shares = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> allStarredTracks = new MutableLiveData<>(null);
    private boolean fetchingAllStarred = false;

    private List<HomeSector> sectors;

    public HomeViewModel(@NonNull Application application) {
        super(application);

        setHomeSectorList();

        songRepository = new SongRepository();
        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        chronologyRepository = new ChronologyRepository();
        favoriteRepository = new FavoriteRepository();
        playlistRepository = new PlaylistRepository();
        sharingRepository = new SharingRepository();

        albumsSyncViewModel = new StarredAlbumsSyncViewModel(application);
        artistSyncViewModel = new StarredArtistsSyncViewModel(application);

        setOfflineFavorite();

        playlistRepository.getPlaylistUpdateTrigger().observeForever(needsRefresh -> {
            if (needsRefresh != null && needsRefresh) {
                playlistRepository.updatePinnedPlaylists();
            }
        });
    }

    public LiveData<List<Child>> getDiscoverSongSample(LifecycleOwner owner) {
        if (dicoverSongSample.getValue() == null) {
            songRepository.getRandomSample(10, null, null).observe(owner, dicoverSongSample::postValue);
        }

        return dicoverSongSample;
    }

    public LiveData<List<Child>> getRandomShuffleSample() {
        return songRepository.getRandomSample(100, null, null);
    }

    public LiveData<List<Chronology>> getChronologySample(LifecycleOwner owner) {
        if (thisGridTopSong.getValue() == null) {
            Calendar cal = Calendar.getInstance();
            String server = Preferences.getServerId();

            int currentWeek = cal.get(Calendar.WEEK_OF_YEAR);
            long start = cal.getTimeInMillis();

            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 1);
            long end = cal.getTimeInMillis();

            chronologyRepository.getChronology(server, start, end).observe(owner, thisGridTopSong::postValue);
        }
        return thisGridTopSong;
    }

    public LiveData<List<Child>> getHistory(LifecycleOwner owner) {
        if (history.getValue() == null) {
            songRepository.getRecentlyPlayedSongs(20).observe(owner, history::postValue);
        }
        return history;
    }

    public LiveData<List<ArtistID3>> getRecentlyPlayedArtists(LifecycleOwner owner) {
        if (recentlyPlayedArtists.getValue() == null) {
            artistRepository.getRecentlyPlayedArtists(20).observe(owner, recentlyPlayedArtists::postValue);
        }
        return recentlyPlayedArtists;
    }

    public LiveData<List<ArtistID3>> getTopPlayedArtists(LifecycleOwner owner) {
        if (topPlayedArtists.getValue() == null) {
            artistRepository.getTopPlayedArtists(20).observe(owner, topPlayedArtists::postValue);
        }
        return topPlayedArtists;
    }

    public LiveData<List<Child>> getTopPlayedSongs(LifecycleOwner owner) {
        if (topPlayedSongs.getValue() == null) {
            songRepository.getTopPlayedSongs(20).observe(owner, topPlayedSongs::postValue);
        }
        return topPlayedSongs;
    }

    public LiveData<List<AlbumID3>> getRecentlyReleasedAlbums(LifecycleOwner owner) {
        if (newReleasedAlbum.getValue() == null) {
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);

            albumRepository.getAlbums("byYear", 500, currentYear, currentYear).observe(owner, albums -> {
                if (albums != null) {
                    albums.sort(Comparator.comparing(AlbumID3::getCreated).reversed());
                    newReleasedAlbum.postValue(albums.subList(0, Math.min(20, albums.size())));
                }
            });
        }

        return newReleasedAlbum;
    }

    public LiveData<List<Child>> getStarredTracksSample(LifecycleOwner owner) {
        if (starredTracksSample.getValue() == null) {
            songRepository.getStarredSongs(true, 10).observe(owner, starredTracksSample::postValue);
        }

        return starredTracksSample;
    }

    public LiveData<List<ArtistID3>> getStarredArtistsSample(LifecycleOwner owner) {
        if (starredArtistsSample.getValue() == null) {
            artistRepository.getStarredArtists(true, 10).observe(owner, starredArtistsSample::postValue);
        }

        return starredArtistsSample;
    }

    public LiveData<List<ArtistID3>> getBestOfArtists(LifecycleOwner owner) {
        if (bestOfArtists.getValue() == null) {
            artistRepository.getStarredArtists(true, 20).observe(owner, bestOfArtists::postValue);
        }

        return bestOfArtists;
    }

    public LiveData<List<Child>> getStarredTracks(LifecycleOwner owner) {
        if (starredTracks.getValue() == null) {
            songRepository.getStarredSongs(false, -1).observe(owner, songs -> {
                if (songs == null) {
                    return;
                }

                List<Child> sampled = sampleRandomItems(songs, 20);
                if (!sampled.isEmpty() || starredTracks.getValue() == null || starredTracks.getValue().isEmpty()) {
                    starredTracks.postValue(sampled);
                }
            });
        }

        return starredTracks;
    }

    public LiveData<List<AlbumID3>> getStarredAlbums(LifecycleOwner owner) {
        if (starredAlbums.getValue() == null) {
            albumRepository.getStarredAlbums(true, 20).observe(owner, starredAlbums::postValue);
        }

        return starredAlbums;
    }

    public LiveData<List<Child>> getAllStarredAlbumSongs() {
        return albumsSyncViewModel.getAllStarredAlbumSongs();
    }

    public LiveData<List<Child>> getAllStarredArtistSongs() {
        return artistSyncViewModel.getAllStarredArtistSongs();
    }

    public LiveData<List<ArtistID3>> getStarredArtists(LifecycleOwner owner) {
        if (starredArtists.getValue() == null) {
            artistRepository.getStarredArtists(true, 20).observe(owner, starredArtists::postValue);
        }

        return starredArtists;
    }

    public LiveData<List<Integer>> getYearList(LifecycleOwner owner) {
        if (years.getValue() == null) {
            albumRepository.getDecades().observe(owner, years::postValue);
        }

        return years;
    }

    public LiveData<List<AlbumID3>> getMostPlayedAlbums(LifecycleOwner owner) {
        if (mostPlayedAlbumSample.getValue() == null) {
            albumRepository.getAlbums("frequent", 20, null, null).observe(owner, mostPlayedAlbumSample::postValue);
        }

        return mostPlayedAlbumSample;
    }

    public LiveData<List<AlbumID3>> getMostRecentlyAddedAlbums(LifecycleOwner owner) {
        if (recentlyAddedAlbumSample.getValue() == null) {
            albumRepository.getAlbums("newest", 20, null, null).observe(owner, recentlyAddedAlbumSample::postValue);
        }

        return recentlyAddedAlbumSample;
    }

    public LiveData<List<AlbumID3>> getRecentlyPlayedAlbumList(LifecycleOwner owner) {
        if (recentlyPlayedAlbumSample.getValue() == null) {
            albumRepository.getAlbums("recent", 20, null, null).observe(owner, recentlyPlayedAlbumSample::postValue);
        }

        return recentlyPlayedAlbumSample;
    }

    public LiveData<List<Child>> getMediaInstantMix(LifecycleOwner owner, Child media) {
        mediaInstantMix.setValue(Collections.emptyList());

        songRepository.getInstantMix(media.getId(), SeedType.TRACK, 20).observe(owner, mediaInstantMix::postValue);

        return mediaInstantMix;
    }

    public LiveData<List<Child>> getArtistInstantMix(LifecycleOwner owner, ArtistID3 artist) {
        artistInstantMix.setValue(Collections.emptyList());

        artistRepository.getTopSongs(artist.getName(), 10).observe(owner, artistInstantMix::postValue);

        return artistInstantMix;
    }

    public LiveData<List<Child>> getArtistBestOf(LifecycleOwner owner, ArtistID3 artist) {
        artistBestOf.setValue(Collections.emptyList());

        artistRepository.getTopSongs(artist.getName(), 10).observe(owner, artistBestOf::postValue);

        return artistBestOf;
    }

    public LiveData<List<Playlist>> getPinnedPlaylists(LifecycleOwner owner) {
        return playlistRepository.getPinnedPlaylists();
    }

    public LiveData<List<Share>> getShares(LifecycleOwner owner) {
        if (shares.getValue() == null) {
            sharingRepository.getShares().observe(owner, shares::postValue);
        }

        return shares;
    }

    public LiveData<List<Child>> getAllStarredTracks() {
        if (!fetchingAllStarred && allStarredTracks.getValue() == null) {
            fetchingAllStarred = true;
            songRepository.getStarredSongs(false, -1).observeForever(new androidx.lifecycle.Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> songs) {
                    songRepository.getStarredSongs(false, -1).removeObserver(this);
                    allStarredTracks.postValue(songs);
                    fetchingAllStarred = false;
                }
            });
        }
        return allStarredTracks;
    }

    public void refreshAllStarredTracks() {
        fetchingAllStarred = false;
        allStarredTracks.setValue(null);
        getAllStarredTracks();
    }

    public void changeChronologyPeriod(LifecycleOwner owner, int period) {
        Calendar cal = Calendar.getInstance();
        String server = Preferences.getServerId();
        int currentWeek = cal.get(Calendar.WEEK_OF_YEAR);

        long start = 0;
        long end = 0;

        if (period == 0) {
            start = cal.getTimeInMillis();
            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 1);
            end = cal.getTimeInMillis();
        } else if (period == 1) {
            start = cal.getTimeInMillis();
            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 4);
            end = cal.getTimeInMillis();
        } else if (period == 2) {
            start = cal.getTimeInMillis();
            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 52);
            end = cal.getTimeInMillis();
        }

        chronologyRepository.getChronology(server, start, end).observe(owner, thisGridTopSong::postValue);
    }

    public void refreshDiscoverySongSample(LifecycleOwner owner) {
        songRepository.getRandomSample(10, null, null).observe(owner, dicoverSongSample::postValue);
    }

    public void refreshSimilarSongSample(LifecycleOwner owner) {
        songRepository.getStarredSongs(true, 10).observe(owner, starredTracksSample::postValue);
    }

    public void refreshRadioArtistSample(LifecycleOwner owner) {
        artistRepository.getStarredArtists(true, 10).observe(owner, starredArtistsSample::postValue);
    }

    public void refreshBestOfArtist(LifecycleOwner owner) {
        artistRepository.getStarredArtists(true, 20).observe(owner, bestOfArtists::postValue);
    }

    public void refreshStarredTracks(LifecycleOwner owner) {
        songRepository.getStarredSongs(false, -1).observe(owner, songs -> {
            if (songs == null) {
                return;
            }

            List<Child> sampled = sampleRandomItems(songs, 20);
            if (!sampled.isEmpty() || starredTracks.getValue() == null || starredTracks.getValue().isEmpty()) {
                starredTracks.postValue(sampled);
            }
        });
    }

    private List<Child> sampleRandomItems(List<Child> source, int size) {
        if (source == null || source.isEmpty() || size <= 0) {
            return Collections.emptyList();
        }

        List<Child> shuffled = new ArrayList<>(source);
        Collections.shuffle(shuffled);

        return new ArrayList<>(shuffled.subList(0, Math.min(size, shuffled.size())));
    }

    public void refreshStarredAlbums(LifecycleOwner owner) {
        albumRepository.getStarredAlbums(true, 20).observe(owner, starredAlbums::postValue);
    }

    public void refreshStarredArtists(LifecycleOwner owner) {
        artistRepository.getStarredArtists(true, 20).observe(owner, starredArtists::postValue);
    }

    public void refreshMostPlayedAlbums(LifecycleOwner owner) {
        albumRepository.getAlbums("frequent", 20, null, null).observe(owner, mostPlayedAlbumSample::postValue);
    }

    public void refreshMostRecentlyAddedAlbums(LifecycleOwner owner) {
        albumRepository.getAlbums("newest", 20, null, null).observe(owner, recentlyAddedAlbumSample::postValue);
    }

    public void refreshRecentlyPlayedAlbumList(LifecycleOwner owner) {
        albumRepository.getAlbums("recent", 20, null, null).observe(owner, recentlyPlayedAlbumSample::postValue);
    }

    public void refreshHistory(LifecycleOwner owner) {
        songRepository.getRecentlyPlayedSongs(20).observe(owner, history::postValue);
    }

    public void refreshRecentlyPlayedArtists(LifecycleOwner owner) {
        artistRepository.getRecentlyPlayedArtists(20).observe(owner, recentlyPlayedArtists::postValue);
    }

    public void refreshTopPlayedArtists(LifecycleOwner owner) {
        artistRepository.getTopPlayedArtists(20).observe(owner, topPlayedArtists::postValue);
    }

    public void refreshTopPlayedSongs(LifecycleOwner owner) {
        songRepository.getTopPlayedSongs(20).observe(owner, topPlayedSongs::postValue);
    }

    public void refreshPinnedPlaylists() {
        playlistRepository.updatePinnedPlaylists();
    }

    public void refreshShares(LifecycleOwner owner) {
        sharingRepository.getShares().observe(owner, this.shares::postValue);
    }

    public void refreshHomeSectorList() {
        setHomeSectorList();
    }

    private void setHomeSectorList() {
        if (Preferences.getHomeSectorList() != null && !Preferences.getHomeSectorList().equals("null")) {
            sectors = new Gson().fromJson(
                    Preferences.getHomeSectorList(),
                    new TypeToken<List<HomeSector>>() {
                    }.getType()
            );

            // Add missing sectors if any (e.g. after an app update)
            List<HomeSector> standardSectors = fillStandardHomeSectorList();
            boolean changed = false;
            for (HomeSector standardSector : standardSectors) {
                if (sectors.stream().noneMatch(s -> s.getId().equals(standardSector.getId()))) {
                    sectors.add(standardSector);
                    changed = true;
                }
            }
            if (changed) {
                Preferences.setHomeSectorList(sectors);
            }
        } else {
            sectors = fillStandardHomeSectorList();
        }
    }

    private List<HomeSector> fillStandardHomeSectorList() {
        List<HomeSector> sectors = new ArrayList<>();

        sectors.add(new HomeSector(Constants.HOME_SECTOR_DISCOVERY, getApplication().getString(R.string.home_title_discovery), true, 1));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_MADE_FOR_YOU, getApplication().getString(R.string.home_title_made_for_you), true, 2));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_BEST_OF, getApplication().getString(R.string.home_title_best_of), true, 3));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RADIO_STATION, getApplication().getString(R.string.home_title_radio_station), true, 4));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_TOP_SONGS, getApplication().getString(R.string.home_title_top_songs), true, 5));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_TRACKS, getApplication().getString(R.string.home_title_starred_tracks), true, 6));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_ALBUMS, getApplication().getString(R.string.home_title_starred_albums), true, 7));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_ARTISTS, getApplication().getString(R.string.home_title_starred_artists), true, 8));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_NEW_RELEASES, getApplication().getString(R.string.home_title_new_releases), true, 9));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_FLASHBACK, getApplication().getString(R.string.home_title_flashback), true, 10));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_MOST_PLAYED, getApplication().getString(R.string.home_title_most_played), true, 11));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_LAST_PLAYED, getApplication().getString(R.string.home_title_last_played), true, 12));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RECENTLY_ADDED, getApplication().getString(R.string.home_title_recently_added), true, 13));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_PINNED_PLAYLISTS, getApplication().getString(R.string.home_title_pinned_playlists), true, 14));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_SHARED, getApplication().getString(R.string.home_title_shares), true, 15));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_HISTORY, getApplication().getString(R.string.home_title_history), true, 16));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RECENTLY_PLAYED_ARTISTS, getApplication().getString(R.string.home_title_recently_played_artists), true, 17));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_TOP_PLAYED_ARTISTS, getApplication().getString(R.string.home_title_top_played_artists), true, 18));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_TOP_PLAYED_SONGS, getApplication().getString(R.string.home_title_top_played_songs), true, 19));

        return sectors;
    }

    public List<HomeSector> getHomeSectorList() {
        return sectors;
    }

    public boolean checkHomeSectorVisibility(String sectorId) {
        if (sectors == null) return true;
        HomeSector sector = sectors.stream()
                .filter(s -> s.getId().equals(sectorId))
                .findAny()
                .orElse(null);
        return sector == null || !sector.isVisible();
    }

    public void setOfflineFavorite() {
        MediaManager.submitPendingScrobbles();
        ArrayList<Favorite> favorites = getFavorites();
        ArrayList<Favorite> favoritesToSave = getFavoritesToSave(favorites);
        ArrayList<Favorite> favoritesToDelete = getFavoritesToDelete(favorites, favoritesToSave);

        manageFavoriteToSave(favoritesToSave);
        manageFavoriteToDelete(favoritesToDelete);
    }

    private ArrayList<Favorite> getFavorites() {
        return new ArrayList<>(favoriteRepository.getFavorites());
    }

    private ArrayList<Favorite> getFavoritesToSave(ArrayList<Favorite> favorites) {
        HashMap<String, Favorite> filteredMap = new HashMap<>();

        for (Favorite favorite : favorites) {
            String key = favorite.toString();

            if (!filteredMap.containsKey(key) || favorite.getTimestamp() > filteredMap.get(key).getTimestamp()) {
                filteredMap.put(key, favorite);
            }
        }

        return new ArrayList<>(filteredMap.values());
    }

    private ArrayList<Favorite> getFavoritesToDelete(ArrayList<Favorite> favorites, ArrayList<Favorite> favoritesToSave) {
        ArrayList<Favorite> favoritesToDelete = new ArrayList<>();

        for (Favorite favorite : favorites) {
            if (!favoritesToSave.contains(favorite)) {
                favoritesToDelete.add(favorite);
            }
        }

        return favoritesToDelete;
    }

    private void manageFavoriteToSave(ArrayList<Favorite> favoritesToSave) {
        for (Favorite favorite : favoritesToSave) {
            if (favorite.getToStar()) {
                favoriteToStar(favorite);
            } else {
                favoriteToUnstar(favorite);
            }
        }
    }

    private void manageFavoriteToDelete(ArrayList<Favorite> favoritesToDelete) {
        for (Favorite favorite : favoritesToDelete) {
            favoriteRepository.delete(favorite);
        }
    }

    private void favoriteToStar(Favorite favorite) {
        if (favorite.getSongId() != null) {
            favoriteRepository.star(favorite.getSongId(), null, null, new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        } else if (favorite.getAlbumId() != null) {
            favoriteRepository.star(null, favorite.getAlbumId(), null, new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        } else if (favorite.getArtistId() != null) {
            favoriteRepository.star(null, null, favorite.getArtistId(), new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        }
    }

    private void favoriteToUnstar(Favorite favorite) {
        if (favorite.getSongId() != null) {
            favoriteRepository.unstar(favorite.getSongId(), null, null, new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        } else if (favorite.getAlbumId() != null) {
            favoriteRepository.unstar(null, favorite.getAlbumId(), null, new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        } else if (favorite.getArtistId() != null) {
            favoriteRepository.unstar(null, null, favorite.getArtistId(), new StarCallback() {
                @Override
                public void onSuccess() {
                    favoriteRepository.delete(favorite);
                }
            });
        }
    }
}
