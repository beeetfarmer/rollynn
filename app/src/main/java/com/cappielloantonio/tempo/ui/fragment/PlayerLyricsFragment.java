package com.cappielloantonio.tempo.ui.fragment;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerLyricsBinding;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Line;
import com.cappielloantonio.tempo.subsonic.models.LyricsList;
import com.cappielloantonio.tempo.util.LyricsRomanizer;
import com.cappielloantonio.tempo.util.LyricsTranslator;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;


@OptIn(markerClass = UnstableApi.class)
public class PlayerLyricsFragment extends Fragment {
    private static final String TAG = "PlayerLyricsFragment";

    private InnerFragmentPlayerLyricsBinding bind;
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private MediaBrowser mediaBrowser;
    private Handler syncLyricsHandler;
    private Runnable syncLyricsRunnable;
    private String currentLyrics;
    private LyricsList currentLyricsList;
    private Integer lastLineIdx;
    private String currentDescription;
    private boolean lyricsSourceSwitchAvailable;
    private List<String> translatedLines;
    private boolean isTranslating;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = InnerFragmentPlayerLyricsBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        initOverlay();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initPanelContent();
        observeDownloadState();
        observeLyricsSourceState();
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
        releaseHandler();
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
        bind = null;
        currentLyrics = null;
        currentLyricsList = null;
        currentDescription = null;
        lastLineIdx = null;
        lyricsSourceSwitchAvailable = false;
        translatedLines = null;
        isTranslating = false;
    }

    private void initOverlay() {
        float overlayLift = getResources().getDisplayMetrics().density * 64f;
        bind.lyricsSourceToggleButton.setTranslationY(-overlayLift);
        bind.downloadLyricsButton.setTranslationY(-overlayLift);
        bind.translateLyricsButton.setTranslationY(-overlayLift);

        bind.translateLyricsButton.setOnClickListener(view -> {
            if (isTranslating) return;

            if (translatedLines != null) {
                translatedLines = null;
                lastLineIdx = null;
                updatePanelContent();
                Toast.makeText(requireContext(), R.string.lyrics_translation_cleared, Toast.LENGTH_SHORT).show();
                updateTranslateButtonIcon();
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
                    lastLineIdx = null;
                    updatePanelContent();
                    updateTranslateButtonIcon();
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
            Integer sourceBefore = playerBottomSheetViewModel.getLyricsSource().getValue();
            boolean switched = playerBottomSheetViewModel.switchLyricsSource();
            if (!switched) {
                Toast.makeText(requireContext(), R.string.player_lyrics_source_switch_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }

            int labelRes = sourceBefore != null && sourceBefore == PlayerBottomSheetViewModel.LYRICS_SOURCE_SERVER
                    ? R.string.player_lyrics_source_lrclib_label
                    : R.string.player_lyrics_source_server_label;
            Toast.makeText(requireContext(), getString(R.string.player_lyrics_source_active, getString(labelRes)), Toast.LENGTH_SHORT).show();
        });
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseHandler() {
        if (syncLyricsHandler != null) {
            syncLyricsHandler.removeCallbacks(syncLyricsRunnable);
            syncLyricsHandler = null;
        }
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                mediaBrowser = mediaBrowserListenableFuture.get();
                defineProgressHandler();
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
            lastLineIdx = null;
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
            if (bind != null) {
                MaterialButton downloadButton = (MaterialButton) bind.downloadLyricsButton;
                if (cached != null && cached) {
                    downloadButton.setIconResource(R.drawable.ic_done);
                    downloadButton.setContentDescription(getString(R.string.player_lyrics_downloaded_content_description));
                } else {
                    downloadButton.setIconResource(R.drawable.ic_download);
                    downloadButton.setContentDescription(getString(R.string.player_lyrics_download_content_description));
                }
            }
        });
    }

    private void observeLyricsSourceState() {
        playerBottomSheetViewModel.getLyricsSourceSwitchAvailable().observe(getViewLifecycleOwner(), canSwitch -> {
            lyricsSourceSwitchAvailable = canSwitch != null && canSwitch;
            updateLyricsSourceButtonVisibility(hasStructuredLyrics(currentLyricsList) || hasText(currentLyrics));
        });

        playerBottomSheetViewModel.getLyricsSource().observe(getViewLifecycleOwner(), source -> {
            if (bind == null) {
                return;
            }

            if (source != null && source == PlayerBottomSheetViewModel.LYRICS_SOURCE_LRCLIB) {
                bind.lyricsSourceToggleButton.setText(getString(R.string.player_lyrics_source_lrclib_label));
                bind.lyricsSourceToggleButton.setContentDescription(getString(R.string.player_lyrics_source_lrclib_content_description));
            } else {
                bind.lyricsSourceToggleButton.setText(getString(R.string.player_lyrics_source_server_label));
                bind.lyricsSourceToggleButton.setContentDescription(getString(R.string.player_lyrics_source_server_content_description));
            }
        });
    }

    private void updatePanelContent() {
        if (bind == null) {
            return;
        }

        bind.nowPlayingSongLyricsSrollView.smoothScrollTo(0, 0);

        boolean hasLyrics = hasStructuredLyrics(currentLyricsList) || hasText(currentLyrics);
        boolean showTranslate = hasLyrics && !NetworkUtil.isServerUnreachable()
                && Preferences.getTranslationApiKey() != null
                && !Preferences.getTranslationApiKey().isEmpty();

        if (hasStructuredLyrics(currentLyricsList)) {
            setSyncLyrics(currentLyricsList);
            bind.nowPlayingSongLyricsTextView.setVisibility(View.VISIBLE);
            bind.emptyDescriptionImageView.setVisibility(View.GONE);
            bind.titleEmptyDescriptionLabel.setVisibility(View.GONE);
            bind.downloadLyricsButton.setVisibility(View.VISIBLE);
            bind.downloadLyricsButton.setEnabled(true);
        } else if (hasText(currentLyrics)) {
            bind.nowPlayingSongLyricsTextView.setText(buildPlainLyricsDisplay(MusicUtil.getReadableLyrics(currentLyrics)));
            bind.nowPlayingSongLyricsTextView.setVisibility(View.VISIBLE);
            bind.emptyDescriptionImageView.setVisibility(View.GONE);
            bind.titleEmptyDescriptionLabel.setVisibility(View.GONE);
            bind.downloadLyricsButton.setVisibility(View.VISIBLE);
            bind.downloadLyricsButton.setEnabled(true);
        } else if (hasText(currentDescription)) {
            bind.nowPlayingSongLyricsTextView.setText(MusicUtil.getReadableLyrics(currentDescription));
            bind.nowPlayingSongLyricsTextView.setVisibility(View.VISIBLE);
            bind.emptyDescriptionImageView.setVisibility(View.GONE);
            bind.titleEmptyDescriptionLabel.setVisibility(View.GONE);
            bind.downloadLyricsButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setEnabled(false);
        } else {
            bind.nowPlayingSongLyricsTextView.setVisibility(View.GONE);
            bind.emptyDescriptionImageView.setVisibility(View.VISIBLE);
            bind.titleEmptyDescriptionLabel.setVisibility(View.VISIBLE);
            bind.downloadLyricsButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setEnabled(false);
        }

        bind.translateLyricsButton.setVisibility(showTranslate ? View.VISIBLE : View.GONE);
        updateTranslateButtonIcon();

        updateLyricsSourceButtonVisibility(hasStructuredLyrics(currentLyricsList) || hasText(currentLyrics));
    }

    private void updateLyricsSourceButtonVisibility(boolean hasLyrics) {
        if (bind == null) {
            return;
        }

        bind.lyricsSourceToggleButton.setVisibility(hasLyrics ? View.VISIBLE : View.GONE);
        bind.lyricsSourceToggleButton.setEnabled(hasLyrics);
        bind.lyricsSourceToggleButton.setAlpha(lyricsSourceSwitchAvailable ? 0.85f : 0.65f);
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

    @SuppressLint("DefaultLocale")
    private void setSyncLyrics(LyricsList lyricsList) {
        if (lyricsList.getStructuredLyrics() != null && !lyricsList.getStructuredLyrics().isEmpty() && lyricsList.getStructuredLyrics().get(0).getLine() != null) {
            List<Line> lines = lyricsList.getStructuredLyrics().get(0).getLine();
            boolean romanize = Preferences.isLyricsRomanizationEnabled();

            if (lines != null) {
                SpannableStringBuilder builder = new SpannableStringBuilder();
                int secondarySize = (int) (bind.nowPlayingSongLyricsTextView.getTextSize() * 0.75f);
                int secondaryColor = requireContext().getResources().getColor(R.color.shadowsLyricsTextColor, null);

                for (int i = 0; i < lines.size(); i++) {
                    String text = lines.get(i).getValue().trim();
                    builder.append(text);

                    if (romanize) {
                        String rom = LyricsRomanizer.romanize(text);
                        if (rom != null && !rom.equals(text)) {
                            builder.append("\n");
                            int romStart = builder.length();
                            builder.append(rom);
                            int romEnd = builder.length();
                            builder.setSpan(new AbsoluteSizeSpan(secondarySize), romStart, romEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            builder.setSpan(new ForegroundColorSpan(secondaryColor), romStart, romEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }

                    if (translatedLines != null && i < translatedLines.size() && !translatedLines.get(i).isEmpty()) {
                        builder.append("\n");
                        int transStart = builder.length();
                        builder.append(translatedLines.get(i));
                        int transEnd = builder.length();
                        builder.setSpan(new AbsoluteSizeSpan(secondarySize), transStart, transEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        builder.setSpan(new ForegroundColorSpan(secondaryColor), transStart, transEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    builder.append("\n\n");
                }

                bind.nowPlayingSongLyricsTextView.setText(builder);
            }
        }
    }

    private void defineProgressHandler() {
        playerBottomSheetViewModel.getLiveLyricsList().observe(getViewLifecycleOwner(), lyricsList -> {
            if (!hasStructuredLyrics(lyricsList)) {
                releaseHandler();
                return;
            }

            if (!lyricsList.getStructuredLyrics().get(0).getSynced()) {
                releaseHandler();
                return;
            }

            syncLyricsHandler = new Handler();
            syncLyricsRunnable = () -> {
                if (syncLyricsHandler != null) {
                    if (bind != null) {
                        displaySyncedLyrics();
                    }

                    syncLyricsHandler.postDelayed(syncLyricsRunnable, 250);
                }
            };

            syncLyricsHandler.postDelayed(syncLyricsRunnable, 250);
        });
    }

    private void displaySyncedLyrics() {
        LyricsList lyricsList = playerBottomSheetViewModel.getLiveLyricsList().getValue();
        int timestamp = (int) (mediaBrowser.getCurrentPosition());

        if (hasStructuredLyrics(lyricsList)) {
            List<Line> lines = lyricsList.getStructuredLyrics().get(0).getLine();
            if (lines == null || lines.isEmpty()) {
                return;
            }

            int curIdx = 0;
            for (; curIdx < lines.size(); ++curIdx) {
                Integer start = lines.get(curIdx).getStart();
                if (start != null && start > timestamp) {
                    curIdx--;
                    break;
                }
            }

            if (lastLineIdx != null && curIdx == lastLineIdx) {
                return;
            }
            lastLineIdx = curIdx;

            boolean romanize = Preferences.isLyricsRomanizationEnabled();
            int secondarySize = (int) (bind.nowPlayingSongLyricsTextView.getTextSize() * 0.75f);
            SpannableStringBuilder builder = new SpannableStringBuilder();

            int highlightStart = -1;
            for (int i = 0; i < lines.size(); ++i) {
                String text = lines.get(i).getValue().trim();
                boolean highlight = i == curIdx;
                if (highlight) highlightStart = builder.length();

                int lineTextStart = builder.length();
                builder.append(text);
                int lineTextEnd = builder.length();

                final int lineStart = lines.get(i).getStart();
                final boolean isHighlight = highlight;
                builder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View view) {
                        mediaBrowser.seekTo(lineStart + 1);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(false);
                        if (isHighlight) {
                            ds.setColor(requireContext().getResources().getColor(R.color.lyricsTextColor, null));
                        } else {
                            ds.setColor(requireContext().getResources().getColor(R.color.shadowsLyricsTextColor, null));
                        }
                    }
                }, lineTextStart, lineTextEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                if (romanize) {
                    String rom = LyricsRomanizer.romanize(text);
                    if (rom != null && !rom.equals(text)) {
                        builder.append("\n");
                        int romStart = builder.length();
                        builder.append(rom);
                        int romEnd = builder.length();
                        builder.setSpan(new AbsoluteSizeSpan(secondarySize), romStart, romEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        int romColor = isHighlight
                                ? requireContext().getResources().getColor(R.color.lyricsTextColor, null)
                                : requireContext().getResources().getColor(R.color.shadowsLyricsTextColor, null);
                        builder.setSpan(new ForegroundColorSpan(romColor), romStart, romEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }

                if (translatedLines != null && i < translatedLines.size() && !translatedLines.get(i).isEmpty()) {
                    builder.append("\n");
                    int transStart = builder.length();
                    builder.append(translatedLines.get(i));
                    int transEnd = builder.length();
                    builder.setSpan(new AbsoluteSizeSpan(secondarySize), transStart, transEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int transColor = isHighlight
                            ? requireContext().getResources().getColor(R.color.lyricsTextColor, null)
                            : requireContext().getResources().getColor(R.color.shadowsLyricsTextColor, null);
                    builder.setSpan(new ForegroundColorSpan(transColor), transStart, transEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                builder.append("\n\n");
            }

            bind.nowPlayingSongLyricsTextView.setMovementMethod(LinkMovementMethod.getInstance());
            bind.nowPlayingSongLyricsTextView.setText(builder);

            if (highlightStart >= 0 && playerBottomSheetViewModel.getSyncLyricsState()) {
                bind.nowPlayingSongLyricsSrollView.smoothScrollTo(0, getScroll(highlightStart));
            }
        }
    }

    private CharSequence buildPlainLyricsDisplay(String lyrics) {
        if (lyrics == null) return lyrics;

        boolean romanize = Preferences.isLyricsRomanizationEnabled();
        boolean hasTranslation = translatedLines != null;

        String[] lines = lyrics.split("\n");

        boolean anyRomanizable = false;
        if (romanize) {
            for (String line : lines) {
                if (LyricsRomanizer.needsRomanization(line.trim())) {
                    anyRomanizable = true;
                    break;
                }
            }
        }

        if (!anyRomanizable && !hasTranslation) return lyrics;

        SpannableStringBuilder builder = new SpannableStringBuilder();
        int secondarySize = (int) (bind.nowPlayingSongLyricsTextView.getTextSize() * 0.75f);
        int secondaryColor = requireContext().getResources().getColor(R.color.shadowsLyricsTextColor, null);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            builder.append(line);

            if (romanize && !line.isEmpty()) {
                String rom = LyricsRomanizer.romanize(line);
                if (rom != null && !rom.equals(line)) {
                    builder.append("\n");
                    int romStart = builder.length();
                    builder.append(rom);
                    int romEnd = builder.length();
                    builder.setSpan(new AbsoluteSizeSpan(secondarySize), romStart, romEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.setSpan(new ForegroundColorSpan(secondaryColor), romStart, romEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            if (hasTranslation && i < translatedLines.size() && !translatedLines.get(i).isEmpty()) {
                builder.append("\n");
                int transStart = builder.length();
                builder.append(translatedLines.get(i));
                int transEnd = builder.length();
                builder.setSpan(new AbsoluteSizeSpan(secondarySize), transStart, transEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(secondaryColor), transStart, transEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (i < lines.length - 1) {
                builder.append("\n");
            }
        }
        return builder;
    }

    private List<String> collectLyricsLines() {
        if (hasStructuredLyrics(currentLyricsList)) {
            List<Line> lines = currentLyricsList.getStructuredLyrics().get(0).getLine();
            List<String> result = new ArrayList<>();
            for (Line line : lines) {
                result.add(line.getValue().trim());
            }
            return result;
        } else if (hasText(currentLyrics)) {
            String readable = MusicUtil.getReadableLyrics(currentLyrics);
            if (readable == null) return null;
            String[] split = readable.split("\n");
            List<String> result = new ArrayList<>();
            for (String s : split) {
                result.add(s.trim());
            }
            return result;
        }
        return null;
    }

    private void updateTranslateButtonIcon() {
        if (bind == null) return;
        MaterialButton btn = (MaterialButton) bind.translateLyricsButton;
        if (translatedLines != null) {
            btn.setIconResource(R.drawable.ic_close);
        } else {
            btn.setIconResource(R.drawable.ic_translate);
        }
    }

    private int getScroll(int startIndex) {
        Layout layout = bind.nowPlayingSongLyricsTextView.getLayout();
        if (layout == null) return 0;

        int line = layout.getLineForOffset(startIndex);
        int lineTop = layout.getLineTop(line);
        int lineBottom = layout.getLineBottom(line);
        int lineCenter = (lineTop + lineBottom) / 2;

        int scrollViewHeight = bind.nowPlayingSongLyricsSrollView.getHeight();
        int scroll = lineCenter - scrollViewHeight / 2;

        return Math.max(scroll, 0);
    }
}
