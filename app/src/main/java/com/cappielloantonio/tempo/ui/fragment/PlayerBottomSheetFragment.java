package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.viewpager2.widget.ViewPager2;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentPlayerBottomSheetBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.PlayQueue;

import java.util.Date;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.fragment.pager.PlayerControllerVerticalPager;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.UIUtil;
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.stream.IntStream;

@OptIn(markerClass = UnstableApi.class)
public class PlayerBottomSheetFragment extends Fragment {
    private FragmentPlayerBottomSheetBinding bind;

    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private Handler progressBarHandler;
    private Runnable progressBarRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        bind = FragmentPlayerBottomSheetBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        customizeBottomSheetAction();
        initViewPager();
        setHeaderBookmarksButton();
        initFavoriteButton();
        applyPlayerBackgroundColor();
        applyPlayerTextColor();

        return view;
    }

    private void initFavoriteButton() {
        playerBottomSheetViewModel.getLiveMedia().observe(getViewLifecycleOwner(), media -> {
            if (media != null && bind != null) {
                bind.playerHeaderLayout.buttonFavoriteMini.setChecked(media.getStarred() != null);
                bind.playerHeaderLayout.buttonFavoriteMini.setOnClickListener(v -> playerBottomSheetViewModel.setFavorite(requireContext(), media));
            }
        });

        MediaManager.getFavoriteEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null || bind == null) return;
            String songId = (String) event[0];
            Date starred = (Date) event[1];
            Child media = playerBottomSheetViewModel.getLiveMedia().getValue();
            if (media != null && media.getId().equals(songId)) {
                bind.playerHeaderLayout.buttonFavoriteMini.setChecked(starred != null);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
        bindMediaController();
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

    private void customizeBottomSheetAction() {
        bind.playerHeaderLayout.getRoot().setOnClickListener(view -> ((MainActivity) requireActivity()).expandBottomSheet());
    }

    private void initViewPager() {
        bind.playerBodyLayout.playerBodyBottomSheetViewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        bind.playerBodyLayout.playerBodyBottomSheetViewPager.setAdapter(new PlayerControllerVerticalPager(this));
    }

    private void applyPlayerBackgroundColor() {
        if (bind == null) return;

        int playerBackgroundColor = UIUtil.getPlayerBackgroundColor(requireContext());
        bind.getRoot().setBackgroundColor(Color.TRANSPARENT);
        bind.playerBodyLayout.playerBodyBottomSheetViewPager.setBackgroundColor(playerBackgroundColor);
    }

    private void applyPlayerTextColor() {
        if (bind == null) return;

        int textColor = getPlayerTextColor();
        bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setTextColor(textColor);
        bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setTextColor(textColor);
    }

    private int getPlayerTextColor() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaController.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();

                mediaBrowser.setShuffleModeEnabled(Preferences.isShuffleModeEnabled());
                mediaBrowser.setRepeatMode(Preferences.getRepeatMode());

                setMediaControllerListener(mediaBrowser);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setMediaControllerListener(MediaBrowser mediaBrowser) {
        defineProgressBarHandler(mediaBrowser);
        setMediaControllerUI(mediaBrowser);
        setMetadata(mediaBrowser.getMediaMetadata());
        setContentDuration(mediaBrowser.getContentDuration());
        setPlayingState(mediaBrowser.isPlaying());
        setHeaderMediaController(mediaBrowser);
        setHeaderNextButtonState(mediaBrowser.hasNextMediaItem());

        mediaBrowser.addListener(new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                setMediaControllerUI(mediaBrowser);
                setMetadata(mediaMetadata);
                setContentDuration(mediaBrowser.getContentDuration());
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                setPlayingState(isPlaying);
            }

            @Override
            public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
                Player.Listener.super.onSkipSilenceEnabledChanged(skipSilenceEnabled);
            }

            @Override
            public void onEvents(Player player, Player.Events events) {
                setHeaderNextButtonState(mediaBrowser.hasNextMediaItem());
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
        if (mediaMetadata.extras != null) {
            playerBottomSheetViewModel.setLiveMedia(getViewLifecycleOwner(), mediaMetadata.extras.getString("type"), mediaMetadata.extras.getString("id"));
            playerBottomSheetViewModel.setLiveAlbum(getViewLifecycleOwner(), mediaMetadata.extras.getString("type"), mediaMetadata.extras.getString("albumId"));
            playerBottomSheetViewModel.setLiveArtist(getViewLifecycleOwner(), mediaMetadata.extras.getString("type"), mediaMetadata.extras.getString("artistId"));
            playerBottomSheetViewModel.setLiveDescription(mediaMetadata.extras.getString("description", null));

            String type = mediaMetadata.extras.getString("type");

            if (Objects.equals(type, Constants.MEDIA_TYPE_RADIO)) {
                // For radio: keep header consistent with full player
                String stationName = mediaMetadata.extras.getString(
                        "stationName",
                        mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : ""
                );

                String artist = mediaMetadata.extras.getString("radioArtist", "");
                String title = mediaMetadata.extras.getString("radioTitle", "");

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

                bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setText(mainTitle);
                bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setText(stationName);

                bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setVisibility(!TextUtils.isEmpty(mainTitle) ? View.VISIBLE : View.GONE);
                bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setVisibility(!TextUtils.isEmpty(stationName) ? View.VISIBLE : View.GONE);
            } else {
                // Default (music, podcast, etc.)
                bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setText(mediaMetadata.extras.getString("title"));
                bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setText(
                        mediaMetadata.artist != null
                                ? mediaMetadata.artist
                                : ""
                );

                bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setVisibility(mediaMetadata.extras.getString("title") != null && !Objects.equals(mediaMetadata.extras.getString("title"), "") ? View.VISIBLE : View.GONE);
                bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setVisibility(
                        mediaMetadata.extras.getString("artist") != null && !Objects.equals(mediaMetadata.extras.getString("artist"), "")
                                ? View.VISIBLE
                                : View.GONE);
            }

            CustomGlideRequest.Builder
                    .from(requireContext(), mediaMetadata.extras.getString("coverArtId"), CustomGlideRequest.ResourceType.Song)
                    .build()
                    .into(bind.playerHeaderLayout.playerHeaderMediaCoverImage);
        }
    }


    private void setMediaControllerUI(MediaBrowser mediaBrowser) {
        if (mediaBrowser.getMediaMetadata().extras != null) {
            switch (mediaBrowser.getMediaMetadata().extras.getString("type", Constants.MEDIA_TYPE_MUSIC)) {
                case Constants.MEDIA_TYPE_PODCAST:
                    bind.playerHeaderLayout.playerHeaderFastForwardMediaButton.setVisibility(View.VISIBLE);
                    bind.playerHeaderLayout.playerHeaderRewindMediaButton.setVisibility(View.VISIBLE);
                    bind.playerHeaderLayout.playerHeaderNextMediaButton.setVisibility(View.GONE);
                    break;
                case Constants.MEDIA_TYPE_MUSIC:
                default:
                    bind.playerHeaderLayout.playerHeaderFastForwardMediaButton.setVisibility(View.GONE);
                    bind.playerHeaderLayout.playerHeaderRewindMediaButton.setVisibility(View.GONE);
                    bind.playerHeaderLayout.playerHeaderNextMediaButton.setVisibility(View.VISIBLE);
                    break;
            }
        }
    }

    private void setContentDuration(long duration) {
        bind.playerHeaderLayout.playerHeaderProgressBar.setMax((int) (duration / 1000));
    }

    private void setProgress(MediaBrowser mediaBrowser) {
        if (bind != null)
            bind.playerHeaderLayout.playerHeaderProgressBar.setProgress((int) (mediaBrowser.getCurrentPosition() / 1000), true);
    }

    private void setPlayingState(boolean isPlaying) {
        bind.playerHeaderLayout.playerHeaderButton.setChecked(isPlaying);
        runProgressBarHandler(isPlaying);
    }

    private void setHeaderMediaController(MediaBrowser mediaBrowser) {
        bind.playerHeaderLayout.playerHeaderButton.setOnClickListener(view -> {
            if (mediaBrowser.isPlaying()) {
                mediaBrowser.pause();
            } else {
                mediaBrowser.play();
            }
        });
        bind.playerHeaderLayout.playerHeaderNextMediaButton.setOnClickListener(view -> mediaBrowser.seekToNext());
        bind.playerHeaderLayout.playerHeaderRewindMediaButton.setOnClickListener(view -> mediaBrowser.seekBack());
        bind.playerHeaderLayout.playerHeaderFastForwardMediaButton.setOnClickListener(view -> mediaBrowser.seekForward());
    }

    private void setHeaderNextButtonState(boolean isEnabled) {
        bind.playerHeaderLayout.playerHeaderNextMediaButton.setEnabled(isEnabled);
        bind.playerHeaderLayout.playerHeaderNextMediaButton.setAlpha(isEnabled ? (float) 1.0 : (float) 0.3);
    }

    public View getPlayerHeader() {
        return requireView().findViewById(R.id.player_header_layout);
    }

    public void goBackToFirstPage() {
        bind.playerBodyLayout.playerBodyBottomSheetViewPager.setCurrentItem(0, false);
        goToControllerPage();
    }

    public void goToControllerPage() {
        PlayerControllerVerticalPager playerControllerVerticalPager = (PlayerControllerVerticalPager) bind.playerBodyLayout.playerBodyBottomSheetViewPager.getAdapter();
        if (playerControllerVerticalPager != null) {
            PlayerControllerFragment playerControllerFragment = (PlayerControllerFragment) playerControllerVerticalPager.getRegisteredFragment(0);
            if (playerControllerFragment != null) {
                playerControllerFragment.goToControllerPage();
            }
        }
    }

    public void goToLyricsPage() {
        PlayerControllerVerticalPager playerControllerVerticalPager = (PlayerControllerVerticalPager) bind.playerBodyLayout.playerBodyBottomSheetViewPager.getAdapter();
        if (playerControllerVerticalPager != null) {
            PlayerControllerFragment playerControllerFragment = (PlayerControllerFragment) playerControllerVerticalPager.getRegisteredFragment(0);
            if (playerControllerFragment != null) {
                playerControllerFragment.goToLyricsPage();
            }
        }
    }

    public void goToQueuePage() {
        bind.playerBodyLayout.playerBodyBottomSheetViewPager.setCurrentItem(1, true);
    }

    public void setPlayerControllerVerticalPagerDraggableState(Boolean isDraggable) {
        ViewPager2 playerControllerVerticalPager = (ViewPager2) bind.playerBodyLayout.playerBodyBottomSheetViewPager;
        playerControllerVerticalPager.setUserInputEnabled(isDraggable);
    }

    public void setBodyVisibility(boolean visible) {
        if (bind != null) {
            bind.playerBodyLayout.getRoot().setVisibility(visible ? View.VISIBLE : View.GONE);
            bind.getRoot().setBackgroundColor(visible ? UIUtil.getPlayerBackgroundColor(requireContext()) : Color.TRANSPARENT);
        }
    }

    public void setMiniPlayerWidth(int width) {
        if (bind != null) {
            ViewGroup.LayoutParams params = bind.playerHeaderLayout.getRoot().getLayoutParams();
            params.width = width;
            bind.playerHeaderLayout.getRoot().setLayoutParams(params);
        }
    }

    private void defineProgressBarHandler(MediaBrowser mediaBrowser) {
        progressBarHandler = new Handler();
        progressBarRunnable = () -> {
            setProgress(mediaBrowser);
            progressBarHandler.postDelayed(progressBarRunnable, 1000);
        };
    }

    private void runProgressBarHandler(boolean isPlaying) {
        if (isPlaying) {
            progressBarHandler.postDelayed(progressBarRunnable, 1000);
        } else {
            progressBarHandler.removeCallbacks(progressBarRunnable);
        }
    }

    private void setHeaderBookmarksButton() {
        if (Preferences.isSyncronizationEnabled()) {
            playerBottomSheetViewModel.getPlayQueue().observeForever(new Observer<PlayQueue>() {
                @Override
                public void onChanged(PlayQueue playQueue) {
                    playerBottomSheetViewModel.getPlayQueue().removeObserver(this);

                    if (bind == null) return;

                    if (playQueue != null && !playQueue.getEntries().isEmpty()) {
                        int index = IntStream.range(0, playQueue.getEntries().size()).filter(ix -> playQueue.getEntries().get(ix).getId().equals(playQueue.getCurrent())).findFirst().orElse(-1);

                        if (index != -1) {
                            bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.VISIBLE);
                            bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setOnClickListener(v -> {
                                MediaManager.startQueue(mediaBrowserListenableFuture, playQueue.getEntries(), index);
                                bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.GONE);
                            });
                        }
                    } else {
                        bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.GONE);
                        bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setOnClickListener(null);
                    }
                }
            });

            bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setOnLongClickListener(v -> {
                bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.GONE);
                return true;
            });

            new Handler().postDelayed(() -> {
                if (bind != null)
                    bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.GONE);
            }, Preferences.getSyncCountdownTimer() * 1000L);
        }
    }
}
