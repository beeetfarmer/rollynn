package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.RepeatModeUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.widget.ViewPager2;

import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.ui.dialog.PlaylistChooserDialog;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerControllerBinding;
import com.cappielloantonio.tempo.service.EqualizerManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.PlaybackSpeedDialog;
import com.cappielloantonio.tempo.ui.dialog.RatingDialog;
import com.cappielloantonio.tempo.ui.dialog.TrackInfoDialog;
import com.cappielloantonio.tempo.ui.fragment.pager.PlayerControllerHorizontalPager;
import com.cappielloantonio.tempo.util.AssetLinkUtil;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.UIUtil;
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel;
import com.cappielloantonio.tempo.viewmodel.RatingViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@UnstableApi
public class PlayerControllerFragment extends Fragment {
    private static final String TAG = "PlayerCoverFragment";

    private InnerFragmentPlayerControllerBinding bind;
    private ViewPager2 playerMediaCoverViewPager;
    private ToggleButton buttonFavorite;
    private RatingViewModel ratingViewModel;
    private RatingBar songRatingBar;
    private LinearLayout playerMetadataContainer;
    private Button playbackSpeedButton;
    private ToggleButton skipSilenceToggleButton;
    private Chip playerMediaExtension;
    private TextView playerMediaBitrate;
    private View playerQuickActionView;
    private ImageButton playerOpenQueueButton;
    private ImageButton playerTrackInfo;
    private View ratingContainer;
    private ImageButton equalizerButton;
    private ImageButton addToPlaylistButton;
    private ImageButton overflowMenuButton;
    private ImageButton lyricsButton;
    private ChipGroup assetLinkChipGroup;
    private Chip playerSongLinkChip;
    private Chip playerAlbumLinkChip;
    private Chip playerArtistLinkChip;

    private MainActivity activity;
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private MediaService.LocalBinder mediaServiceBinder;
    private boolean isServiceBound = false;
    private boolean isFirstBatch = true;

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = (sharedPreferences, key) -> {
        if ("now_playing_metadata".equals(key)) {
            if (bind != null && mediaBrowserListenableFuture != null && mediaBrowserListenableFuture.isDone()) {
                try {
                    MediaBrowser browser = mediaBrowserListenableFuture.get();
                    setMetadata(browser.getMediaMetadata());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = InnerFragmentPlayerControllerBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);
        ratingViewModel = new ViewModelProvider(requireActivity()).get(RatingViewModel.class);

        init();
        initQuickActionView();
        initCoverLyricsSlideView();
        initMediaListenable();
        initEqualizerButton();
        initOverflowMenu();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        initializeBrowser();
        bindMediaController();
    }

    @Override
    public void onStop() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        playerMediaCoverViewPager = bind.getRoot().findViewById(R.id.player_media_cover_view_pager);
        buttonFavorite = bind.getRoot().findViewById(R.id.button_favorite);
        playerMetadataContainer = bind.getRoot().findViewById(R.id.player_metadata_container);
        playbackSpeedButton = bind.getRoot().findViewById(R.id.player_playback_speed_button);
        skipSilenceToggleButton = bind.getRoot().findViewById(R.id.player_skip_silence_toggle_button);
        playerMediaExtension = bind.getRoot().findViewById(R.id.player_media_extension);
        playerMediaBitrate = bind.getRoot().findViewById(R.id.player_media_bitrate);
        playerQuickActionView = bind.getRoot().findViewById(R.id.player_quick_action_view);
        playerOpenQueueButton = bind.getRoot().findViewById(R.id.player_open_queue_button);
        playerTrackInfo = bind.getRoot().findViewById(R.id.player_info_track);
        songRatingBar =  bind.getRoot().findViewById(R.id.song_rating_bar);
        ratingContainer = bind.getRoot().findViewById(R.id.rating_container);
        equalizerButton = bind.getRoot().findViewById(R.id.player_open_equalizer_button);
        addToPlaylistButton = bind.getRoot().findViewById(R.id.button_add_to_playlist);
        if (addToPlaylistButton != null) {
            addToPlaylistButton.setOnClickListener(v -> launchPlaylistChooser());
        }
        overflowMenuButton = bind.getRoot().findViewById(R.id.button_overflow_menu);
        lyricsButton = bind.getRoot().findViewById(R.id.player_open_lyrics_button);
        assetLinkChipGroup = bind.getRoot().findViewById(R.id.asset_link_chip_group);
        playerSongLinkChip = bind.getRoot().findViewById(R.id.asset_link_song_chip);
        playerAlbumLinkChip = bind.getRoot().findViewById(R.id.asset_link_album_chip);
        playerArtistLinkChip = bind.getRoot().findViewById(R.id.asset_link_artist_chip);
        checkAndSetRatingContainerVisibility();
    }

    private void initQuickActionView() {
        playerOpenQueueButton.setOnClickListener(view -> {
            PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) requireActivity().getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");
            if (playerBottomSheetFragment != null) {
                playerBottomSheetFragment.goToQueuePage();
            }
        });

        if (lyricsButton != null) {
            lyricsButton.setOnClickListener(view -> {
                if (playerMediaCoverViewPager.getCurrentItem() == 1) {
                    goToControllerPage();
                } else {
                    goToLyricsPage();
                }
            });
        }
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            if (bind == null) return;
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();

                bind.nowPlayingMediaControllerView.setPlayer(mediaBrowser);
                mediaBrowser.setShuffleModeEnabled(Preferences.isShuffleModeEnabled());
                mediaBrowser.setRepeatMode(Preferences.getRepeatMode());
                setMediaControllerListener(mediaBrowser);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setMediaControllerListener(MediaBrowser mediaBrowser) {
        setMediaControllerUI(mediaBrowser);
        setMetadata(mediaBrowser.getMediaMetadata());
        setMediaInfo(mediaBrowser.getMediaMetadata());

        mediaBrowser.addListener(new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                setMediaControllerUI(mediaBrowser);
                setMetadata(mediaMetadata);
                setMediaInfo(mediaMetadata);
            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
                Preferences.setShuffleModeEnabled(shuffleModeEnabled);
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {
                Preferences.setRepeatMode(repeatMode);
            }
        });
    }

    private void setMetadata(MediaMetadata mediaMetadata) {
        setDynamicMetadata(mediaMetadata);
        updateAssetLinkChips(mediaMetadata);
    }

    private void setDynamicMetadata(MediaMetadata mediaMetadata) {
        if (playerMetadataContainer == null) return;
        playerMetadataContainer.removeAllViews();

        List<String> enabledFields = Preferences.getNowPlayingMetadata();
        String type = mediaMetadata.extras != null ? mediaMetadata.extras.getString("type") : null;

        if (Objects.equals(type, Constants.MEDIA_TYPE_RADIO)) {
            renderRadioMetadata(mediaMetadata, enabledFields);
            return;
        }

        for (String field : enabledFields) {
            switch (field) {
                case Constants.METADATA_TITLE:
                    if (mediaMetadata.title != null) {
                        TextView titleView = createMetadataView(String.valueOf(mediaMetadata.title), R.style.PlayerMetadataTitle);
                        playerMetadataContainer.addView(titleView);
                        bindAlbumLink(titleView);
                    }
                    break;
                case Constants.METADATA_ARTIST:
                    if (mediaMetadata.artist != null) {
                        TextView artistView = createMetadataView(String.valueOf(mediaMetadata.artist), R.style.PlayerMetadataArtist);
                        artistView.setTextColor(getPlayerTextColor());
                        playerMetadataContainer.addView(artistView);
                        bindArtistLink(artistView);
                    }
                    break;
                case Constants.METADATA_ALBUM:
                    if (mediaMetadata.albumTitle != null) {
                        TextView albumView = createMetadataView(String.valueOf(mediaMetadata.albumTitle), R.style.PlayerMetadataAlbum);
                        albumView.setTextColor(getPlayerTextColor());
                        playerMetadataContainer.addView(albumView);
                        bindAlbumLink(albumView);
                    }
                    break;
                case Constants.METADATA_YEAR:
                    if (mediaMetadata.releaseYear != null) {
                        TextView yearView = createMetadataView(String.valueOf(mediaMetadata.releaseYear), R.style.PlayerMetadataSecondary);
                        yearView.setTextColor(getPlayerTextColor());
                        playerMetadataContainer.addView(yearView);
                    } else if (mediaMetadata.extras != null && mediaMetadata.extras.containsKey("year")) {
                        TextView yearView = createMetadataView(String.valueOf(mediaMetadata.extras.getInt("year")), R.style.PlayerMetadataSecondary);
                        yearView.setTextColor(getPlayerTextColor());
                        playerMetadataContainer.addView(yearView);
                    }
                    break;
                case Constants.METADATA_GENRE:
                    if (mediaMetadata.genre != null) {
                        TextView genreView = createMetadataView(String.valueOf(mediaMetadata.genre), R.style.PlayerMetadataSecondary);
                        genreView.setTextColor(getPlayerTextColor());
                        playerMetadataContainer.addView(genreView);
                    }
                    break;
                case Constants.METADATA_BITRATE:
                    if (mediaMetadata.extras != null) {
                        int rawBitrate = mediaMetadata.extras.getInt("bitrate", 0);
                        String suffix = mediaMetadata.extras.getString("suffix");
                        StringBuilder bitrateText = new StringBuilder();
                        if (!TextUtils.isEmpty(suffix)) {
                            bitrateText.append(suffix.toUpperCase());
                        }
                        if (rawBitrate != 0) {
                            if (bitrateText.length() > 0) bitrateText.append(" • ");
                            bitrateText.append(rawBitrate).append(" kbps");
                        }

                        if (bitrateText.length() > 0) {
                            TextView bitrateView = createMetadataView(bitrateText.toString(), R.style.PlayerMetadataSecondary);
                            bitrateView.setTextColor(getPlayerTextColor());
                            playerMetadataContainer.addView(bitrateView);
                        }
                    }
                    break;
                case Constants.METADATA_PLAY_COUNT:
                    if (mediaMetadata.extras != null) {
                        String currentSongId = mediaMetadata.extras.getString("id");
                        long basePlayCount = mediaMetadata.extras.getLong("playCount", 0);
                        long effectivePlayCount = basePlayCount + MediaManager.getPlayCountIncrement(currentSongId);
                        if (effectivePlayCount != 0) {
                            TextView playCountView = createMetadataView(effectivePlayCount + " plays", R.style.PlayerMetadataSecondary);
                            playCountView.setTextColor(getPlayerTextColor());
                            playerMetadataContainer.addView(playCountView);

                            final long[] lastSeenVersion = {MediaManager.getScrobbleVersion()};
                            MediaManager.getScrobbledSongId().observe(getViewLifecycleOwner(), scrobbledId -> {
                                long currentVersion = MediaManager.getScrobbleVersion();
                                if (currentVersion <= lastSeenVersion[0]) return;
                                lastSeenVersion[0] = currentVersion;
                                if (currentSongId != null && currentSongId.equals(scrobbledId)) {
                                    long updated = basePlayCount + MediaManager.getPlayCountIncrement(currentSongId);
                                    playCountView.setText(updated + " plays");
                                }
                            });
                        } else {
                            TextView playCountView = createMetadataView("", R.style.PlayerMetadataSecondary);
                            playCountView.setTextColor(getPlayerTextColor());
                            playCountView.setVisibility(View.GONE);
                            playerMetadataContainer.addView(playCountView);

                            final long[] lastSeenVersion = {MediaManager.getScrobbleVersion()};
                            MediaManager.getScrobbledSongId().observe(getViewLifecycleOwner(), scrobbledId -> {
                                long currentVersion = MediaManager.getScrobbleVersion();
                                if (currentVersion <= lastSeenVersion[0]) return;
                                lastSeenVersion[0] = currentVersion;
                                if (currentSongId != null && currentSongId.equals(scrobbledId)) {
                                    long updated = basePlayCount + MediaManager.getPlayCountIncrement(currentSongId);
                                    playCountView.setText(updated + " plays");
                                    playCountView.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    }
                    break;
                case Constants.METADATA_SCROBBLES:
                    TextView scrobbleView = createMetadataView("", R.style.PlayerMetadataSecondary);
                    scrobbleView.setTextColor(getPlayerTextColor());
                    scrobbleView.setVisibility(View.GONE);
                    playerMetadataContainer.addView(scrobbleView);

                    String artist = mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : null;
                    String title = mediaMetadata.title != null ? String.valueOf(mediaMetadata.title) : null;
                    playerBottomSheetViewModel.fetchLastFmScrobbleCount(artist, title);
                    playerBottomSheetViewModel.getLastFmScrobbleCount().observe(getViewLifecycleOwner(), count -> {
                        if (count != null && count > 0) {
                            scrobbleView.setText(count + " scrobbles");
                            scrobbleView.setVisibility(View.VISIBLE);
                        } else {
                            scrobbleView.setVisibility(View.GONE);
                        }
                    });
                    break;
            }
        }
    }

    private void renderRadioMetadata(MediaMetadata mediaMetadata, List<String> enabledFields) {
        String stationName = mediaMetadata.extras != null
                ? mediaMetadata.extras.getString("stationName",
                mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : "")
                : mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : "";

        String artist = mediaMetadata.extras != null ? mediaMetadata.extras.getString("radioArtist", "") : "";
        String title = mediaMetadata.extras != null ? mediaMetadata.extras.getString("radioTitle", "") : "";

        String mainTitle;
        if (!TextUtils.isEmpty(artist) && !TextUtils.isEmpty(title)) {
            mainTitle = artist + " - " + title;
        } else if (!TextUtils.isEmpty(title)) {
            mainTitle = title;
        } else if (!TextUtils.isEmpty(artist)) {
            mainTitle = artist;
        } else {
            mainTitle = stationName;
        }

        TextView titleView = createMetadataView(mainTitle, R.style.HeadlineLarge);
        playerMetadataContainer.addView(titleView);

        TextView stationView = createMetadataView(stationName, R.style.TitleMedium);
        stationView.setTextColor(getPlayerTextColor());
        playerMetadataContainer.addView(stationView);
    }

    private TextView createMetadataView(String text, int styleRes) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setTextAppearance(styleRes);
        textView.setTextColor(getPlayerTextColor());
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        textView.setGravity(android.view.Gravity.CENTER);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        textView.setSelected(true);
        textView.setPadding(0, UIUtil.dpToPx(requireContext(), 2), 0, UIUtil.dpToPx(requireContext(), 2));
        return textView;
    }

    private int getPlayerTextColor() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;
    }

    private void bindAlbumLink(View view) {
        playerBottomSheetViewModel.getLiveAlbum().observe(getViewLifecycleOwner(), album -> {
            if (album != null) {
                view.setOnClickListener(v -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.ALBUM_OBJECT, album);
                    NavHostFragment.findNavController(this).navigate(R.id.albumPageFragment, bundle);
                    activity.collapseBottomSheetDelayed();
                });
            }
        });
    }

    private void bindArtistLink(View view) {
        playerBottomSheetViewModel.getLiveArtist().observe(getViewLifecycleOwner(), artist -> {
            if (artist != null) {
                view.setOnClickListener(v -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.ARTIST_OBJECT, artist);
                    NavHostFragment.findNavController(this).navigate(R.id.artistPageFragment, bundle);
                    activity.collapseBottomSheetDelayed();
                });
            }
        });
    }

    private void setMediaInfo(MediaMetadata mediaMetadata) {
        boolean isLocal = false;
        
        if (mediaBrowserListenableFuture != null && mediaBrowserListenableFuture.isDone()) {
            try {
                MediaBrowser browser = mediaBrowserListenableFuture.get();
                if (browser != null && browser.getCurrentMediaItem() != null) {
                    android.net.Uri currentUri = browser.getCurrentMediaItem().requestMetadata.mediaUri;
                    if (currentUri != null) {
                        String scheme = currentUri.getScheme();
                        isLocal = "content".equals(scheme) || "file".equals(scheme);
                    }
                }
            } catch (Exception e) {
                Log.e("DEBUG_PLAYER", "Error getting browser for UI update", e);
            }
        }

        if (mediaMetadata.extras != null) {
            String extension = mediaMetadata.extras.getString("suffix", getString(R.string.player_unknown_format));
            int rawBitrate = mediaMetadata.extras.getInt("bitrate", 0);
            String bitrate = rawBitrate != 0 ? rawBitrate + "kbps" : "Original";
            String samplingRate = mediaMetadata.extras.getInt("samplingRate", 0) != 0 ? 
                    new java.text.DecimalFormat("0.#").format(mediaMetadata.extras.getInt("samplingRate", 0) / 1000.0) + "kHz" : "";
            String bitDepth = mediaMetadata.extras.getInt("bitDepth", 0) != 0 ? mediaMetadata.extras.getInt("bitDepth", 0) + "b" : "";

            playerMediaExtension.setText(extension);

            if (bitrate.equals("Original") && !isLocal) {
                playerMediaBitrate.setVisibility(View.GONE);
            } else {
                List<String> items = new ArrayList<>();
                if (!bitrate.trim().isEmpty()) items.add(bitrate);
                if (!bitDepth.trim().isEmpty()) items.add(bitDepth);
                if (!samplingRate.trim().isEmpty()) items.add(samplingRate);
                String mediaQuality = TextUtils.join(" • ", items);
                
                playerMediaBitrate.setVisibility(View.VISIBLE);
                playerMediaBitrate.setText(isLocal ? mediaQuality : mediaQuality);
            }
        }

        
        if (!isLocal) {
            boolean isTranscodingExtension = !MusicUtil.getTranscodingFormatPreference().equals("raw");
            boolean isTranscodingBitrate = !MusicUtil.getBitratePreference().equals("0");
            if (isTranscodingExtension || isTranscodingBitrate) {
                playerMediaExtension.setText(MusicUtil.getTranscodingFormatPreference() + " (" + getString(R.string.player_transcoding) + ")");
                playerMediaBitrate.setText(!MusicUtil.getBitratePreference().equals("0") ? 
                        MusicUtil.getBitratePreference() + "kbps" : getString(R.string.player_transcoding_requested));
            }

        }

        playerTrackInfo.setOnClickListener(view -> {
            TrackInfoDialog dialog = new TrackInfoDialog(mediaMetadata);
            dialog.show(activity.getSupportFragmentManager(), null);
            });
    }
    private void updateAssetLinkChips(MediaMetadata mediaMetadata) {
        if (assetLinkChipGroup == null) return;
        String mediaType = mediaMetadata.extras != null ? mediaMetadata.extras.getString("type", Constants.MEDIA_TYPE_MUSIC) : Constants.MEDIA_TYPE_MUSIC;
        if (!Constants.MEDIA_TYPE_MUSIC.equals(mediaType)) {
            clearAssetLinkChip(playerSongLinkChip);
            clearAssetLinkChip(playerAlbumLinkChip);
            clearAssetLinkChip(playerArtistLinkChip);
            syncAssetLinkGroupVisibility();
            return;
        }

        String songId = mediaMetadata.extras != null ? mediaMetadata.extras.getString("id") : null;
        String albumId = mediaMetadata.extras != null ? mediaMetadata.extras.getString("albumId") : null;
        String artistId = mediaMetadata.extras != null ? mediaMetadata.extras.getString("artistId") : null;

        AssetLinkUtil.AssetLink songLink = bindAssetLinkChip(playerSongLinkChip, AssetLinkUtil.TYPE_SONG, songId);
        AssetLinkUtil.AssetLink albumLink = bindAssetLinkChip(playerAlbumLinkChip, AssetLinkUtil.TYPE_ALBUM, albumId);
        AssetLinkUtil.AssetLink artistLink = bindAssetLinkChip(playerArtistLinkChip, AssetLinkUtil.TYPE_ARTIST, artistId);
        bindAssetLinkView(playerMediaCoverViewPager, songLink);
        syncAssetLinkGroupVisibility();
    }

    private AssetLinkUtil.AssetLink bindAssetLinkChip(Chip chip, String type, String id) {
        if (chip == null) return null;
        if (TextUtils.isEmpty(id)) {
            clearAssetLinkChip(chip);
            return null;
        }

        String label = getString(AssetLinkUtil.getLabelRes(type));
        AssetLinkUtil.AssetLink assetLink = AssetLinkUtil.buildAssetLink(type, id);
        if (assetLink == null) {
            clearAssetLinkChip(chip);
            return null;
        }

        chip.setText(getString(R.string.asset_link_chip_text, label, assetLink.id));
        chip.setVisibility(View.VISIBLE);

        chip.setOnClickListener(v -> {
            if (assetLink != null) {
                activity.openAssetLink(assetLink);
            }
        });

        chip.setOnLongClickListener(v -> {
            if (assetLink != null) {
                AssetLinkUtil.copyToClipboard(requireContext(), assetLink);
                Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, id), Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        return assetLink;
    }

    private void clearAssetLinkChip(Chip chip) {
        if (chip == null) return;
        chip.setVisibility(View.GONE);
        chip.setText("");
        chip.setOnClickListener(null);
        chip.setOnLongClickListener(null);
    }

    private void bindAssetLinkView(View view, AssetLinkUtil.AssetLink assetLink) {
        if (view == null) return;
        if (assetLink == null) {
            AssetLinkUtil.clearLinkAppearance(view);
            view.setOnClickListener(null);
            view.setOnLongClickListener(null);
            view.setClickable(false);
            view.setLongClickable(false);
            return;
        }

        view.setClickable(true);
        view.setLongClickable(true);
        AssetLinkUtil.applyLinkAppearance(view);
        view.setOnClickListener(v -> {
            boolean collapse = !AssetLinkUtil.TYPE_SONG.equals(assetLink.type);
            activity.openAssetLink(assetLink, collapse);
        });
        view.setOnLongClickListener(v -> {
            AssetLinkUtil.copyToClipboard(requireContext(), assetLink);
            Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, assetLink.id), Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void syncAssetLinkGroupVisibility() {
        if (assetLinkChipGroup == null) return;
        boolean hasVisible = false;
        for (int i = 0; i < assetLinkChipGroup.getChildCount(); i++) {
            View child = assetLinkChipGroup.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                hasVisible = true;
                break;
            }
        }
        assetLinkChipGroup.setVisibility(hasVisible ? View.VISIBLE : View.GONE);
    }

    private void setMediaControllerUI(MediaBrowser mediaBrowser) {
        initPlaybackSpeedButton(mediaBrowser);

        if (mediaBrowser.getMediaMetadata().extras != null) {
            switch (mediaBrowser.getMediaMetadata().extras.getString("type", Constants.MEDIA_TYPE_MUSIC)) {
                case Constants.MEDIA_TYPE_PODCAST:
                    bind.getRoot().setShowShuffleButton(false);
                    bind.getRoot().setShowRewindButton(true);
                    bind.getRoot().setShowPreviousButton(false);
                    bind.getRoot().setShowNextButton(false);
                    bind.getRoot().setShowFastForwardButton(true);
                    bind.getRoot().setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
                    setViewVisibilityIfPresent(R.id.button_favorite, View.GONE);
                    setViewVisibilityIfPresent(R.id.button_add_to_playlist, View.GONE);
                    setViewVisibilityIfPresent(R.id.button_overflow_menu, View.GONE);
                    setPlaybackParameters(mediaBrowser);
                    break;
                case Constants.MEDIA_TYPE_RADIO:
                    bind.getRoot().setShowShuffleButton(false);
                    bind.getRoot().setShowRewindButton(false);
                    bind.getRoot().setShowPreviousButton(false);
                    bind.getRoot().setShowNextButton(false);
                    bind.getRoot().setShowFastForwardButton(false);
                    bind.getRoot().setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
                    setViewVisibilityIfPresent(R.id.button_favorite, View.GONE);
                    setViewVisibilityIfPresent(R.id.button_add_to_playlist, View.GONE);
                    setViewVisibilityIfPresent(R.id.button_overflow_menu, View.GONE);
                    setPlaybackParameters(mediaBrowser);
                    break;
                case Constants.MEDIA_TYPE_MUSIC:
                default:
                    bind.getRoot().setShowShuffleButton(true);
                    bind.getRoot().setShowRewindButton(false);
                    bind.getRoot().setShowPreviousButton(true);
                    bind.getRoot().setShowNextButton(true);
                    bind.getRoot().setShowFastForwardButton(false);
                    bind.getRoot().setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL | RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE);
                    setViewVisibilityIfPresent(R.id.button_favorite, View.VISIBLE);
                    setViewVisibilityIfPresent(R.id.button_add_to_playlist, View.VISIBLE);
                    setViewVisibilityIfPresent(R.id.button_overflow_menu, View.VISIBLE);
                    setPlaybackParameters(mediaBrowser);
                    break;
            }
        }
    }

    private void initCoverLyricsSlideView() {
        playerMediaCoverViewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        playerMediaCoverViewPager.setAdapter(new PlayerControllerHorizontalPager(this));
        updateTrackInfoVisibility(playerMediaCoverViewPager.getCurrentItem());

        playerMediaCoverViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateTrackInfoVisibility(position);

                PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) requireActivity().getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");

                if (position == 0) {
                    activity.setBottomSheetDraggableState(true);

                    if (playerBottomSheetFragment != null) {
                        playerBottomSheetFragment.setPlayerControllerVerticalPagerDraggableState(true);
                    }
                } else if (position == 1) {
                    activity.setBottomSheetDraggableState(false);

                    if (playerBottomSheetFragment != null) {
                        playerBottomSheetFragment.setPlayerControllerVerticalPagerDraggableState(false);
                    }
                }
            }
        });
    }

    private void updateTrackInfoVisibility(int position) {
        if (playerTrackInfo == null) return;
        playerTrackInfo.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
    }

    private void initMediaListenable() {
        playerBottomSheetViewModel.getLiveMedia().observe(getViewLifecycleOwner(), media -> {
            if (media != null) {
                ratingViewModel.setSong(media);
                buttonFavorite.setChecked(media.getStarred() != null);
                buttonFavorite.setOnClickListener(v -> playerBottomSheetViewModel.setFavorite(requireContext(), media));
                buttonFavorite.setOnLongClickListener(v -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.TRACK_OBJECT, media);

                    RatingDialog dialog = new RatingDialog();
                    dialog.setArguments(bundle);
                    dialog.show(requireActivity().getSupportFragmentManager(), null);


                    return true;
                });

                Integer currentRating = media.getUserRating();

                if (currentRating != null) {
                    songRatingBar.setRating(currentRating);
                } else {
                    songRatingBar.setRating(0);
                }

                songRatingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
                    @Override
                    public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                        if (fromUser) {
                            ratingViewModel.rate((int) rating);
                            media.setUserRating((int) rating);
                            MediaManager.postRatingEvent(media.getId(), (int) rating);
                        }
                    }
                });


                if (media.getPlayCount() != null && mediaBrowserListenableFuture != null && mediaBrowserListenableFuture.isDone()) {
                    MediaManager.resetPlayCountIncrement(media.getId());
                    try {
                        MediaBrowser browser = mediaBrowserListenableFuture.get();
                        MediaItem currentItem = browser.getCurrentMediaItem();
                        if (currentItem != null && currentItem.mediaMetadata.extras != null
                                && media.getId().equals(currentItem.mediaMetadata.extras.getString("id"))) {
                            currentItem.mediaMetadata.extras.putLong("playCount", media.getPlayCount());
                            setMetadata(browser.getMediaMetadata());
                        }
                    } catch (Exception ignored) {}
                }

                if (getActivity() != null) {
                    playerBottomSheetViewModel.refreshMediaInfo(requireActivity(), media);
                }
            }
        });

        MediaManager.getFavoriteEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            String songId = (String) event[0];
            Date starred = (Date) event[1];
            Child media = playerBottomSheetViewModel.getLiveMedia().getValue();
            if (media != null && media.getId().equals(songId)) {
                buttonFavorite.setChecked(starred != null);
            }
        });

        MediaManager.getRatingEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            String songId = (String) event[0];
            int rating = (Integer) event[1];
            Child media = playerBottomSheetViewModel.getLiveMedia().getValue();
            if (media != null && media.getId().equals(songId)) {
                songRatingBar.setOnRatingBarChangeListener(null);
                songRatingBar.setRating(rating);
                songRatingBar.setOnRatingBarChangeListener((ratingBar, r, fromUser) -> {
                    if (fromUser) {
                        ratingViewModel.rate((int) r);
                        media.setUserRating((int) r);
                        MediaManager.postRatingEvent(media.getId(), (int) r);
                    }
                });
            }
        });
    }

    private void initPlaybackSpeedButton(MediaBrowser mediaBrowser) {
        playbackSpeedButton.setOnClickListener(view -> {
            PlaybackSpeedDialog dialog = new PlaybackSpeedDialog();
            dialog.setPlaybackSpeedListener(speed -> {
                mediaBrowser.setPlaybackParameters(new PlaybackParameters(speed));
                playbackSpeedButton.setText(getString(R.string.player_playback_speed, speed));
            });
            dialog.show(requireActivity().getSupportFragmentManager(), null);
        });

        skipSilenceToggleButton.setOnClickListener(view -> {
            Preferences.setSkipSilenceMode(!skipSilenceToggleButton.isChecked());
        });
    }

    private void initEqualizerButton() {
        if (equalizerButton == null) return;
        equalizerButton.setOnClickListener(v -> {
            NavController navController = NavHostFragment.findNavController(this);
            NavOptions navOptions = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.equalizerFragment, true)
                    .build();
            navController.navigate(R.id.equalizerFragment, null, navOptions);
            if (activity != null) activity.collapseBottomSheetDelayed();
        });
    }

    private void initOverflowMenu() {
        if (overflowMenuButton == null) return;
        overflowMenuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenuInflater().inflate(R.menu.menu_now_playing_overflow, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_add_to_playlist) {
                    launchPlaylistChooser();
                    return true;
                } else if (id == R.id.menu_go_to_album) {
                    playerBottomSheetViewModel.getLiveAlbum().observe(getViewLifecycleOwner(), album -> {
                        if (album != null) {
                            Bundle bundle = new Bundle();
                            bundle.putParcelable(Constants.ALBUM_OBJECT, album);
                            NavHostFragment.findNavController(this).navigate(R.id.albumPageFragment, bundle);
                            activity.collapseBottomSheetDelayed();
                        }
                    });
                    return true;
                } else if (id == R.id.menu_go_to_artist) {
                    playerBottomSheetViewModel.getLiveArtist().observe(getViewLifecycleOwner(), artist -> {
                        if (artist != null) {
                            Bundle bundle = new Bundle();
                            bundle.putParcelable(Constants.ARTIST_OBJECT, artist);
                            NavHostFragment.findNavController(this).navigate(R.id.artistPageFragment, bundle);
                            activity.collapseBottomSheetDelayed();
                        }
                    });
                    return true;
                } else if (id == R.id.menu_instant_mix) {
                    Child media = playerBottomSheetViewModel.getLiveMedia().getValue();
                    if (media != null) {
                        ListenableFuture<MediaBrowser> activityBrowserFuture = activity.getMediaBrowserListenableFuture();
                        if (activityBrowserFuture == null) return true;

                        isFirstBatch = true;
                        Toast.makeText(requireContext(), R.string.bottom_sheet_generating_instant_mix, Toast.LENGTH_SHORT).show();

                        playerBottomSheetViewModel.getMediaInstantMix(activity, media).observe(activity, mixMedia -> {
                            if (mixMedia == null || mixMedia.isEmpty()) return;
                            if (getActivity() == null) return;

                            MusicUtil.ratingFilter(mixMedia);

                            if (isFirstBatch) {
                                isFirstBatch = false;
                                MediaManager.startQueue(activityBrowserFuture, mixMedia, 0);
                                activity.setBottomSheetInPeek(true);
                            } else {
                                MediaManager.enqueue(activityBrowserFuture, mixMedia, true);
                            }
                        });
                    }
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void launchPlaylistChooser() {
        Child media = playerBottomSheetViewModel.getLiveMedia().getValue();
        if (media != null) {
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(Collections.singletonList(media)));
            PlaylistChooserDialog dialog = new PlaylistChooserDialog();
            dialog.setArguments(bundle);
            dialog.show(requireActivity().getSupportFragmentManager(), null);
        }
    }

    public void goToControllerPage() {
        playerMediaCoverViewPager.setCurrentItem(0, false);
    }

    public void goToLyricsPage() {
        playerMediaCoverViewPager.setCurrentItem(1, true);
    }

    private void checkAndSetRatingContainerVisibility() {
        if (ratingContainer == null) return;

        if (Preferences.showItemStarRating()) {
            songRatingBar.setVisibility(View.VISIBLE);
        } else {
            songRatingBar.setVisibility(View.GONE);
        }

        TextView ratingText = bind.getRoot().findViewById(R.id.rating_text);
        if (ratingText != null) {
            ratingText.setVisibility(Preferences.showItemStarRating() ? View.VISIBLE : View.GONE);
        }
    }

    private void setPlaybackParameters(MediaBrowser mediaBrowser) {
        Button playbackSpeedButton = bind.getRoot().findViewById(R.id.player_playback_speed_button);
        float currentSpeed = Preferences.getPlaybackSpeed();
        boolean skipSilence = Preferences.isSkipSilenceMode();

        mediaBrowser.setPlaybackParameters(new PlaybackParameters(currentSpeed));
        if (playbackSpeedButton != null) {
            playbackSpeedButton.setText(getString(R.string.player_playback_speed, currentSpeed));
        }

        // TODO Skippare il silenzio
        if (skipSilenceToggleButton != null) {
            skipSilenceToggleButton.setChecked(skipSilence);
        }
    }

    private void setViewVisibilityIfPresent(int viewId, int visibility) {
        View view = bind.getRoot().findViewById(viewId);
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    private void resetPlaybackParameters(MediaBrowser mediaBrowser) {
        mediaBrowser.setPlaybackParameters(new PlaybackParameters(1.0f));
        // TODO Resettare lo skip del silenzio
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediaServiceBinder = (MediaService.LocalBinder) service;
            isServiceBound = true;
            checkEqualizerBands();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaServiceBinder = null;
            isServiceBound = false;
        }
    };

    private void bindMediaService() {
        Intent intent = new Intent(requireActivity(), MediaService.class);
        intent.setAction(MediaService.ACTION_BIND_EQUALIZER);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isServiceBound = true;
    }

    private void checkEqualizerBands() {
        if (mediaServiceBinder != null) {
            EqualizerManager eqManager = mediaServiceBinder.getEqualizerManager();
            short numBands = eqManager.getNumberOfBands();

            if (equalizerButton != null) {
                equalizerButton.setVisibility(numBands == 0 ? View.GONE : View.VISIBLE);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        bindMediaService();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isServiceBound) {
            requireActivity().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}
