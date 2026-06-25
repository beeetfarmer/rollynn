package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.databinding.FragmentPlaylistPageBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.SongHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.ExternalAudioWriter;
import com.cappielloantonio.tempo.util.PlaylistCoverCache;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.cappielloantonio.tempo.viewmodel.PlaylistPageViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;

import android.widget.PopupMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class PlaylistPageFragment extends Fragment implements ClickCallback {
    private FragmentPlaylistPageBinding bind;
    private MainActivity activity;
    private PlaylistPageViewModel playlistPageViewModel;
    private PlaybackViewModel playbackViewModel;

    private SongHorizontalAdapter songHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private Menu optionsMenu;
    private int downloadTotalCount = 0;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean trackingDownloads = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.playlist_page_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                songHorizontalAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);

        optionsMenu = menu;
        initMenuOption(menu);
        updateRemoveDownloadsVisibility();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentPlaylistPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        playlistPageViewModel = new ViewModelProvider(requireActivity()).get(PlaylistPageViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        init();
        initAppBar();
        initMusicButton();
        initBackCover();
        initSongsView();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();

        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observePlayback();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (songHorizontalAdapter != null) setMediaBrowserListenableFuture();
        resumeDownloadTrackingIfNeeded();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        progressHandler.removeCallbacksAndMessages(null);
        trackingDownloads = false;
        bind = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_sort_playlist) {
            View anchor = activity.findViewById(R.id.action_sort_playlist);
            if (anchor == null) anchor = bind.animToolbar;
            showSortPopupMenu(anchor);
            return true;
        } else if (item.getItemId() == R.id.action_download_playlist) {
            playlistPageViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
                if (isVisible() && getActivity() != null) {
                    Playlist downloadPlaylist = playlistPageViewModel.getPlaylist();
                    PlaylistCoverCache.save(downloadPlaylist.getId(), downloadPlaylist.getCoverArtId());
                    if (Preferences.getDownloadDirectoryUri() == null) {
                        downloadTotalCount = (int) songs.stream().map(song -> song.getId()).distinct().count();
                        playlistPageViewModel.setDownloadInProgress(downloadPlaylist.getId(), downloadTotalCount);
                        showDownloadProgress(0, downloadTotalCount);
                        List<Download> toDownloadList = new ArrayList<>();
                        for (int position = 0; position < songs.size(); position++) {
                            Download toDownload = new Download(songs.get(position));
                            toDownload.setPlaylistId(downloadPlaylist.getId());
                            toDownload.setPlaylistName(downloadPlaylist.getName());
                            toDownload.setPlaylistPosition(position);
                            toDownloadList.add(toDownload);
                        }
                        DownloadUtil.getDownloadTracker(requireContext()).download(
                            MappingUtil.mapDownloads(songs),
                            toDownloadList
                        );
                        startTrackingDownloadProgress();
                    } else {
                        songs.forEach(child -> ExternalAudioWriter.downloadToUserDirectory(requireContext(), child));
                    }
                }
            });
            return true;
        } else if (item.getItemId() == R.id.action_remove_downloads) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.playlist_remove_downloads)
                    .setMessage(R.string.playlist_remove_downloads_confirm)
                    .setPositiveButton(R.string.playlist_editor_dialog_neutral_button, (dialog, which) -> removePlaylistDownloads())
                    .setNegativeButton(R.string.playlist_editor_dialog_negative_button, null)
                    .show();
            return true;
        } else if (item.getItemId() == R.id.action_pin_playlist) {
            playlistPageViewModel.setPinned(true);
            return true;
        } else if (item.getItemId() == R.id.action_unpin_playlist) {
            playlistPageViewModel.setPinned(false);
            return true;
        }

        return false;
    }

    private void init() {
        playlistPageViewModel.setPlaylist(requireArguments().getParcelable(Constants.PLAYLIST_OBJECT));
    }

    private void initMenuOption(Menu menu) {
        playlistPageViewModel.isPinned(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), isPinned -> {
            menu.findItem(R.id.action_unpin_playlist).setVisible(isPinned);
            menu.findItem(R.id.action_pin_playlist).setVisible(!isPinned);
        });
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        bind.animToolbar.setTitle(playlistPageViewModel.getPlaylist().getName());

        bind.playlistNameLabel.setText(playlistPageViewModel.getPlaylist().getName());
        bind.playlistSongCountLabel.setText(getString(R.string.playlist_song_count, playlistPageViewModel.getPlaylist().getSongCount()));
        bind.playlistDurationLabel.setText(getString(R.string.playlist_duration, MusicUtil.getReadableDurationString(playlistPageViewModel.getPlaylist().getDuration(), false)));

        bind.animToolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });

        Objects.requireNonNull(bind.animToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void initMusicButton() {
        playlistPageViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            if (bind != null) {
                bind.playlistPagePlayButton.setOnClickListener(v -> {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                });

                bind.playlistPageShuffleButton.setOnClickListener(v -> {
                    java.util.List<com.cappielloantonio.tempo.subsonic.models.Child> shuffledSongs = new java.util.ArrayList<>(songs);
                    java.util.Collections.shuffle(shuffledSongs);
                    MediaManager.startQueue(mediaBrowserListenableFuture, shuffledSongs, 0);
                    activity.setBottomSheetInPeek(true);
                });
            }
        });
    }

    private void initBackCover() {
        playlistPageViewModel.getPlaylistSongLiveList().observe(requireActivity(), songs -> {
            if (bind != null && songs != null && !songs.isEmpty()) {
                java.util.List<com.cappielloantonio.tempo.subsonic.models.Child> randomSongs = new java.util.ArrayList<>(songs);
                java.util.Collections.shuffle(randomSongs);

                // Pic top-left
                CustomGlideRequest.Builder
                        .from(requireContext(), !randomSongs.isEmpty() ? randomSongs.get(0).getCoverArtId() : playlistPageViewModel.getPlaylist().getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .transform(new GranularRoundedCorners(CustomGlideRequest.CORNER_RADIUS, 0, 0, 0))
                        .into(bind.playlistCoverImageViewTopLeft);

                // Pic top-right
                CustomGlideRequest.Builder
                        .from(requireContext(), randomSongs.size() > 1 ? randomSongs.get(1).getCoverArtId() : playlistPageViewModel.getPlaylist().getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .transform(new GranularRoundedCorners(0, CustomGlideRequest.CORNER_RADIUS, 0, 0))
                        .into(bind.playlistCoverImageViewTopRight);

                // Pic bottom-left
                CustomGlideRequest.Builder
                        .from(requireContext(), randomSongs.size() > 2 ? randomSongs.get(2).getCoverArtId() : playlistPageViewModel.getPlaylist().getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .transform(new GranularRoundedCorners(0, 0, 0, CustomGlideRequest.CORNER_RADIUS))
                        .into(bind.playlistCoverImageViewBottomLeft);

                // Pic bottom-right
                CustomGlideRequest.Builder
                        .from(requireContext(), randomSongs.size() > 3 ? randomSongs.get(3).getCoverArtId() : playlistPageViewModel.getPlaylist().getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .transform(new GranularRoundedCorners(0, 0, CustomGlideRequest.CORNER_RADIUS, 0))
                        .into(bind.playlistCoverImageViewBottomRight);
            }
        });
    }

    private void initSongsView() {
        bind.songRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.songRecyclerView.setHasFixedSize(true);

        songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        bind.songRecyclerView.setAdapter(songHorizontalAdapter);
        setMediaBrowserListenableFuture();
        reapplyPlayback();

        playlistPageViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            songHorizontalAdapter.setItems(songs);
            if (songs != null) {
                bind.playlistSongCountLabel.setText(getString(R.string.playlist_song_count, songs.size()));
                long totalDuration = songs.stream().mapToLong(s -> s.getDuration() != null ? s.getDuration() : 0).sum();
                bind.playlistDurationLabel.setText(getString(R.string.playlist_duration, MusicUtil.getReadableDurationString(totalDuration, false)));
            }
            reapplyPlayback();
        });
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        bundle.putString(Constants.PLAYLIST_ID, playlistPageViewModel.getPlaylist().getId());
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    private void observePlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (songHorizontalAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (songHorizontalAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void reapplyPlayback() {
        if (songHorizontalAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    private void showSortPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.sort_playlist_song_popup_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_playlist_song_sort_default) {
                songHorizontalAdapter.sort(Constants.MEDIA_DEFAULT_ORDER);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_playlist_song_sort_artist) {
                songHorizontalAdapter.sort(Constants.MEDIA_BY_ARTIST);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_playlist_song_sort_name) {
                songHorizontalAdapter.sort(Constants.MEDIA_BY_TITLE);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void setMediaBrowserListenableFuture() {
        songHorizontalAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }

    private void updateRemoveDownloadsVisibility() {
        if (optionsMenu == null) return;
        String playlistId = playlistPageViewModel.getPlaylist().getId();
        new Thread(() -> {
            DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();
            int count = downloadDao.getDownloadCountForPlaylist(playlistId);
            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (optionsMenu != null) {
                        MenuItem removeItem = optionsMenu.findItem(R.id.action_remove_downloads);
                        if (removeItem != null) removeItem.setVisible(count > 0);
                    }
                });
            }
        }).start();
    }

    private void removePlaylistDownloads() {
        String playlistId = playlistPageViewModel.getPlaylist().getId();
        new Thread(() -> {
            DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();
            List<Download> downloads = new ArrayList<>(downloadDao.getByPlaylistIdSync(playlistId));
            if (!downloads.isEmpty()) {
                List<androidx.media3.common.MediaItem> mediaItems = downloads.stream()
                        .map(MappingUtil::mapDownload)
                        .collect(Collectors.toList());
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        DownloadUtil.getDownloadTracker(requireContext()).remove(mediaItems, downloads);
                        PlaylistCoverCache.remove(playlistId);
                        updateRemoveDownloadsVisibility();
                    });
                }
            }
        }).start();
    }

    private void showDownloadProgress(int completed, int total) {
        if (bind == null) return;
        bind.downloadProgressLayout.setVisibility(View.VISIBLE);
        bind.downloadProgressBar.setMax(total);
        bind.downloadProgressBar.setProgress(completed);
        if (completed >= total) {
            bind.downloadProgressText.setText(R.string.playlist_download_complete);
            progressHandler.postDelayed(() -> {
                if (bind != null) bind.downloadProgressLayout.setVisibility(View.GONE);
                updateRemoveDownloadsVisibility();
            }, 2000);
        } else {
            bind.downloadProgressText.setText(getString(R.string.playlist_downloading_progress, completed, total));
        }
    }

    private void resumeDownloadTrackingIfNeeded() {
        if (trackingDownloads) return;
        String activeId = playlistPageViewModel.getDownloadingPlaylistId();
        if (activeId == null) return;
        Playlist playlist = playlistPageViewModel.getPlaylist();
        if (playlist == null || !activeId.equals(playlist.getId())) return;
        downloadTotalCount = playlistPageViewModel.getDownloadTotalCount();
        startTrackingDownloadProgress();
    }

    private void startTrackingDownloadProgress() {
        if (trackingDownloads) return;
        trackingDownloads = true;
        String playlistId = playlistPageViewModel.getPlaylist().getId();
        pollDownloadProgress(playlistId);
    }

    private void pollDownloadProgress(String playlistId) {
        if (!trackingDownloads || bind == null) return;
        new Thread(() -> {
            DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();
            int completed = downloadDao.getDownloadCountForPlaylist(playlistId);
            Map<String, Float> perTrackProgress = getPerTrackProgress();
            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (bind == null) return;
                    showDownloadProgress(completed, downloadTotalCount);
                    if (songHorizontalAdapter != null) {
                        songHorizontalAdapter.updateDownloadProgress(perTrackProgress);
                    }
                    if (completed < downloadTotalCount) {
                        progressHandler.postDelayed(() -> pollDownloadProgress(playlistId), 500);
                    } else {
                        trackingDownloads = false;
                        playlistPageViewModel.clearDownloadInProgress();
                        if (songHorizontalAdapter != null) {
                            songHorizontalAdapter.updateDownloadProgress(null);
                        }
                    }
                });
            }
        }).start();
    }

    private Map<String, Float> getPerTrackProgress() {
        Map<String, Float> progressMap = new HashMap<>();
        try {
            DownloadManager downloadManager = DownloadUtil.getDownloadManager(requireContext());
            androidx.media3.exoplayer.offline.DownloadIndex downloadIndex = downloadManager.getDownloadIndex();
            try (androidx.media3.exoplayer.offline.DownloadCursor cursor = downloadIndex.getDownloads()) {
                while (cursor.moveToNext()) {
                    androidx.media3.exoplayer.offline.Download download = cursor.getDownload();
                    if (download.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING) {
                        progressMap.put(download.request.id, download.getPercentDownloaded() / 100f);
                    } else if (download.state == androidx.media3.exoplayer.offline.Download.STATE_QUEUED) {
                        progressMap.put(download.request.id, 0f);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return progressMap;
    }
}
