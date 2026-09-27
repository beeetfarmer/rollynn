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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RatingBar;
import android.widget.ImageView;
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

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.SubtitleView;

import com.cappielloantonio.tempo.popinn.PopinnApi;
import com.cappielloantonio.tempo.popinn.PopinnClient;
import com.cappielloantonio.tempo.popinn.PopinnPlayRequest;
import com.cappielloantonio.tempo.popinn.PopinnRepository;
import com.cappielloantonio.tempo.popinn.PopinnSubtitle;
import com.cappielloantonio.tempo.popinn.PopinnVideo;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.ui.dialog.PlaylistChooserDialog;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerControllerBinding;
import com.cappielloantonio.tempo.service.EqualizerManager;
import com.cappielloantonio.tempo.service.BaseMediaService;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.EqualizerPresetPickerDialog;
import com.cappielloantonio.tempo.ui.dialog.PlaybackSpeedDialog;
import com.cappielloantonio.tempo.ui.dialog.RatingDialog;
import com.cappielloantonio.tempo.ui.dialog.TrackInfoDialog;
import com.cappielloantonio.tempo.ui.fragment.pager.PlayerControllerHorizontalPager;
import com.cappielloantonio.tempo.util.AssetLinkUtil;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.PlayerBackgroundUtil;
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
    private ImageButton sleepTimerButton;
    // White or black picked from the album-coloured background; null = theme default.
    private Integer playerContentColor;
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

    // --- Music video switching (Popinn) ---
    private static final int VIDEO_SEEK_STEP_SECONDS = 10;

    private ImageButton switchToVideoButton;
    private FrameLayout playerVideoContainer;
    private AspectRatioFrameLayout playerVideoAspect;
    private SurfaceView playerVideoView;
    private SubtitleView playerVideoSubtitles;
    private PlayerControlView fullscreenController;
    private ImageButton videoFullscreenButton;
    private TextView videoSeekBackLabel;
    private TextView videoSeekForwardLabel;

    private final PopinnRepository popinnRepository = new PopinnRepository();
    private PopinnVideo matchedVideo;
    private String matchQueryKey;

    private ExoPlayer videoPlayer;
    private boolean videoMode = false;

    private long videoWatchedMs;
    private long videoWatchStartedAt = C.TIME_UNSET;
    private int videoDurationSeconds;
    private boolean videoNavidromeScrobbled;
    private boolean videoPopinnReported;

    private boolean videoFullscreen = false;
    private ViewGroup videoOriginalParent;
    private int videoOriginalIndex;
    private ViewGroup.LayoutParams videoOriginalParams;
    private int savedOrientation;
    private OnBackPressedCallback fullscreenBackCallback;

    private final Handler videoSeekHandler = new Handler(Looper.getMainLooper());
    private int videoAccumulatedSeek;
    private boolean videoLastSeekForward;
    private GestureDetector fullscreenGestureDetector;

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = (sharedPreferences, key) -> {
        if ("now_playing_metadata".equals(key)) {
            updateCoverHeight();
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

        // Apple Music style seek bar: the time bar draws square ends, so clip its
        // (thumbless, 7dp) track to a pill.
        View timeBar = view.findViewById(R.id.exo_progress);
        timeBar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                int barHeight = UIUtil.dpToPx(v.getContext(), 7);
                int top = (v.getHeight() - barHeight) / 2;
                outline.setRoundRect(v.getPaddingLeft(), top, v.getWidth() - v.getPaddingRight(),
                        top + barHeight, barHeight / 2f);
            }
        });
        timeBar.setClipToOutline(true);

        // Apple Music style: time left on the right, and the bar swells while dragged.
        TextView remainingTime = view.findViewById(R.id.player_remaining_time);
        StringBuilder timeBuilder = new StringBuilder();
        java.util.Formatter timeFormatter = new java.util.Formatter(timeBuilder, java.util.Locale.getDefault());
        java.util.function.LongConsumer showRemaining = position -> {
            Player player = bind.nowPlayingMediaControllerView.getPlayer();
            long duration = player != null ? player.getDuration() : C.TIME_UNSET;
            remainingTime.setText(duration == C.TIME_UNSET ? ""
                    : "-" + androidx.media3.common.util.Util.getStringForTime(
                    timeBuilder, timeFormatter, Math.max(0, duration - position)));
        };
        bind.nowPlayingMediaControllerView.setProgressUpdateListener((position, buffered) -> showRemaining.accept(position));
        ((androidx.media3.ui.DefaultTimeBar) timeBar).addListener(new androidx.media3.ui.TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(@NonNull androidx.media3.ui.TimeBar bar, long position) {
                timeBar.animate().scaleY(1.8f).scaleX(1.03f).setDuration(150).start();
                showRemaining.accept(position);
            }

            @Override
            public void onScrubMove(@NonNull androidx.media3.ui.TimeBar bar, long position) {
                showRemaining.accept(position);
            }

            @Override
            public void onScrubStop(@NonNull androidx.media3.ui.TimeBar bar, long position, boolean canceled) {
                timeBar.animate().scaleY(1f).scaleX(1f).setDuration(200).start();
            }
        });

        playerBottomSheetViewModel.getPlayerDominantColor().observe(getViewLifecycleOwner(), dominant -> {
            playerContentColor = dominant == null ? null
                    : PlayerBackgroundUtil.contentColor(requireContext(), dominant);
            applyPlayerContentColor();
        });

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
        // Leaving the screen: report what was watched and drop the video player,
        // but leave the audio track as it is (do not restart it here).
        if (videoMode) teardownVideo();
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (videoMode) teardownVideo();
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
        sleepTimerButton = bind.getRoot().findViewById(R.id.button_sleep_timer);
        if (sleepTimerButton != null) {
            sleepTimerButton.setOnClickListener(v -> showSleepTimerDialog());
            updateSleepTimerButton();
        }
        overflowMenuButton = bind.getRoot().findViewById(R.id.button_overflow_menu);
        lyricsButton = bind.getRoot().findViewById(R.id.player_open_lyrics_button);
        assetLinkChipGroup = bind.getRoot().findViewById(R.id.asset_link_chip_group);
        playerSongLinkChip = bind.getRoot().findViewById(R.id.asset_link_song_chip);
        playerAlbumLinkChip = bind.getRoot().findViewById(R.id.asset_link_album_chip);
        playerArtistLinkChip = bind.getRoot().findViewById(R.id.asset_link_artist_chip);
        checkAndSetRatingContainerVisibility();
        initVideoSwitch();
        updateCoverHeight();
    }

    /**
     * Size the album art from how much metadata is shown: with only a couple of
     * lines it takes more of the screen (up to 60%), shrinking toward a 50% floor
     * as more fields are enabled so they always have room.
     */
    private void updateCoverHeight() {
        if (bind == null) return;
        androidx.constraintlayout.widget.Guideline guideline = bind.getRoot().findViewById(R.id.guideline);
        if (guideline == null) return;

        int count = Preferences.getNowPlayingMetadata().size();
        float percent = 0.60f - Math.max(0, count - 2) * 0.02f;
        percent = Math.max(0.50f, Math.min(0.60f, percent));
        guideline.setGuidelinePercent(percent);
    }

    private void initVideoSwitch() {
        switchToVideoButton = bind.getRoot().findViewById(R.id.player_switch_to_video_button);
        playerVideoContainer = bind.getRoot().findViewById(R.id.player_video_container);
        playerVideoAspect = bind.getRoot().findViewById(R.id.player_video_aspect);
        playerVideoView = bind.getRoot().findViewById(R.id.player_video_view);
        playerVideoSubtitles = bind.getRoot().findViewById(R.id.player_video_subtitles);
        videoFullscreenButton = bind.getRoot().findViewById(R.id.player_video_fullscreen_button);
        videoSeekBackLabel = bind.getRoot().findViewById(R.id.player_video_seek_back_label);
        videoSeekForwardLabel = bind.getRoot().findViewById(R.id.player_video_seek_forward_label);

        if (switchToVideoButton != null) switchToVideoButton.setOnClickListener(v -> toggleVideoMode());
        if (videoFullscreenButton != null) videoFullscreenButton.setOnClickListener(v -> toggleFullscreen());
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

        String alignment = Preferences.getMetadataAlignment();
        switch (alignment) {
            case Preferences.METADATA_ALIGNMENT_LEFT:
                playerMetadataContainer.setGravity(android.view.Gravity.START);
                break;
            case Preferences.METADATA_ALIGNMENT_RIGHT:
                playerMetadataContainer.setGravity(android.view.Gravity.END);
                break;
            default:
                playerMetadataContainer.setGravity(android.view.Gravity.CENTER);
                break;
        }

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

                        TextView playCountView = createMetadataView("", R.style.PlayerMetadataSecondary);
                        playCountView.setTextColor(getPlayerTextColor());
                        if (effectivePlayCount != 0) {
                            playCountView.setText(effectivePlayCount + " plays");
                        } else {
                            playCountView.setVisibility(View.GONE);
                        }
                        playerMetadataContainer.addView(playCountView);

                        // Koito, when configured and matched, is authoritative; block server increments then.
                        final boolean[] koitoOverridden = {false};
                        final long[] lastSeenVersion = {MediaManager.getScrobbleVersion()};
                        MediaManager.getScrobbledSongId().observe(getViewLifecycleOwner(), scrobbledId -> {
                            if (koitoOverridden[0]) return;
                            long currentVersion = MediaManager.getScrobbleVersion();
                            if (currentVersion <= lastSeenVersion[0]) return;
                            lastSeenVersion[0] = currentVersion;
                            if (currentSongId != null && currentSongId.equals(scrobbledId)) {
                                long updated = basePlayCount + MediaManager.getPlayCountIncrement(currentSongId);
                                playCountView.setText(updated + " plays");
                                playCountView.setVisibility(View.VISIBLE);
                            }
                        });

                        if (Preferences.useKoitoStats() && com.cappielloantonio.tempo.koito.KoitoClient.isConfigured()) {
                            String kArtist = mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : null;
                            String kTitle = mediaMetadata.title != null ? String.valueOf(mediaMetadata.title) : null;
                            String kAlbum = mediaMetadata.albumTitle != null ? String.valueOf(mediaMetadata.albumTitle) : null;
                            playerBottomSheetViewModel.getKoitoTrackCount(kArtist, kTitle, kAlbum).observe(getViewLifecycleOwner(), koitoCount -> {
                                if (koitoCount != null && koitoCount > 0) {
                                    koitoOverridden[0] = true;
                                    playCountView.setText(koitoCount + " plays");
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
        String alignment = Preferences.getMetadataAlignment();
        switch (alignment) {
            case Preferences.METADATA_ALIGNMENT_LEFT:
                textView.setGravity(android.view.Gravity.START);
                break;
            case Preferences.METADATA_ALIGNMENT_RIGHT:
                textView.setGravity(android.view.Gravity.END);
                break;
            default:
                textView.setGravity(android.view.Gravity.CENTER);
                break;
        }
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        textView.setSelected(true);
        textView.setPadding(0, UIUtil.dpToPx(requireContext(), 2), 0, UIUtil.dpToPx(requireContext(), 2));
        return textView;
    }

    private int getPlayerTextColor() {
        if (playerContentColor != null) return playerContentColor;
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;
    }

    private int contentColor() {
        return playerContentColor != null ? playerContentColor
                : UIUtil.getThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurface);
    }

    /**
     * Recolours the text, icons, buttons and seek bar over the album-coloured
     * background so they stay visible on any artwork. The artwork and video area
     * are left alone; the time and rating labels are dimmed slightly.
     */
    private void applyPlayerContentColor() {
        if (bind == null) return;
        int color = contentColor();
        tintPlayerViews(bind.getRoot(), color);

        int secondary = androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0xB3);
        for (int id : new int[]{R.id.exo_position, R.id.player_remaining_time, R.id.rating_text}) {
            View label = bind.getRoot().findViewById(id);
            if (label instanceof TextView) ((TextView) label).setTextColor(secondary);
        }

        // These show an active state in the accent colour; let them re-apply it.
        updateSleepTimerButton();
        setSwitchButtonActive(videoMode);
    }

    private void tintPlayerViews(View view, int color) {
        int id = view.getId();
        if (id == R.id.player_media_cover_view_pager || id == R.id.player_video_container) return;

        android.content.res.ColorStateList tint = android.content.res.ColorStateList.valueOf(color);
        if (view instanceof androidx.media3.ui.DefaultTimeBar) {
            androidx.media3.ui.DefaultTimeBar bar = (androidx.media3.ui.DefaultTimeBar) view;
            bar.setPlayedColor(color);
            bar.setScrubberColor(color);
            bar.setBufferedColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0x66));
            bar.setUnplayedColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0x33));
        } else if (view instanceof android.widget.RatingBar) {
            ((android.widget.RatingBar) view).setProgressTintList(tint);
        } else if (view instanceof ImageView) {
            // Icons are drawn either as the image (app:tint) or as the background.
            if (((ImageView) view).getImageTintList() != null) ((ImageView) view).setImageTintList(tint);
            if (view.getBackgroundTintList() != null) view.setBackgroundTintList(tint);
        } else if (view instanceof android.widget.CompoundButton) {
            // Favourite / skip-silence toggles draw their icon as a tinted background.
            if (view.getBackgroundTintList() != null) view.setBackgroundTintList(tint);
        } else if (view instanceof TextView && !(view instanceof com.google.android.material.button.MaterialButton)
                && !(view instanceof com.google.android.material.chip.Chip)) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tintPlayerViews(group.getChildAt(i), color);
        }
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

        // Track info moved to the overflow menu; the cover button is gone.
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
            updateVideoMatch(media);
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
            EqualizerPresetPickerDialog dialog = new EqualizerPresetPickerDialog();
            dialog.setEditListener(() -> {
                NavController navController = NavHostFragment.findNavController(this);
                NavOptions navOptions = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .build();
                navController.navigate(R.id.equalizerFragment, null, navOptions);
                if (activity != null) activity.collapseBottomSheetDelayed();
            });
            dialog.show(requireActivity().getSupportFragmentManager(), null);
        });
    }

    private static final int[] SLEEP_TIMER_PRESETS = {0, 15, 30, 45, 60};

    private void showSleepTimerDialog() {
        boolean active = Preferences.getSleepTimerEnd() > System.currentTimeMillis();
        int activeMinutes = active ? Preferences.getSleepTimerMinutes() : 0;

        // Labels: Off, presets, then a Custom entry (showing its value when a custom timer is active).
        int customIndex = SLEEP_TIMER_PRESETS.length;
        boolean customActive = active && activeMinutes > 0 && presetIndexOf(activeMinutes) < 0;
        String[] labels = new String[SLEEP_TIMER_PRESETS.length + 1];
        labels[0] = getString(R.string.sleep_timer_off);
        for (int i = 1; i < SLEEP_TIMER_PRESETS.length; i++) {
            labels[i] = getString(R.string.sleep_timer_minutes, SLEEP_TIMER_PRESETS[i]);
        }
        labels[customIndex] = customActive
                ? getString(R.string.sleep_timer_custom_active, activeMinutes)
                : getString(R.string.sleep_timer_custom);

        int checked;
        if (!active || activeMinutes == 0) checked = 0;
        else if (customActive) checked = customIndex;
        else checked = presetIndexOf(activeMinutes);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.sleep_timer_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == customIndex) {
                        showCustomSleepTimerInput();
                    } else {
                        setSleepTimer(SLEEP_TIMER_PRESETS[which]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                .show();
    }

    private int presetIndexOf(int minutes) {
        for (int i = 0; i < SLEEP_TIMER_PRESETS.length; i++) {
            if (SLEEP_TIMER_PRESETS[i] == minutes) return i;
        }
        return -1;
    }

    private void showCustomSleepTimerInput() {
        View view = getLayoutInflater().inflate(R.layout.dialog_sleep_timer_custom, null);
        EditText input = view.findViewById(R.id.sleep_timer_input);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.sleep_timer_custom_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        int minutes = Integer.parseInt(input.getText().toString().trim());
                        if (minutes > 0) setSleepTimer(minutes);
                    } catch (NumberFormatException ignored) {
                        // no valid number entered; leave the timer unchanged
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                .show();
    }

    private void setSleepTimer(int minutes) {
        Intent intent = new Intent(BaseMediaService.ACTION_SET_SLEEP_TIMER)
                .setPackage(requireContext().getPackageName());
        intent.putExtra(BaseMediaService.EXTRA_SLEEP_MINUTES, minutes);
        requireContext().sendBroadcast(intent);
        Toast.makeText(requireContext(),
                minutes == 0 ? getString(R.string.sleep_timer_cancelled)
                        : getString(R.string.sleep_timer_set, minutes),
                Toast.LENGTH_SHORT).show();
        sleepTimerButton.postDelayed(this::updateSleepTimerButton, 100);
    }

    private void updateSleepTimerButton() {
        if (sleepTimerButton == null) return;
        boolean active = Preferences.getSleepTimerEnd() > System.currentTimeMillis();
        int color = UIUtil.getThemeColor(requireContext(),
                com.google.android.material.R.attr.colorPrimary);
        if (!active) color = contentColor();
        sleepTimerButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
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
                } else if (id == R.id.menu_track_info) {
                    if (mediaBrowserListenableFuture != null && mediaBrowserListenableFuture.isDone()) {
                        try {
                            MediaBrowser browser = mediaBrowserListenableFuture.get();
                            new TrackInfoDialog(browser.getMediaMetadata())
                                    .show(activity.getSupportFragmentManager(), null);
                        } catch (Exception ignored) {
                        }
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

    // ----------------------------------------------------------------------
    // Music video switching
    // ----------------------------------------------------------------------

    private MediaBrowser getBrowser() {
        if (mediaBrowserListenableFuture != null && mediaBrowserListenableFuture.isDone()) {
            try {
                return mediaBrowserListenableFuture.get();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Called on every track change: hides the button, then looks up a match. */
    private void updateVideoMatch(Child media) {
        if (switchToVideoButton == null) return;

        if (videoMode) teardownVideo();
        matchedVideo = null;
        switchToVideoButton.setVisibility(View.GONE);

        if (media == null || !PopinnClient.isConfigured()) {
            matchQueryKey = null;
            return;
        }

        String artist = media.getArtist();
        String title = media.getTitle();
        if (artist == null || title == null) {
            matchQueryKey = null;
            return;
        }

        String key = artist + "\u0000" + title;
        matchQueryKey = key;
        popinnRepository.findVideoForSong(artist, title).observe(getViewLifecycleOwner(), video -> {
            if (switchToVideoButton == null || !key.equals(matchQueryKey)) return;
            matchedVideo = video;
            switchToVideoButton.setVisibility(video != null ? View.VISIBLE : View.GONE);
        });
    }

    private void toggleVideoMode() {
        if (videoMode) exitVideoMode();
        else enterVideoMode();
    }

    private void enterVideoMode() {
        if (videoMode || matchedVideo == null || playerVideoContainer == null) return;
        MediaBrowser browser = getBrowser();
        if (browser == null) return;

        videoMode = true;
        videoWatchedMs = 0;
        videoWatchStartedAt = C.TIME_UNSET;
        videoNavidromeScrobbled = false;
        videoPopinnReported = false;
        videoDurationSeconds = matchedVideo.getDuration() != null ? matchedVideo.getDuration() : 0;

        browser.pause();

        setSwitchButtonActive(true);
        playerMediaCoverViewPager.setUserInputEnabled(false);
        playerVideoContainer.setVisibility(View.VISIBLE);

        if (PopinnClient.hasToken()) {
            startVideoPlayback();
        } else {
            new Thread(() -> {
                PopinnClient.login();
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    if (bind != null && videoMode) startVideoPlayback();
                });
            }).start();
        }
    }

    private void startVideoPlayback() {
        String url = PopinnClient.toAbsoluteUrl(
                matchedVideo.getPlaybackUrl() != null ? matchedVideo.getPlaybackUrl() : matchedVideo.getVideoUrl());
        if (url == null) {
            exitVideoMode();
            return;
        }

        // Subtitles are declared on the MediaItem up front, so fetch them before
        // building the player. A failure here is not fatal — the video still plays.
        PopinnApi api = PopinnClient.getApi();
        if (api == null || matchedVideo.getId() == null) {
            buildVideoPlayer(url, Collections.emptyList());
            return;
        }

        api.getSubtitles(matchedVideo.getId()).enqueue(new Callback<List<PopinnSubtitle>>() {
            @Override
            public void onResponse(@NonNull Call<List<PopinnSubtitle>> call, @NonNull Response<List<PopinnSubtitle>> response) {
                if (bind == null || !videoMode) return;
                List<PopinnSubtitle> subtitles = response.isSuccessful() && response.body() != null
                        ? response.body() : Collections.emptyList();
                buildVideoPlayer(url, subtitles);
            }

            @Override
            public void onFailure(@NonNull Call<List<PopinnSubtitle>> call, @NonNull Throwable t) {
                if (bind == null || !videoMode) return;
                buildVideoPlayer(url, Collections.emptyList());
            }
        });
    }

    private void buildVideoPlayer(String url, List<PopinnSubtitle> subtitles) {
        if (bind == null || !videoMode) return;

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(PopinnClient.getAuthHeaders());

        videoPlayer = new ExoPlayer.Builder(requireContext())
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        videoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && videoDurationSeconds <= 0) {
                    long d = videoPlayer.getDuration();
                    if (d != C.TIME_UNSET && d > 0) videoDurationSeconds = (int) (d / 1000);
                }
                if (state == Player.STATE_ENDED) {
                    stopWatchClock();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) startWatchClock();
                else stopWatchClock();
            }

            @Override
            public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                if (playerVideoAspect != null && videoSize.height > 0) {
                    float ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height;
                    playerVideoAspect.setAspectRatio(ratio);
                }
            }

            @Override
            public void onCues(@NonNull CueGroup cueGroup) {
                if (playerVideoSubtitles != null) playerVideoSubtitles.setCues(cueGroup.cues);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(requireContext(), R.string.music_video_player_error, Toast.LENGTH_SHORT).show();
                exitVideoMode();
            }
        });

        videoPlayer.setVideoSurfaceView(playerVideoView);
        applyInlineSubtitleSize();
        // The player screen's seekbar now drives the video; the transport and
        // quick-action buttons don't apply to a single video, so hide them.
        bind.nowPlayingMediaControllerView.setPlayer(videoPlayer);
        applyVideoModeControls(true);

        List<MediaItem.SubtitleConfiguration> subtitleConfigurations = toSubtitleConfigurations(subtitles);
        videoPlayer.setMediaItem(new MediaItem.Builder()
                .setUri(url)
                .setSubtitleConfigurations(subtitleConfigurations)
                .build());

        if (!subtitleConfigurations.isEmpty()) {
            String language = subtitleConfigurations.get(0).language;
            if (language != null) {
                videoPlayer.setTrackSelectionParameters(
                        videoPlayer.getTrackSelectionParameters().buildUpon()
                                .setPreferredTextLanguage(language)
                                .build());
            }
        }

        videoPlayer.seekTo(0);
        videoPlayer.setPlayWhenReady(true);
        videoPlayer.prepare();
    }

    /**
     * In video mode only the seekbar and play/pause make sense, so the transport
     * and quick-action buttons are hidden and restored on the way back to audio.
     */
    private void applyVideoModeControls(boolean video) {
        if (bind == null) return;

        if (video) {
            bind.nowPlayingMediaControllerView.setShowShuffleButton(false);
            bind.nowPlayingMediaControllerView.setShowPreviousButton(false);
            bind.nowPlayingMediaControllerView.setShowNextButton(false);
            bind.nowPlayingMediaControllerView.setShowRewindButton(false);
            bind.nowPlayingMediaControllerView.setShowFastForwardButton(false);
            bind.nowPlayingMediaControllerView.setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
        }

        int visibility = video ? View.GONE : View.VISIBLE;
        if (addToPlaylistButton != null) addToPlaylistButton.setVisibility(visibility);
        if (equalizerButton != null) equalizerButton.setVisibility(visibility);
        if (lyricsButton != null) lyricsButton.setVisibility(visibility);
        if (playerOpenQueueButton != null) playerOpenQueueButton.setVisibility(visibility);
    }

    /** Small, fixed caption size for the letterboxed video inside the player. */
    private void applyInlineSubtitleSize() {
        if (playerVideoSubtitles != null) {
            playerVideoSubtitles.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        }
    }

    private List<MediaItem.SubtitleConfiguration> toSubtitleConfigurations(List<PopinnSubtitle> subtitles) {
        List<MediaItem.SubtitleConfiguration> configurations = new ArrayList<>();
        for (PopinnSubtitle subtitle : subtitles) {
            String subtitleUrl = PopinnClient.toAbsoluteUrl(subtitle.getUrl());
            if (subtitleUrl == null) continue;
            configurations.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                    // Always WebVTT: the server converts SubRip on the way out.
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(subtitle.getLanguage())
                    .setSelectionFlags(configurations.isEmpty() ? C.SELECTION_FLAG_DEFAULT : 0)
                    .build());
        }
        return configurations;
    }

    private void exitVideoMode() {
        teardownVideo();
        // Switching back to audio restarts the track from the beginning.
        MediaBrowser browser = getBrowser();
        if (browser != null) {
            browser.seekTo(0);
            browser.play();
        }
    }

    /** Releases the video player and restores the audio UI. Reports watch time. */
    private void teardownVideo() {
        if (videoFullscreen) exitFullscreen();
        stopWatchClock();
        reportPopinnPlay();

        if (videoPlayer != null) {
            videoPlayer.release();
            videoPlayer = null;
        }

        MediaBrowser browser = getBrowser();
        if (browser != null && bind != null) {
            bind.nowPlayingMediaControllerView.setPlayer(browser);
            setMediaControllerUI(browser);
            applyVideoModeControls(false);
        }

        if (playerVideoContainer != null) playerVideoContainer.setVisibility(View.GONE);
        if (playerMediaCoverViewPager != null) playerMediaCoverViewPager.setUserInputEnabled(true);
        setSwitchButtonActive(false);
        videoMode = false;
    }

    private void setSwitchButtonActive(boolean active) {
        if (switchToVideoButton == null || getContext() == null) return;
        switchToVideoButton.setColorFilter(active
                ? UIUtil.getThemeColor(requireContext(), com.google.android.material.R.attr.colorPrimary)
                : contentColor());
    }

    private void startWatchClock() {
        if (videoWatchStartedAt == C.TIME_UNSET) videoWatchStartedAt = SystemClock.elapsedRealtime();
    }

    private void stopWatchClock() {
        if (videoWatchStartedAt == C.TIME_UNSET) return;
        videoWatchedMs += SystemClock.elapsedRealtime() - videoWatchStartedAt;
        videoWatchStartedAt = C.TIME_UNSET;
        maybeScrobbleToNavidrome();
    }

    /**
     * Credits the song on Navidrome once enough of the video has been watched,
     * using the same percentage threshold as audio scrobbling (default 90%).
     */
    private void maybeScrobbleToNavidrome() {
        if (videoNavidromeScrobbled) return;

        int dur = videoDurationSeconds;
        if (dur <= 0 && videoPlayer != null) {
            long d = videoPlayer.getDuration();
            if (d != C.TIME_UNSET && d > 0) dur = (int) (d / 1000);
        }
        int threshold = Preferences.getScrobbleThreshold();
        if (dur <= 0 || videoWatchedMs * 100 < dur * 1000L * threshold) return;

        MediaBrowser browser = getBrowser();
        if (browser != null && browser.getCurrentMediaItem() != null) {
            MediaManager.scrobble(browser.getCurrentMediaItem(), true, System.currentTimeMillis());
            videoNavidromeScrobbled = true;
        }
    }

    /** Fire-and-forget watch report to Popinn, at most once per video session. */
    private void reportPopinnPlay() {
        if (videoPopinnReported || matchedVideo == null || matchedVideo.getId() == null) return;

        long watchedSeconds = videoWatchedMs / 1000;
        if (watchedSeconds < 1) return;

        PopinnApi api = PopinnClient.getApi();
        if (api == null) return;

        videoPopinnReported = true;
        Integer duration = matchedVideo.getDuration() != null && matchedVideo.getDuration() > 0
                ? matchedVideo.getDuration()
                : (videoDurationSeconds > 0 ? videoDurationSeconds : null);

        api.recordPlay(matchedVideo.getId(), new PopinnPlayRequest((double) watchedSeconds, duration))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) { }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) { }
                });
    }

    // ---- Fullscreen ----

    private void toggleFullscreen() {
        if (videoFullscreen) exitFullscreen();
        else enterFullscreen();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void enterFullscreen() {
        if (videoFullscreen || playerVideoContainer == null || activity == null) return;
        if (!(playerVideoContainer.getParent() instanceof ViewGroup)) return;

        videoOriginalParent = (ViewGroup) playerVideoContainer.getParent();
        videoOriginalIndex = videoOriginalParent.indexOfChild(playerVideoContainer);
        videoOriginalParams = playerVideoContainer.getLayoutParams();
        videoOriginalParent.removeView(playerVideoContainer);

        ViewGroup content = activity.findViewById(android.R.id.content);
        content.addView(playerVideoContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        savedOrientation = activity.getRequestedOrientation();
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        // Draw edge to edge, including behind the notch, so the black video fills
        // the screen instead of leaving a white inset near the cutout.
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            activity.getWindow().setAttributes(attrs);
        }

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), playerVideoContainer);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Standard video controls, created only for fullscreen so their exo_*
        // ids never collide with the audio seekbar's while inline.
        fullscreenController = new PlayerControlView(requireContext());
        fullscreenController.setPlayer(videoPlayer);
        fullscreenController.setShowTimeoutMs(3000);
        FrameLayout.LayoutParams controllerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controllerParams.gravity = android.view.Gravity.BOTTOM;
        playerVideoContainer.addView(fullscreenController, controllerParams);

        // Full-size captions for the fullscreen video.
        if (playerVideoSubtitles != null) playerVideoSubtitles.setUserDefaultTextSize();

        playerVideoContainer.setOnTouchListener((v, event) -> {
            getFullscreenGestureDetector().onTouchEvent(event);
            return true;
        });

        fullscreenBackCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                exitFullscreen();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(fullscreenBackCallback);

        videoFullscreen = true;
    }

    private void exitFullscreen() {
        if (!videoFullscreen || activity == null) return;

        if (fullscreenBackCallback != null) {
            fullscreenBackCallback.remove();
            fullscreenBackCallback = null;
        }

        playerVideoContainer.setOnTouchListener(null);
        applyInlineSubtitleSize();
        if (fullscreenController != null) {
            fullscreenController.setPlayer(null);
            playerVideoContainer.removeView(fullscreenController);
            fullscreenController = null;
        }

        ViewGroup content = activity.findViewById(android.R.id.content);
        content.removeView(playerVideoContainer);

        if (videoOriginalParent != null) {
            videoOriginalParent.addView(playerVideoContainer, videoOriginalIndex, videoOriginalParams);
            videoOriginalParent = null;
        }

        activity.setRequestedOrientation(savedOrientation);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        controller.show(WindowInsetsCompat.Type.systemBars());
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            activity.getWindow().setAttributes(attrs);
        }
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        videoSeekHandler.removeCallbacksAndMessages(null);
        videoFullscreen = false;
    }

    private GestureDetector getFullscreenGestureDetector() {
        if (fullscreenGestureDetector == null) {
            fullscreenGestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(@NonNull MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                    if (fullscreenController == null) return true;
                    if (fullscreenController.isFullyVisible()) fullscreenController.hide();
                    else fullscreenController.show();
                    return true;
                }

                @Override
                public boolean onDoubleTap(@NonNull MotionEvent e) {
                    seekVideoBy(e.getX() >= playerVideoView.getWidth() / 2f);
                    return true;
                }
            });
        }
        return fullscreenGestureDetector;
    }

    private void seekVideoBy(boolean forward) {
        if (videoPlayer == null) return;

        if (videoAccumulatedSeek == 0 || forward != videoLastSeekForward) {
            videoAccumulatedSeek = VIDEO_SEEK_STEP_SECONDS;
        } else {
            videoAccumulatedSeek += VIDEO_SEEK_STEP_SECONDS;
        }
        videoLastSeekForward = forward;

        long target = videoPlayer.getCurrentPosition() + (long) VIDEO_SEEK_STEP_SECONDS * 1000 * (forward ? 1 : -1);
        long duration = videoPlayer.getDuration();
        if (duration != C.TIME_UNSET) target = Math.min(target, duration);
        videoPlayer.seekTo(Math.max(target, 0));

        showVideoSeekFeedback(forward);
    }

    private void showVideoSeekFeedback(boolean forward) {
        TextView shown = forward ? videoSeekForwardLabel : videoSeekBackLabel;
        TextView hidden = forward ? videoSeekBackLabel : videoSeekForwardLabel;
        if (shown == null || hidden == null) return;

        shown.setText(getString(
                forward ? R.string.music_video_seek_forward : R.string.music_video_seek_back,
                videoAccumulatedSeek));
        hidden.animate().cancel();
        hidden.setAlpha(0f);
        shown.animate().cancel();
        shown.setAlpha(1f);

        videoSeekHandler.removeCallbacksAndMessages(null);
        videoSeekHandler.postDelayed(() -> {
            videoAccumulatedSeek = 0;
            if (videoSeekBackLabel != null) videoSeekBackLabel.animate().alpha(0f).setDuration(200).start();
            if (videoSeekForwardLabel != null) videoSeekForwardLabel.animate().alpha(0f).setDuration(200).start();
        }, 800);
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
