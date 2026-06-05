package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.database.dao.RecentSearchDao;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.RecentSearch;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.SearchResult2;
import com.cappielloantonio.tempo.subsonic.models.SearchResult3;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchingRepository {
    private final RecentSearchDao recentSearchDao = AppDatabase.getInstance().recentSearchDao();

    public MutableLiveData<SearchResult2> search2(String query) {
        MutableLiveData<SearchResult2> result = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 20, 20)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            result.setValue(response.body().getSubsonicResponse().getSearchResult2());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return result;
    }

    public MutableLiveData<SearchResult3> search3(String query) {
        MutableLiveData<SearchResult3> result = new MutableLiveData<>();

        if (NetworkUtil.isServerUnreachable()) {
            searchDownloads(query, result);
            return result;
        }

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 20, 20)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.raw().networkResponse() != null) {
                            result.setValue(response.body().getSubsonicResponse().getSearchResult3());
                        } else {
                            searchDownloads(query, result);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        searchDownloads(query, result);
                    }
                });

        return result;
    }

    @androidx.media3.common.util.UnstableApi
    private void searchDownloads(String query, MutableLiveData<SearchResult3> result) {
        new Thread(() -> {
            DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();
            List<Download> downloads = downloadDao.searchSync(query);
            SearchResult3 localResult = new SearchResult3();
            List<Child> songs = new ArrayList<>(downloads);
            localResult.setSongs(songs);
            result.postValue(localResult);
        }).start();
    }

    public MutableLiveData<List<String>> getSuggestions(String query) {
        MutableLiveData<List<String>> suggestions = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 5, 5, 5)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<String> newSuggestions = new ArrayList();

                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSearchResult3() != null) {
                            if (response.body().getSubsonicResponse().getSearchResult3().getArtists() != null) {
                                for (ArtistID3 artistID3 : response.body().getSubsonicResponse().getSearchResult3().getArtists()) {
                                    newSuggestions.add(artistID3.getName());
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getAlbums() != null) {
                                for (AlbumID3 albumID3 : response.body().getSubsonicResponse().getSearchResult3().getAlbums()) {
                                    newSuggestions.add(albumID3.getName());
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getSongs() != null) {
                                for (Child song : response.body().getSubsonicResponse().getSearchResult3().getSongs()) {
                                    newSuggestions.add(song.getTitle());
                                }
                            }

                            LinkedHashSet<String> hashSet = new LinkedHashSet<>(newSuggestions);
                            ArrayList<String> suggestionsWithoutDuplicates = new ArrayList<>(hashSet);

                            suggestions.setValue(suggestionsWithoutDuplicates);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return suggestions;
    }

    public void insert(RecentSearch recentSearch) {
        InsertThreadSafe insert = new InsertThreadSafe(recentSearchDao, recentSearch);
        Thread thread = new Thread(insert);
        thread.start();
    }

    public void delete(RecentSearch recentSearch) {
        DeleteThreadSafe delete = new DeleteThreadSafe(recentSearchDao, recentSearch);
        Thread thread = new Thread(delete);
        thread.start();
    }

    public List<String> getRecentSearchSuggestion() {
        List<String> recent = new ArrayList<>();

        RecentThreadSafe suggestionsThread = new RecentThreadSafe(recentSearchDao);
        Thread thread = new Thread(suggestionsThread);
        thread.start();

        try {
            thread.join();
            recent = suggestionsThread.getRecent();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return recent;
    }

    private static class DeleteThreadSafe implements Runnable {
        private final RecentSearchDao recentSearchDao;
        private final RecentSearch recentSearch;

        public DeleteThreadSafe(RecentSearchDao recentSearchDao, RecentSearch recentSearch) {
            this.recentSearchDao = recentSearchDao;
            this.recentSearch = recentSearch;
        }

        @Override
        public void run() {
            recentSearchDao.delete(recentSearch);
        }
    }

    private static class InsertThreadSafe implements Runnable {
        private final RecentSearchDao recentSearchDao;
        private final RecentSearch recentSearch;

        public InsertThreadSafe(RecentSearchDao recentSearchDao, RecentSearch recentSearch) {
            this.recentSearchDao = recentSearchDao;
            this.recentSearch = recentSearch;
        }

        @Override
        public void run() {
            recentSearchDao.insert(recentSearch);
        }
    }

    private static class RecentThreadSafe implements Runnable {
        private final RecentSearchDao recentSearchDao;
        private List<String> recent = new ArrayList<>();

        public RecentThreadSafe(RecentSearchDao recentSearchDao) {
            this.recentSearchDao = recentSearchDao;
        }

        @Override
        public void run() {
            if(Preferences.isSearchSortingChronologicallyEnabled()){
                recent = recentSearchDao.getRecent();
            }
            else {
                recent = recentSearchDao.getAlpha();
            }
        }

        public List<String> getRecent() {
            return recent;
        }
    }
}
