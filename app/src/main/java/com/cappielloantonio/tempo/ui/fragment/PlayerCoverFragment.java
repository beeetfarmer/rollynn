package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerCoverBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

@UnstableApi
public class PlayerCoverFragment extends Fragment {
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private InnerFragmentPlayerCoverBinding bind;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = InnerFragmentPlayerCoverBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        // Fade the artwork's bottom into the player background colour so it melts
        // into the area where the title and controls sit (Apple Music style).
        playerBottomSheetViewModel.getPlayerDominantColor().observe(getViewLifecycleOwner(), dominant -> {
            if (bind == null) return;
            if (dominant == null) {
                bind.nowPlayingCoverBottomFade.setBackground(null);
                return;
            }
            int fadeColor = com.cappielloantonio.tempo.util.PlayerBackgroundUtil.backgroundTopColor(requireContext(), dominant);
            bind.nowPlayingCoverBottomFade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{android.graphics.Color.TRANSPARENT, fadeColor}));
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();
        bindMediaController();
    }

    @Override
    public void onStop() {
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                setMediaBrowserListener(mediaBrowser);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setMediaBrowserListener(MediaBrowser mediaBrowser) {
        setCover(mediaBrowser.getMediaMetadata());

        mediaBrowser.addListener(new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                setCover(mediaMetadata);
            }
        });
    }

    private void setCover(MediaMetadata mediaMetadata) {
        CustomGlideRequest.Builder
                .from(requireContext(), mediaMetadata.extras != null ? mediaMetadata.extras.getString("coverArtId") : null, CustomGlideRequest.ResourceType.Song)
                .build()
                .into(bind.nowPlayingSongCoverImageView);
    }
}
