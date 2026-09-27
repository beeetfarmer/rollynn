package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentAlbumPageBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.SongHorizontalAdapter;
import com.cappielloantonio.tempo.ui.dialog.PlaylistChooserDialog;
import com.cappielloantonio.tempo.ui.dialog.RatingDialog;
import com.cappielloantonio.tempo.util.AssetLinkUtil;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.ExternalAudioWriter;
import com.cappielloantonio.tempo.util.PlayerBackgroundUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.UIUtil;
import com.cappielloantonio.tempo.viewmodel.AlbumPageViewModel;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class AlbumPageFragment extends Fragment implements ClickCallback {
    private FragmentAlbumPageBinding bind;
    private MainActivity activity;
    private AlbumPageViewModel albumPageViewModel;
    private PlaybackViewModel playbackViewModel;
    private SongHorizontalAdapter songHorizontalAdapter;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private Integer trackTitleColor;
    private Integer trackSubtitleColor;

    /** @noinspection deprecation*/
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    /** @noinspection deprecation*/
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.album_page_menu, menu);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentAlbumPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        albumPageViewModel = new ViewModelProvider(requireActivity()).get(AlbumPageViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        init(view);
        initAppBar();
        initHero();
        initAlbumInfoTextButton();
        initAlbumNotes();
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

    public void onResume() {
        super.onResume();
        // Applied here (not onStart) so the incoming page re-asserts immersive bars
        // after the outgoing page restores them. Portrait full-bleed header only.
        if (activity != null && bind != null && bind.appbar != null) activity.applyImmersiveSystemBars();
        if (songHorizontalAdapter != null) setMediaBrowserListenableFuture();
    }

    @Override
    public void onPause() {
        if (activity != null) activity.restoreDefaultSystemBars();
        super.onPause();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    /** @noinspection deprecation*/
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_rate_album) {
            Bundle bundle = new Bundle();
            AlbumID3 album = albumPageViewModel.getAlbum().getValue();
            bundle.putParcelable(Constants.ALBUM_OBJECT, album);
            RatingDialog dialog = new RatingDialog();
            dialog.setArguments(bundle);
            dialog.show(requireActivity().getSupportFragmentManager(), null);
            return true;
        }

        if (item.getItemId() == R.id.action_download_album) {
            albumPageViewModel.getAlbumSongLiveList().observe(getViewLifecycleOwner(), songs -> {
                if (Preferences.getDownloadDirectoryUri() == null) {
                    DownloadUtil.getDownloadTracker(requireContext()).download(
                        MappingUtil.mapDownloads(songs),
                        songs.stream().map(Download::new).collect(Collectors.toList())
                    );
                } else {
                    songs.forEach(child -> ExternalAudioWriter.downloadToUserDirectory(requireContext(), child));
                }
            });
            return true;
        }
        if (item.getItemId() == R.id.action_add_to_playlist) {
            albumPageViewModel.getAlbumSongLiveList().observe(getViewLifecycleOwner(), songs -> {
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(songs));

                PlaylistChooserDialog dialog = new PlaylistChooserDialog();
                dialog.setArguments(bundle);
                dialog.show(requireActivity().getSupportFragmentManager(), null);
            });
            return true;
        }

        return false;
    }

    private void init(View view) {
        AlbumID3 albumArg = requireArguments().getParcelable(Constants.ALBUM_OBJECT);
        assert albumArg != null;
        albumPageViewModel.setAlbum(getViewLifecycleOwner(), albumArg);
        ToggleButton favoriteToggle = view.findViewById(R.id.button_favorite);
        favoriteToggle.setChecked(albumArg.getStarred() != null);

        favoriteToggle.setOnClickListener(v -> {
            albumPageViewModel.setFavorite();
        });
        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (album != null) {
                favoriteToggle.setChecked(album.getStarred() != null);
            }
        });
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
            // The collapsing title is disabled and the name overlays the artwork,
            // so stop the toolbar falling back to the app name over the image.
            activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // The app bar's background is set to the album colour once the artwork
        // loads (see applyDynamicBackground); as the parallax art scrolls away it
        // reveals that matching colour behind the status bar and back button,
        // rather than a solid black/surface bar.

        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (bind != null && album != null) {
                bind.albumNameLabel.setText(album.getName());
                bind.albumArtistLabel.setText(album.getArtist());
                AssetLinkUtil.applyLinkAppearance(bind.albumArtistLabel);
                // Keep the on-art colour if it was already computed (applyLinkAppearance
                // would otherwise reset the artist to the theme accent).
                if (trackTitleColor != null) {
                    bind.albumNameLabel.setTextColor(trackTitleColor);
                    bind.albumArtistLabel.setTextColor(trackTitleColor);
                }
                AssetLinkUtil.AssetLink artistLink = buildArtistLink(album);
                bind.albumArtistLabel.setOnLongClickListener(v -> {
                    if (artistLink != null) {
                        AssetLinkUtil.copyToClipboard(requireContext(), artistLink);
                        Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, artistLink.id), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    return false;
                });
                // Landscape's classic layout shows these individually; guard them
                // since the portrait hero shows a single combined line instead.
                if (bind.albumReleaseYearLabel != null) {
                    bind.albumReleaseYearLabel.setText(album.getYear() != 0 ? String.valueOf(album.getYear()) : "");
                    if (album.getYear() != 0) {
                        bind.albumReleaseYearLabel.setVisibility(View.VISIBLE);
                        AssetLinkUtil.applyLinkAppearance(bind.albumReleaseYearLabel);
                        bind.albumReleaseYearLabel.setOnClickListener(v -> openYearLink(album.getYear()));
                        bind.albumReleaseYearLabel.setOnLongClickListener(v -> {
                            AssetLinkUtil.AssetLink yearLink = buildYearLink(album.getYear());
                            if (yearLink != null) {
                                AssetLinkUtil.copyToClipboard(requireContext(), yearLink);
                                Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, yearLink.id), Toast.LENGTH_SHORT).show();
                            }
                            return true;
                        });
                    } else {
                        bind.albumReleaseYearLabel.setVisibility(View.GONE);
                        bind.albumReleaseYearLabel.setOnClickListener(null);
                        bind.albumReleaseYearLabel.setOnLongClickListener(null);
                        AssetLinkUtil.clearLinkAppearance(bind.albumReleaseYearLabel);
                    }
                }
                if (bind.albumSongCountDurationTextview != null) {
                    bind.albumSongCountDurationTextview.setText(getString(R.string.album_page_tracks_count_and_duration, album.getSongCount(), album.getDuration() != null ? album.getDuration() / 60 : 0));
                }
                if (bind.albumGenresTextview != null) {
                    if (album.getGenre() != null && !album.getGenre().isEmpty()) {
                        bind.albumGenresTextview.setText(album.getGenre());
                        bind.albumGenresTextview.setVisibility(View.VISIBLE);
                    } else {
                        bind.albumGenresTextview.setVisibility(View.GONE);
                    }
                }
                bindAlbumPlayStats(album);

                // Portrait hero: year · genre · N songs • M minutes on one line, over the art.
                if (bind.albumHeroInfo != null) {
                    java.util.List<String> parts = new java.util.ArrayList<>();
                    if (album.getYear() != 0) parts.add(String.valueOf(album.getYear()));
                    if (album.getGenre() != null && !album.getGenre().isEmpty()) parts.add(album.getGenre());
                    parts.add(getString(R.string.album_page_tracks_count_and_duration, album.getSongCount(),
                            album.getDuration() != null ? album.getDuration() / 60 : 0));
                    bind.albumHeroInfo.setText(android.text.TextUtils.join("  ·  ", parts));
                }
            }
        });

        bind.animToolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());

        if (bind.animToolbar.getOverflowIcon() != null) {
            bind.animToolbar.getOverflowIcon().setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
        }

        // The album detail (genres, notes, dates, stats) is always shown now, so
        // the collapsible arrow is gone from the portrait header; hide it in the
        // landscape layout too.
        if (bind.albumDetailView != null) bind.albumDetailView.setVisibility(View.VISIBLE);
        if (bind.albumOtherInfoButton != null) bind.albumOtherInfoButton.setVisibility(View.GONE);
    }

    private void initHero() {
        if (bind.appbar == null || bind.albumHero == null) return; // landscape keeps the classic header
        // Header takes ~52% of the screen height so the square cover fills it.
        ViewGroup.LayoutParams appbarParams = bind.appbar.getLayoutParams();
        appbarParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.52f);
        bind.appbar.setLayoutParams(appbarParams);
        // The collapsing layout grows by the status bar inset, pushing the art's
        // bottom under the content; pin the art to the visible header so its
        // faded bottom edge lines up with the background below.
        bind.albumCoverImageView.getLayoutParams().height = appbarParams.height;

        // The name block is anchored centred on the header's bottom edge; shift it
        // up so it sits fully over the artwork rather than straddling the edge.
        int lift = UIUtil.dpToPx(requireContext(), 10);
        bind.albumHero.post(() -> {
            if (bind == null) return;
            bind.albumHero.setTranslationY(-(bind.albumHero.getHeight() / 2f) - lift);
        });
    }

    private void initAlbumInfoTextButton() {
        bind.albumArtistLabel.setOnClickListener(v -> albumPageViewModel.getArtist().observe(getViewLifecycleOwner(), artist -> {
            if (artist != null) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.ARTIST_OBJECT, artist);
                activity.navController.navigate(R.id.action_albumPageFragment_to_artistPageFragment, bundle);
            } else
                Toast.makeText(requireContext(), getString(R.string.album_error_retrieving_artist), Toast.LENGTH_SHORT).show();
        }));
    }

    private void initAlbumNotes() {
        albumPageViewModel.getAlbumInfo().observe(getViewLifecycleOwner(), albumInfo -> {
            if (albumInfo != null) {
                if (bind != null) bind.albumNotesTextview.setVisibility(View.VISIBLE);
                if (bind != null)
                    bind.albumNotesTextview.setText(MusicUtil.forceReadableString(albumInfo.getNotes()));

                if (bind != null && albumInfo.getLastFmUrl() != null && !albumInfo.getLastFmUrl().isEmpty()) {
                    bind.albumNotesTextview.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(albumInfo.getLastFmUrl()));
                        startActivity(intent);
                    });
                }
            } else {
                if (bind != null) bind.albumNotesTextview.setVisibility(View.GONE);
            }
        });
    }

    private void initMusicButton() {
        albumPageViewModel.getAlbumSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            if (bind != null && !songs.isEmpty()) {
                bind.albumPagePlayButton.setOnClickListener(v -> {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                });

                bind.albumPageShuffleButton.setOnClickListener(v -> {
                    Collections.shuffle(songs);
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                });
            }

            if (bind != null && songs.isEmpty()) {
                bind.albumPagePlayButton.setEnabled(false);
                bind.albumPageShuffleButton.setEnabled(false);
            }
        });
    }

    private void initBackCover() {
        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (bind != null && album != null) {
                CustomGlideRequest.loadFadedArt(requireContext(), album.getCoverArtId(),
                        CustomGlideRequest.ResourceType.Album, bind.albumCoverImageView,
                        this::applyDynamicBackground, null);
            }
        });
    }

    /**
     * Apple Music style: colour the background below the art with the colour its
     * bottom edge dissolves into, and switch the overlaid name/artist to white or
     * black for legibility against it. Portrait full-bleed header only.
     */
    private void applyDynamicBackground(int edge) {
        if (bind == null || bind.albumContentContainer == null) return;
        int fadeColor = PlayerBackgroundUtil.backgroundTopColor(requireContext(), edge);

        // One solid colour down the whole page, as Apple Music does, so the text
        // colour picked from it stays legible everywhere (a gradient toward the theme
        // base left light text on a light bottom half and vice versa).
        bind.albumContentContainer.setBackgroundColor(fadeColor);

        // The art scrolls away to reveal this matching colour behind the status bar
        // and back button, and the collapsed header's pinned strip is covered with it.
        if (bind.appbar != null) bind.appbar.setBackgroundColor(fadeColor);
        bind.collapsingToolbar.setContentScrimColor(fadeColor);
        bind.collapsingToolbar.setStatusBarScrimColor(fadeColor);

        applyOnArtColors(fadeColor);
    }

    /**
     * Recolours the overlaid name/artist, the play/shuffle/favourite controls, the
     * back and overflow icons, and the track list to white or black for legibility
     * against {@code artColor} (the colour the artwork dissolves into).
     */
    private void applyOnArtColors(int artColor) {
        if (bind == null) return;
        boolean lightArt = androidx.core.graphics.ColorUtils.calculateLuminance(artColor) > 0.5f;
        int onArt = lightArt ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
        int contrast = lightArt ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;
        android.content.res.ColorStateList onArtTint = android.content.res.ColorStateList.valueOf(onArt);

        int secondary = androidx.core.graphics.ColorUtils.setAlphaComponent(onArt, 190);
        bind.albumNameLabel.setTextColor(onArt);
        bind.albumArtistLabel.setTextColor(onArt);

        // The detail block (genre, duration, dates, notes, stats) sits on the same
        // colour; recolour it so it stays readable on light and dark artwork alike.
        setDetailTextColor(bind.albumHeroInfo, secondary);
        setDetailTextColor(bind.albumReleaseYearLabel, secondary);
        setDetailTextColor(bind.albumGenresTextview, secondary);
        setDetailTextColor(bind.albumSongCountDurationTextview, secondary);
        setDetailTextColor(bind.albumNotesTextview, secondary);
        setDetailTextColor(bind.albumPlayStatsTextview, secondary);

        // Play: a filled pill in the on-art colour with contrasting label/icon.
        if (bind.albumPagePlayButton instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton play =
                    (com.google.android.material.button.MaterialButton) bind.albumPagePlayButton;
            play.setBackgroundTintList(onArtTint);
            play.setTextColor(contrast);
            play.setIconTint(android.content.res.ColorStateList.valueOf(contrast));
        }
        if (bind.albumPageShuffleIcon != null) bind.albumPageShuffleIcon.setColorFilter(onArt);
        bind.buttonFavorite.setBackgroundTintList(onArtTint);

        if (bind.animToolbar != null) {
            bind.animToolbar.setNavigationIconTint(onArt);
            if (bind.animToolbar.getOverflowIcon() != null) bind.animToolbar.getOverflowIcon().setTint(onArt);
        }

        // Track rows sit on the same colour; dim the subtitle slightly.
        trackTitleColor = onArt;
        trackSubtitleColor = secondary;
        applyTrackTextColors();
    }

    private void setDetailTextColor(android.widget.TextView view, int color) {
        if (view != null) view.setTextColor(color);
    }

    private void applyTrackTextColors() {
        if (songHorizontalAdapter != null && trackTitleColor != null) {
            songHorizontalAdapter.setTextColorOverride(trackTitleColor, trackSubtitleColor);
        }
    }

    private void bindAlbumPlayStats(AlbumID3 album) {
        // Show the server's own stats immediately, then override with Koito if it has a match.
        setAlbumPlayStatsText(UIUtil.buildPlayStats(album.getPlayCount(), album.getPlayed()));

        if (Preferences.useKoitoStats() && com.cappielloantonio.tempo.koito.KoitoClient.isConfigured()) {
            albumPageViewModel.getKoitoAlbumStats(album.getMusicBrainzId(), album.getName(), album.getArtist())
                    .observe(getViewLifecycleOwner(), koito -> {
                        if (bind == null || koito == null) return; // no Koito match: keep the server's stats
                        java.util.Date lastPlayed = koito.getLastPlayed() != null ? koito.getLastPlayed() : album.getPlayed();
                        setAlbumPlayStatsText(UIUtil.buildPlayStats(koito.getPlayCount(), lastPlayed));
                    });
        }
    }

    private void setAlbumPlayStatsText(String stats) {
        bind.albumPlayStatsTextview.setText(stats);
        bind.albumPlayStatsTextview.setVisibility(stats != null ? View.VISIBLE : View.GONE);
    }

    private void initSongsView() {
        albumPageViewModel.getAlbum().observe(getViewLifecycleOwner(), album -> {
            if (bind != null && album != null) {
                bind.songRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()) {
                    @Override
                    public boolean canScrollVertically() {
                        return false;
                    }
                });
                bind.songRecyclerView.setHasFixedSize(false);

                songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, false, false, album);
                bind.songRecyclerView.setAdapter(songHorizontalAdapter);
                applyTrackTextColors();
                setMediaBrowserListenableFuture();
                reapplyPlayback();

                if (Preferences.showTopSongIndicator() && album.getArtist() != null && !album.getArtist().isEmpty()) {
                    albumPageViewModel.getTopSongs(album.getArtist(), 50).observe(getViewLifecycleOwner(), topSongs -> {
                        if (songHorizontalAdapter == null || topSongs == null) return;
                        List<String> rankedIds = new ArrayList<>();
                        for (Child s : topSongs) if (s.getId() != null) rankedIds.add(s.getId());
                        songHorizontalAdapter.setTopSongRankedIds(rankedIds);
                    });
                }

                albumPageViewModel.getAlbumSongLiveList().observe(getViewLifecycleOwner(), songs -> {
                    songHorizontalAdapter.setItems(songs);
                    reapplyPlayback();
                    bindKoitoTrackCounts(album, songs);
                });
            }
        });
    }

    private void bindKoitoTrackCounts(AlbumID3 album, List<Child> songs) {
        if (!Preferences.useKoitoStats() || !com.cappielloantonio.tempo.koito.KoitoClient.isConfigured() || songs == null) return;
        java.util.Map<String, String> songTitles = new java.util.HashMap<>();
        for (Child s : songs) if (s.getId() != null && s.getTitle() != null) songTitles.put(s.getId(), s.getTitle());
        if (songTitles.isEmpty()) return;
        albumPageViewModel.getKoitoAlbumTrackCounts(album.getMusicBrainzId(), album.getName(), album.getArtist(), songTitles)
                .observe(getViewLifecycleOwner(), counts -> {
                    if (songHorizontalAdapter != null && counts != null && !counts.isEmpty()) {
                        songHorizontalAdapter.setKoitoTrackCounts(counts);
                    }
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

    private void setMediaBrowserListenableFuture() {
        songHorizontalAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }

    private void openYearLink(int year) {
        AssetLinkUtil.AssetLink link = buildYearLink(year);
        if (link != null) {
            activity.openAssetLink(link);
        }
    }

    private AssetLinkUtil.AssetLink buildYearLink(int year) {
        if (year <= 0) return null;
        return AssetLinkUtil.buildAssetLink(AssetLinkUtil.TYPE_YEAR, String.valueOf(year));
    }

    private AssetLinkUtil.AssetLink buildArtistLink(AlbumID3 album) {
        if (album == null || album.getArtistId() == null || album.getArtistId().isEmpty()) {
            return null;
        }
        return AssetLinkUtil.buildAssetLink(AssetLinkUtil.TYPE_ARTIST, album.getArtistId());
    }
}
