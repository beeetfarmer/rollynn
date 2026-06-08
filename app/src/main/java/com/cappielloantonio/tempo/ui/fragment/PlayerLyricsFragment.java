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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import com.cappielloantonio.tempo.ui.span.WipeSpan;

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
import com.cappielloantonio.tempo.subsonic.models.Word;
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
import java.util.concurrent.atomic.AtomicLong;


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
    private Integer lastWordIdx;
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
        lastWordIdx = null;
        lyricsSourceSwitchAvailable = false;
        translatedLines = null;
        isTranslating = false;
        cachedLineIdx = Integer.MIN_VALUE;
        hasWipeSpans = false;
        progressHandlerObserverRegistered = false;
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
            boolean switched = playerBottomSheetViewModel.switchLyricsSource();
            if (!switched) {
                Toast.makeText(requireContext(), R.string.player_lyrics_source_switch_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }

            Integer sourceAfter = playerBottomSheetViewModel.getLyricsSource().getValue();
            int labelRes;
            if (sourceAfter != null && sourceAfter == PlayerBottomSheetViewModel.LYRICS_SOURCE_LRCLIB) {
                labelRes = R.string.player_lyrics_source_lrclib_label;
            } else if (sourceAfter != null && sourceAfter == PlayerBottomSheetViewModel.LYRICS_SOURCE_BETTERLYRICS) {
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
                startSyncLoop();
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
            lastWordIdx = null;
            translatedLines = null;
            cachedLineIdx = Integer.MIN_VALUE;
            hasWipeSpans = false;
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
            } else if (source != null && source == PlayerBottomSheetViewModel.LYRICS_SOURCE_BETTERLYRICS) {
                bind.lyricsSourceToggleButton.setText(getString(R.string.player_lyrics_source_betterlyrics_label));
                bind.lyricsSourceToggleButton.setContentDescription(getString(R.string.player_lyrics_source_betterlyrics_content_description));
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
        if (progressHandlerObserverRegistered) return;
        progressHandlerObserverRegistered = true;

        playerBottomSheetViewModel.getLiveLyricsList().observe(getViewLifecycleOwner(), lyricsList -> startSyncLoop());
    }

    private void startSyncLoop() {
        releaseHandler();

        if (bind == null || mediaBrowser == null) return;

        LyricsList lyricsList = playerBottomSheetViewModel.getLiveLyricsList().getValue();

        if (!hasStructuredLyrics(lyricsList)) return;
        if (!lyricsList.getStructuredLyrics().get(0).getSynced()) return;

        boolean hasWordSync = hasWordSyncData(lyricsList);
        int interval = hasWordSync ? 16 : 250;

        syncLyricsHandler = new Handler();
        syncLyricsRunnable = () -> {
            if (syncLyricsHandler != null) {
                if (bind != null) {
                    displaySyncedLyrics();
                }

                syncLyricsHandler.postDelayed(syncLyricsRunnable, interval);
            }
        };

        syncLyricsHandler.post(syncLyricsRunnable);
    }

    private boolean hasWordSyncData(LyricsList lyricsList) {
        if (!hasStructuredLyrics(lyricsList)) return false;
        List<Line> lines = lyricsList.getStructuredLyrics().get(0).getLine();
        if (lines == null) return false;
        for (Line line : lines) {
            if (line.getWords() != null && !line.getWords().isEmpty()) return true;
        }
        return false;
    }

    private int cachedLineIdx = Integer.MIN_VALUE;
    private boolean hasWipeSpans = false;
    private boolean progressHandlerObserverRegistered = false;
    private final AtomicLong wipeTimestamp = new AtomicLong(0);

    @SuppressLint("ClickableViewAccessibility")
    private void displaySyncedLyrics() {
        LyricsList lyricsList = playerBottomSheetViewModel.getLiveLyricsList().getValue();
        long timestamp = mediaBrowser.getCurrentPosition();

        if (!hasStructuredLyrics(lyricsList)) return;

        List<Line> lines = lyricsList.getStructuredLyrics().get(0).getLine();
        if (lines == null || lines.isEmpty()) return;

        int curIdx = 0;
        for (; curIdx < lines.size(); ++curIdx) {
            Integer start = lines.get(curIdx).getStart();
            if (start != null && start > timestamp) {
                curIdx--;
                break;
            }
        }

        wipeTimestamp.set(timestamp);

        if (cachedLineIdx == curIdx) {
            if (hasWipeSpans) bind.nowPlayingSongLyricsTextView.invalidate();
            return;
        }

        cachedLineIdx = curIdx;

        int highlightColor = requireContext().getResources().getColor(R.color.lyricsTextColor, null);
        int shadowColor = requireContext().getResources().getColor(R.color.shadowsLyricsTextColor, null);
        boolean romanize = Preferences.isLyricsRomanizationEnabled();
        int secondarySize = (int) (bind.nowPlayingSongLyricsTextView.getTextSize() * 0.75f);

        SpannableStringBuilder builder = new SpannableStringBuilder();
        hasWipeSpans = false;

        int highlightStart = -1;
        for (int i = 0; i < lines.size(); ++i) {
            boolean isCurrent = i == curIdx;
            if (isCurrent) highlightStart = builder.length();

            List<Word> words = lines.get(i).getWords();
            String lineText;

            if (isCurrent && words != null && !words.isEmpty()) {
                hasWipeSpans = true;
                for (int w = 0; w < words.size(); w++) {
                    Word word = words.get(w);
                    int wordStart = builder.length();
                    builder.append(word.getText());
                    int wordEnd = builder.length();

                    builder.setSpan(
                            new WipeSpan(highlightColor, shadowColor, word.getStart(), word.getEnd(), wipeTimestamp, word.getStart()),
                            wordStart, wordEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    if (w < words.size() - 1) {
                        String text = word.getText();
                        if (!text.endsWith(" ") && !text.endsWith("-")) {
                            builder.append(" ");
                        }
                    }
                }
                lineText = lines.get(i).getValue().trim();
            } else {
                lineText = lines.get(i).getValue().trim();
                int lineTextStart = builder.length();
                builder.append(lineText);
                int lineTextEnd = builder.length();

                final int lineStart = lines.get(i).getStart();
                final int lineColor = isCurrent ? highlightColor : shadowColor;
                builder.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View view) {
                        mediaBrowser.seekTo(lineStart + 1);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(false);
                        ds.setColor(lineColor);
                    }
                }, lineTextStart, lineTextEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (romanize) {
                String rom = LyricsRomanizer.romanize(lineText);
                if (rom != null && !rom.equals(lineText)) {
                    builder.append("\n");
                    int romStart = builder.length();
                    builder.append(rom);
                    int romEnd = builder.length();
                    builder.setSpan(new AbsoluteSizeSpan(secondarySize), romStart, romEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.setSpan(new ForegroundColorSpan(isCurrent ? highlightColor : shadowColor), romStart, romEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            if (translatedLines != null && i < translatedLines.size() && !translatedLines.get(i).isEmpty()) {
                builder.append("\n");
                int transStart = builder.length();
                builder.append(translatedLines.get(i));
                int transEnd = builder.length();
                builder.setSpan(new AbsoluteSizeSpan(secondarySize), transStart, transEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(isCurrent ? highlightColor : shadowColor), transStart, transEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            builder.append("\n\n");
        }

        bind.nowPlayingSongLyricsTextView.setMovementMethod(LinkMovementMethod.getInstance());
        bind.nowPlayingSongLyricsTextView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Layout layout = bind.nowPlayingSongLyricsTextView.getLayout();
                if (layout != null) {
                    int x = (int) event.getX() - bind.nowPlayingSongLyricsTextView.getTotalPaddingLeft();
                    int y = (int) event.getY() - bind.nowPlayingSongLyricsTextView.getTotalPaddingTop();
                    int line = layout.getLineForVertical(y);
                    int offset = layout.getOffsetForHorizontal(line, x);
                    Spannable spannable = (Spannable) bind.nowPlayingSongLyricsTextView.getText();
                    WipeSpan[] spans = spannable.getSpans(offset, offset, WipeSpan.class);
                    if (spans.length > 0) {
                        mediaBrowser.seekTo(spans[0].getSeekTarget() + 1);
                        return true;
                    }
                }
            }
            return false;
        });
        bind.nowPlayingSongLyricsTextView.setText(builder);

        if (highlightStart >= 0 && playerBottomSheetViewModel.getSyncLyricsState()) {
            final int scrollTarget = highlightStart;
            bind.nowPlayingSongLyricsTextView.post(() -> {
                if (bind != null) {
                    bind.nowPlayingSongLyricsSrollView.smoothScrollTo(0, getScroll(scrollTarget));
                }
            });
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
