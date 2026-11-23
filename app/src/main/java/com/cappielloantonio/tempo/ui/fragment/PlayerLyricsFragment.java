package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.animation.ValueAnimator;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerLyricsBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Line;
import com.cappielloantonio.tempo.subsonic.models.LyricsList;
import com.cappielloantonio.tempo.subsonic.models.StructuredLyrics;
import com.cappielloantonio.tempo.ui.adapter.LyricsAdapter;
import com.cappielloantonio.tempo.ui.view.BlobBackgroundView;
import com.cappielloantonio.tempo.util.LyricItem;
import com.cappielloantonio.tempo.util.LyricsRomanizer;
import com.cappielloantonio.tempo.util.LyricsTimeline;
import com.cappielloantonio.tempo.util.LyricsTranslator;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The lyrics page of the player. Synced lyrics follow playback: the current line is held near the
 * top and fills as it is sung, and tapping a line seeks to it. Unsynced lyrics are shown plainly.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerLyricsFragment extends Fragment {
    private static final String TAG = "PlayerLyricsFragment";

    /** Where the current line's top sits, below the top of the lyrics. */
    private static final int ANCHOR_DP = 110;
    private static final int FADE_TOP_DP = 70;
    private static final int FADE_BOTTOM_DP = 106;
    private static final int FADE_BOTTOM_IMMERSIVE_DP = 118;
    /** How long after the user scrolls the lyrics before they follow playback again. */
    @VisibleForTesting
    static final long RESUME_FOLLOW_MS = 3_000;
    private static final long SCROLL_MS = 750;
    private static final long MODE_MS = 300;
    /** How often to check the position while paused, to catch seeks. */
    private static final long PAUSED_FRAME_MS = 250;
    /** Lines further than this from the screen are jumped to rather than scrolled to. */
    private static final int MAX_SMOOTH_DISTANCE = 6;

    private InnerFragmentPlayerLyricsBinding bind;
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private MediaBrowser mediaBrowser;
    private LyricsAdapter adapter;
    private LinearLayoutManager layoutManager;

    private String currentLyrics;
    private LyricsList currentLyricsList;
    private String currentDescription;
    private boolean lyricsSourceSwitchAvailable;
    private List<String> translatedLines;
    private boolean isTranslating;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean frameScheduled;
    private boolean userScrolling;
    private boolean accessibilityFocusInside;
    /** Set when new lyrics are shown, so the first follow jumps rather than scrolls. */
    private boolean jumpToCurrent;
    private boolean immersive;
    @Nullable
    private ValueAnimator growthAnimator;
    private boolean reduceMotion;

    private final Runnable resumeFollow = () -> {
        userScrolling = false;
        followCurrent(true);
    };

    private final Choreographer.FrameCallback frameCallback = frameTimeNanos -> {
        frameScheduled = false;
        if (bind == null || mediaBrowser == null) return;
        onFrame(mediaBrowser.getCurrentPosition(), mediaBrowser.getDuration());
        scheduleFrame();
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = InnerFragmentPlayerLyricsBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        // The player body leaves the top unpadded (album art bleeds under the status
        // bar), so keep the lyrics below the status bar ourselves, like the queue.
        int basePaddingTop = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int statusTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            v.setPadding(v.getPaddingLeft(), basePaddingTop + statusTop, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);
        reduceMotion = BlobBackgroundView.reduceMotion(requireContext());

        initList();
        initOverlay();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initPanelContent();
        observeDownloadState();
        observeLyricsSourceState();
        styleLyricsActionButtons();

        playerBottomSheetViewModel.getLiveMedia().observe(getViewLifecycleOwner(), this::bindHeader);
        playerBottomSheetViewModel.getLyricsImmersive().observe(getViewLifecycleOwner(),
                on -> setImmersive(Boolean.TRUE.equals(on)));
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindMediaController();
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopFrames();
        handler.removeCallbacks(resumeFollow);
        userScrolling = false;
        if (!Preferences.isDisplayAlwaysOn()) {
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onStop() {
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopFrames();
        handler.removeCallbacksAndMessages(null);
        if (growthAnimator != null) growthAnimator.cancel();
        bind = null;
        adapter = null;
        layoutManager = null;
        currentLyrics = null;
        currentLyricsList = null;
        currentDescription = null;
        lyricsSourceSwitchAvailable = false;
        translatedLines = null;
        isTranslating = false;
        immersive = false;
    }

    private void initList() {
        adapter = new LyricsAdapter(item -> {
            onLineClick(item);
            return kotlin.Unit.INSTANCE;
        });
        adapter.setBlurDistant(!isLowRamDevice());
        adapter.setReduceMotion(reduceMotion);
        layoutManager = new LinearLayoutManager(requireContext());

        RecyclerView list = bind.lyricsList;
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        list.setPadding(list.getPaddingLeft(), dp(ANCHOR_DP), list.getPaddingRight(), 0);
        // Room below the last line, so it can still reach the anchor. The first layout runs
        // without it, which leaves the current line short of the anchor, so place it again.
        list.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int bottom = Math.max(0, (b - t) - dp(ANCHOR_DP));
            if (v.getPaddingBottom() != bottom) {
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
                v.post(() -> followCurrent(false));
            }
        });

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userScrolling = true;
                    handler.removeCallbacks(resumeFollow);
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && userScrolling) {
                    handler.removeCallbacks(resumeFollow);
                    handler.postDelayed(resumeFollow, RESUME_FOLLOW_MS);
                }
            }
        });

        // With TalkBack reading the lyrics, don't scroll them away from under it.
        list.setAccessibilityDelegateCompat(new RecyclerViewAccessibilityDelegate(list) {
            @Override
            public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, @NonNull View child, @NonNull AccessibilityEvent event) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    accessibilityFocusInside = true;
                } else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED) {
                    accessibilityFocusInside = false;
                }
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });

        bind.lyricsFade.setFadeTop(dp(FADE_TOP_DP));
        bind.lyricsFade.setFadeBottom(dp(FADE_BOTTOM_DP));
    }

    private boolean isLowRamDevice() {
        android.app.ActivityManager am = (android.app.ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        return am != null && am.isLowRamDevice();
    }

    private void onLineClick(LyricItem item) {
        if (mediaBrowser == null || adapter == null || !adapter.getSynced()) return;
        int index = adapter.getItems().indexOf(item);
        if (index < 0) return;
        mediaBrowser.seekTo(item.getStartMs());
        handler.removeCallbacks(resumeFollow);
        userScrolling = false;
        adapter.setCurrent(index);
        layoutManager.scrollToPositionWithOffset(index, 0);
    }

    // Playback following

    private void scheduleFrame() {
        if (frameScheduled || bind == null || mediaBrowser == null || !isResumed()) return;
        frameScheduled = true;
        if (mediaBrowser.isPlaying()) {
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } else {
            Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, PAUSED_FRAME_MS);
        }
    }

    private void stopFrames() {
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameScheduled = false;
    }

    /** Moves the lyrics to [positionMs]: the current line, its fill, and the thin progress line. */
    @VisibleForTesting
    void onFrame(long positionMs, long durationMs) {
        if (bind == null || adapter == null) return;
        if (immersive && durationMs > 0) {
            bind.lyricsImmersiveProgress.setProgress((int) Math.min(1000, positionMs * 1000 / durationMs));
        }

        List<LyricItem> items = adapter.getItems();
        if (!adapter.getSynced() || items.isEmpty()) return;

        int index = LyricsTimeline.indexAt(items, positionMs);
        if (index != adapter.getCurrent()) {
            adapter.setCurrent(index);
            followCurrent(!jumpToCurrent);
            jumpToCurrent = false;
        }

        LyricItem item = items.get(index);
        RecyclerView.ViewHolder holder = bind.lyricsList.findViewHolderForAdapterPosition(index);
        if (holder instanceof LyricsAdapter.LineHolder) {
            ((LyricsAdapter.LineHolder) holder).getText().setSungChars(LyricsTimeline.sungChars(item, positionMs));
        } else if (holder instanceof LyricsAdapter.BreakHolder) {
            ((LyricsAdapter.BreakHolder) holder).setProgress(LyricsTimeline.progress(item, positionMs));
        }
    }

    /** Brings the current line to the anchor, unless the user is scrolling or reading with TalkBack. */
    private void followCurrent(boolean smooth) {
        if (bind == null || adapter == null || userScrolling || accessibilityFocusInside) return;
        int index = adapter.getCurrent();
        if (index < 0) return;

        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        boolean near = first != RecyclerView.NO_POSITION
                && index >= first - MAX_SMOOTH_DISTANCE && index <= last + MAX_SMOOTH_DISTANCE;
        if (!smooth || reduceMotion || !near) {
            layoutManager.scrollToPositionWithOffset(index, 0);
            return;
        }
        AnchorScroller scroller = new AnchorScroller(requireContext());
        scroller.setTargetPosition(index);
        layoutManager.startSmoothScroll(scroller);
    }

    /** Scrolls a line's top to the anchor (the list's top padding) with a strong ease-out. */
    private static class AnchorScroller extends LinearSmoothScroller {
        private static final android.view.animation.Interpolator EASE =
                new android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f);

        AnchorScroller(Context context) {
            super(context);
        }

        @Override
        protected int getVerticalSnapPreference() {
            return SNAP_TO_START;
        }

        @Override
        protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
            return 40f / displayMetrics.densityDpi;
        }

        @Override
        protected void onTargetFound(@NonNull View targetView, @NonNull RecyclerView.State state, @NonNull Action action) {
            int dy = calculateDyToMakeVisible(targetView, SNAP_TO_START);
            if (dy != 0) action.update(0, -dy, (int) SCROLL_MS, EASE);
        }
    }

    // Immersive mode, set by the player controller

    private void setImmersive(boolean on) {
        if (bind == null || on == immersive) return;
        immersive = on;

        fade(bind.lyricsHeader, !on, View.INVISIBLE);
        fade(bind.lyricsPill, on, View.GONE);
        if (on) {
            // The page stretches to the bottom of the screen at once, which would carry the
            // buttons down with it mid-fade; they go straight away instead.
            bind.lyricsActionsBar.animate().cancel();
            bind.lyricsActionsBar.setAlpha(0f);
            bind.lyricsActionsBar.setVisibility(View.INVISIBLE);
        } else {
            fade(bind.lyricsActionsBar, true, View.INVISIBLE);
        }
        fade(bind.lyricsImmersiveHint, on, View.GONE);
        fade(bind.lyricsImmersiveProgress, on, View.GONE);
        bind.lyricsFade.setFadeBottom(dp(on ? FADE_BOTTOM_IMMERSIVE_DP : FADE_BOTTOM_DP));

        // The text grows (or shrinks) at its real size a frame at a time, re-wrapping as it goes,
        // with the current line held at the anchor, so nothing jumps when it settles.
        if (growthAnimator != null) growthAnimator.cancel();
        float target = on ? 1f : 0f;
        if (reduceMotion || bind.lyricsList.getWidth() == 0) {
            adapter.setGrowth(target);
            bind.lyricsList.post(() -> followCurrent(false));
            return;
        }
        growthAnimator = ValueAnimator.ofFloat(adapter.getGrowth(), target);
        growthAnimator.setDuration(MODE_MS);
        growthAnimator.setInterpolator(new FastOutSlowInInterpolator());
        growthAnimator.addUpdateListener(a -> applyGrowth((float) a.getAnimatedValue()));
        growthAnimator.start();
    }

    /** One frame of the text growing: sizes it, then holds the current line at the anchor. */
    @VisibleForTesting
    void applyGrowth(float growth) {
        if (bind == null || adapter == null) return;
        adapter.setGrowth(growth);
        followCurrent(false);
    }

    private void fade(View view, boolean show, int hiddenVisibility) {
        view.animate().cancel();
        if (show) {
            if (view.getVisibility() != View.VISIBLE) {
                view.setAlpha(0f);
                view.setVisibility(View.VISIBLE);
            }
            view.animate().alpha(1f).setDuration(MODE_MS).start();
        } else {
            view.animate().alpha(0f).setDuration(MODE_MS)
                    .withEndAction(() -> view.setVisibility(hiddenVisibility)).start();
        }
    }

    // Header

    private void bindHeader(@Nullable Child media) {
        if (bind == null) return;
        String title = media != null && media.getTitle() != null ? media.getTitle() : "";
        String artist = media != null && media.getArtist() != null ? media.getArtist() : "";
        bind.lyricsHeaderTitle.setText(title);
        bind.lyricsHeaderArtist.setText(artist);
        bind.lyricsPillText.setText(artist.isEmpty() ? title : getString(R.string.lyrics_pill_title_artist, title, artist));

        String cover = media != null ? media.getCoverArtId() : null;
        CustomGlideRequest.Builder.from(requireContext(), cover, CustomGlideRequest.ResourceType.Song)
                .build().into(bind.lyricsHeaderArt);
        CustomGlideRequest.Builder.from(requireContext(), cover, CustomGlideRequest.ResourceType.Song)
                .build().into(bind.lyricsPillArt);
    }

    // Lyrics actions

    /** A faint white scrim with white icons, to sit on the dark lyrics background. */
    private void styleLyricsActionButtons() {
        if (bind == null) return;
        ColorStateList scrim = ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 40));
        ColorStateList white = ColorStateList.valueOf(Color.WHITE);
        for (MaterialButton b : new MaterialButton[]{bind.translateLyricsButton, bind.downloadLyricsButton, bind.lyricsSourceToggleButton}) {
            b.setBackgroundTintList(scrim);
            b.setIconTint(white);
            b.setTextColor(Color.WHITE);
        }
    }

    private void initOverlay() {
        bind.translateLyricsButton.setOnClickListener(view -> {
            if (isTranslating) return;

            if (translatedLines != null) {
                translatedLines = null;
                updatePanelContent();
                Toast.makeText(requireContext(), R.string.lyrics_translation_cleared, Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> lines = collectLyricsLines();
            if (lines == null || lines.isEmpty()) return;

            isTranslating = true;
            Toast.makeText(requireContext(), R.string.lyrics_translating, Toast.LENGTH_SHORT).show();

            LyricsTranslator.translate(lines, new LyricsTranslator.Callback() {
                @Override
                public void onSuccess(@NonNull List<String> result) {
                    isTranslating = false;
                    if (bind == null) return;
                    translatedLines = result;
                    updatePanelContent();
                }

                @Override
                public void onError(@NonNull String message) {
                    isTranslating = false;
                    if (bind == null || getContext() == null) return;
                    Toast.makeText(requireContext(), getString(R.string.lyrics_translation_error, message), Toast.LENGTH_LONG).show();
                }
            });
        });

        bind.downloadLyricsButton.setOnClickListener(view -> {
            boolean saved = playerBottomSheetViewModel.downloadCurrentLyrics();
            if (getContext() != null) {
                Toast.makeText(
                        requireContext(),
                        saved ? R.string.player_lyrics_download_success : R.string.player_lyrics_download_failure,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        bind.lyricsSourceToggleButton.setOnClickListener(view -> {
            int switchResult = playerBottomSheetViewModel.switchLyricsSource();
            if (switchResult == PlayerBottomSheetViewModel.SWITCH_RESULT_LOADING) {
                Toast.makeText(requireContext(), R.string.player_lyrics_source_loading, Toast.LENGTH_SHORT).show();
                return;
            }
            if (switchResult != PlayerBottomSheetViewModel.SWITCH_RESULT_SWITCHED) {
                Toast.makeText(requireContext(), R.string.player_lyrics_source_switch_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }

            int sourceAfter = playerBottomSheetViewModel.getActiveLyricsSource();
            int labelRes;
            if (sourceAfter == PlayerBottomSheetViewModel.LYRICS_SOURCE_LRCLIB) {
                labelRes = R.string.player_lyrics_source_lrclib_label;
            } else if (sourceAfter == PlayerBottomSheetViewModel.LYRICS_SOURCE_BETTERLYRICS) {
                labelRes = R.string.player_lyrics_source_betterlyrics_label;
            } else {
                labelRes = R.string.player_lyrics_source_server_label;
            }
            Toast.makeText(requireContext(), getString(R.string.player_lyrics_source_active, getString(labelRes)), Toast.LENGTH_SHORT).show();
        });
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
        mediaBrowser = null;
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                mediaBrowser = mediaBrowserListenableFuture.get();
                // The last line's length depends on the track's, which is known by now.
                if (adapter != null && adapter.getSynced()) updatePanelContent();
                scheduleFrame();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void initPanelContent() {
        playerBottomSheetViewModel.getLiveLyrics().observe(getViewLifecycleOwner(), lyrics -> {
            currentLyrics = lyrics;
            translatedLines = null;
            updatePanelContent();
        });

        playerBottomSheetViewModel.getLiveLyricsList().observe(getViewLifecycleOwner(), lyricsList -> {
            currentLyricsList = lyricsList;
            translatedLines = null;
            updatePanelContent();
        });

        playerBottomSheetViewModel.getLiveDescription().observe(getViewLifecycleOwner(), description -> {
            currentDescription = description;
            updatePanelContent();
        });
    }

    private void observeDownloadState() {
        playerBottomSheetViewModel.getLyricsCachedState().observe(getViewLifecycleOwner(), cached -> {
            if (bind == null) return;
            MaterialButton downloadButton = bind.downloadLyricsButton;
            if (cached != null && cached) {
                downloadButton.setIconResource(R.drawable.ic_done);
                downloadButton.setContentDescription(getString(R.string.player_lyrics_downloaded_content_description));
            } else {
                downloadButton.setIconResource(R.drawable.ic_download);
                downloadButton.setContentDescription(getString(R.string.player_lyrics_download_content_description));
            }
        });
    }

    private void observeLyricsSourceState() {
        playerBottomSheetViewModel.getLyricsSourceSwitchAvailable().observe(getViewLifecycleOwner(), canSwitch -> {
            lyricsSourceSwitchAvailable = canSwitch != null && canSwitch;
            updateLyricsSourceButtonVisibility(hasLyrics());
        });

        playerBottomSheetViewModel.getLyricsSource().observe(getViewLifecycleOwner(), source -> {
            if (bind == null) return;

            if (source != null && source == PlayerBottomSheetViewModel.LYRICS_SOURCE_LRCLIB) {
                bind.lyricsSourceToggleButton.setText(getString(R.string.player_lyrics_source_lrclib_label));
                bind.lyricsSourceToggleButton.setContentDescription(getString(R.string.player_lyrics_source_lrclib_content_description));
            } else if (source != null && source == PlayerBottomSheetViewModel.LYRICS_SOURCE_BETTERLYRICS) {
                bind.lyricsSourceToggleButton.setText(getString(R.string.player_lyrics_source_betterlyrics_label));
                bind.lyricsSourceToggleButton.setContentDescription(getString(R.string.player_lyrics_source_betterlyrics_content_description));
            } else {
                bind.lyricsSourceToggleButton.setText(getString(R.string.player_lyrics_source_server_label));
                bind.lyricsSourceToggleButton.setContentDescription(getString(R.string.player_lyrics_source_server_content_description));
            }
        });
    }

    // Content

    private void updatePanelContent() {
        if (bind == null || adapter == null) return;

        boolean romanize = Preferences.isLyricsRomanizationEnabled();
        List<LyricItem> items = Collections.emptyList();
        boolean synced = false;
        boolean compact = false;

        if (hasStructuredLyrics(currentLyricsList)) {
            StructuredLyrics structured = currentLyricsList.getStructuredLyrics().get(0);
            List<Line> lines = structured.getLine();
            LyricsTimeline.Secondary secondary = (i, text) -> secondaryText(lines.get(i).getBackground(), text, i, romanize);
            if (structured.getSynced()) {
                long duration = mediaBrowser != null ? mediaBrowser.getDuration() : C.TIME_UNSET;
                items = LyricsTimeline.build(lines, duration, secondary);
                synced = true;
            } else {
                List<String> texts = new ArrayList<>();
                for (Line line : lines) texts.add(line.getValue());
                items = LyricsTimeline.plain(texts, secondary);
            }
        } else if (hasText(currentLyrics)) {
            String readable = MusicUtil.getReadableLyrics(currentLyrics);
            items = LyricsTimeline.plain(Arrays.asList(readable.split("\n")),
                    (i, text) -> secondaryText(null, text, i, romanize));
        } else if (hasText(currentDescription)) {
            // A podcast's description, in place of lyrics: ordinary text, not lyric-sized.
            items = LyricsTimeline.plain(Arrays.asList(MusicUtil.getReadableLyrics(currentDescription).split("\n")),
                    (i, text) -> null);
            compact = true;
        }

        adapter.setCompact(compact);
        adapter.submit(items, synced);
        jumpToCurrent = true;
        layoutManager.scrollToPositionWithOffset(0, 0);
        if (mediaBrowser != null) onFrame(mediaBrowser.getCurrentPosition(), mediaBrowser.getDuration());

        boolean hasLyrics = hasLyrics();
        bind.lyricsEmptyLabel.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        bind.lyricsNotSyncedLabel.setVisibility(hasLyrics && !synced ? View.VISIBLE : View.GONE);
        bind.downloadLyricsButton.setVisibility(hasLyrics ? View.VISIBLE : View.GONE);
        bind.downloadLyricsButton.setEnabled(hasLyrics);

        boolean showTranslate = hasLyrics && !NetworkUtil.isServerUnreachable()
                && Preferences.getTranslationApiKey() != null
                && !Preferences.getTranslationApiKey().isEmpty();
        bind.translateLyricsButton.setVisibility(showTranslate ? View.VISIBLE : View.GONE);
        bind.translateLyricsButton.setIconResource(translatedLines != null ? R.drawable.ic_close : R.drawable.ic_translate);

        updateLyricsSourceButtonVisibility(hasLyrics);
    }

    /** Background vocals, romanization and translation for one line, a row each. */
    @Nullable
    private String secondaryText(@Nullable String background, String text, int index, boolean romanize) {
        StringBuilder sb = new StringBuilder();
        if (background != null && !background.isEmpty()) sb.append(background);
        if (romanize) {
            String rom = LyricsRomanizer.romanize(text);
            if (rom != null && !rom.equals(text)) append(sb, rom);
        }
        if (translatedLines != null && index < translatedLines.size()) {
            String translation = translatedLines.get(index);
            if (translation != null && !translation.trim().isEmpty()) append(sb, translation.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void append(StringBuilder sb, String row) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(row);
    }

    private void updateLyricsSourceButtonVisibility(boolean hasLyrics) {
        if (bind == null) return;
        bind.lyricsSourceToggleButton.setVisibility(hasLyrics ? View.VISIBLE : View.GONE);
        bind.lyricsSourceToggleButton.setEnabled(hasLyrics);
        bind.lyricsSourceToggleButton.setAlpha(lyricsSourceSwitchAvailable ? 1f : 0.6f);
    }

    private boolean hasLyrics() {
        return hasStructuredLyrics(currentLyricsList) || hasText(currentLyrics);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean hasStructuredLyrics(LyricsList lyricsList) {
        return lyricsList != null
                && lyricsList.getStructuredLyrics() != null
                && !lyricsList.getStructuredLyrics().isEmpty()
                && lyricsList.getStructuredLyrics().get(0) != null
                && lyricsList.getStructuredLyrics().get(0).getLine() != null
                && !lyricsList.getStructuredLyrics().get(0).getLine().isEmpty();
    }

    private List<String> collectLyricsLines() {
        if (hasStructuredLyrics(currentLyricsList)) {
            List<String> result = new ArrayList<>();
            for (Line line : currentLyricsList.getStructuredLyrics().get(0).getLine()) {
                result.add(line.getValue().trim());
            }
            return result;
        } else if (hasText(currentLyrics)) {
            String readable = MusicUtil.getReadableLyrics(currentLyrics);
            if (readable == null) return null;
            List<String> result = new ArrayList<>();
            for (String s : readable.split("\n")) result.add(s.trim());
            return result;
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
