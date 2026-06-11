package com.cappielloantonio.tempo.repository;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Line;
import com.cappielloantonio.tempo.subsonic.models.LyricsList;
import com.cappielloantonio.tempo.subsonic.models.StructuredLyrics;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LrcGetRepository {
    private static final String API_BASE_URL = "https://lrclib.net/api/get";
    private static final String API_SEARCH_URL = "https://lrclib.net/api/search";
    private static final int DURATION_TOLERANCE_SECONDS = 5;
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
            .build();
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class LrcGetLyricsResult {
        private final String plainLyrics;
        private final LyricsList syncedLyrics;

        public LrcGetLyricsResult(String plainLyrics, LyricsList syncedLyrics) {
            this.plainLyrics = plainLyrics;
            this.syncedLyrics = syncedLyrics;
        }

        public String getPlainLyrics() {
            return plainLyrics;
        }

        public LyricsList getSyncedLyrics() {
            return syncedLyrics;
        }
    }

    public MutableLiveData<LrcGetLyricsResult> getLyrics(@NonNull Child media) {
        MutableLiveData<LrcGetLyricsResult> result = new MutableLiveData<>(null);

        if (TextUtils.isEmpty(media.getArtist()) || TextUtils.isEmpty(media.getTitle())) {
            return result;
        }

        executor.execute(() -> {
            LrcGetLyricsResult exact = fetchExact(media);
            if (exact != null) {
                result.postValue(exact);
                return;
            }

            LrcGetLyricsResult searched = fetchViaSearch(media);
            if (searched != null) {
                result.postValue(searched);
            }
        });

        return result;
    }

    private LrcGetLyricsResult fetchExact(@NonNull Child media) {
        HttpUrl baseUrl = HttpUrl.parse(API_BASE_URL);
        if (baseUrl == null) {
            return null;
        }

        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("artist_name", media.getArtist())
                .addQueryParameter("track_name", media.getTitle());

        if (media.getDuration() != null && media.getDuration() > 0) {
            urlBuilder.addQueryParameter("duration", String.valueOf(media.getDuration()));
        }

        try (Response response = execute(urlBuilder.build())) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            JsonObject jsonObject = gson.fromJson(response.body().string(), JsonObject.class);
            if (jsonObject == null || jsonObject.isJsonNull()) {
                return null;
            }

            return parseResult(jsonObject);
        } catch (IOException ignored) {
            return null;
        }
    }

    private LrcGetLyricsResult fetchViaSearch(@NonNull Child media) {
        HttpUrl baseUrl = HttpUrl.parse(API_SEARCH_URL);
        if (baseUrl == null) {
            return null;
        }

        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("artist_name", media.getArtist())
                .addQueryParameter("track_name", media.getTitle());

        try (Response response = execute(urlBuilder.build())) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            JsonArray jsonArray = gson.fromJson(response.body().string(), JsonArray.class);
            if (jsonArray == null || jsonArray.isJsonNull() || jsonArray.size() == 0) {
                return null;
            }

            JsonObject best = selectBestMatch(jsonArray, media);
            return best != null ? parseResult(best) : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private JsonObject selectBestMatch(JsonArray candidates, @NonNull Child media) {
        Integer targetDuration = media.getDuration() != null && media.getDuration() > 0 ? media.getDuration() : null;

        JsonObject bestSynced = null;
        int bestSyncedDiff = Integer.MAX_VALUE;
        JsonObject bestPlain = null;
        int bestPlainDiff = Integer.MAX_VALUE;

        for (JsonElement element : candidates) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }

            JsonObject candidate = element.getAsJsonObject();
            boolean hasSynced = !TextUtils.isEmpty(getString(candidate, "syncedLyrics"));
            boolean hasPlain = !TextUtils.isEmpty(getString(candidate, "plainLyrics"));
            if (!hasSynced && !hasPlain) {
                continue;
            }

            int diff = durationDiff(candidate, targetDuration);

            if (hasSynced && diff < bestSyncedDiff) {
                bestSynced = candidate;
                bestSyncedDiff = diff;
            }
            if (hasPlain && diff < bestPlainDiff) {
                bestPlain = candidate;
                bestPlainDiff = diff;
            }
        }

        if (bestSynced != null && bestSyncedDiff <= durationTolerance(targetDuration)) {
            return bestSynced;
        }
        if (bestPlain != null && bestPlainDiff <= durationTolerance(targetDuration)) {
            return bestPlain;
        }
        return bestSynced != null ? bestSynced : bestPlain;
    }

    private int durationDiff(JsonObject candidate, Integer targetDuration) {
        if (targetDuration == null || !candidate.has("duration") || candidate.get("duration").isJsonNull()) {
            return 0;
        }

        try {
            return Math.abs(candidate.get("duration").getAsInt() - targetDuration);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private int durationTolerance(Integer targetDuration) {
        return targetDuration == null ? Integer.MAX_VALUE : DURATION_TOLERANCE_SECONDS;
    }

    private LrcGetLyricsResult parseResult(JsonObject jsonObject) {
        String plainLyrics = getString(jsonObject, "plainLyrics");
        String syncedLyricsText = getString(jsonObject, "syncedLyrics");
        String artist = getString(jsonObject, "artistName");
        String title = getString(jsonObject, "trackName");
        String lang = getString(jsonObject, "lang");

        LyricsList syncedLyrics = parseSyncedLyrics(syncedLyricsText, artist, title, lang);

        if (TextUtils.isEmpty(plainLyrics) && syncedLyrics == null) {
            return null;
        }

        return new LrcGetLyricsResult(plainLyrics, syncedLyrics);
    }

    private Response execute(HttpUrl url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Rollynn")
                .build();

        return client.newCall(request).execute();
    }

    private LyricsList parseSyncedLyrics(String lrcText, String artist, String title, String lang) {
        if (TextUtils.isEmpty(lrcText)) {
            return null;
        }

        List<Line> parsedLines = new ArrayList<>();
        String[] rawLines = lrcText.split("\\r?\\n");

        for (String rawLine : rawLines) {
            if (TextUtils.isEmpty(rawLine)) {
                continue;
            }

            Matcher matcher = TIMESTAMP_PATTERN.matcher(rawLine);
            List<Integer> starts = new ArrayList<>();
            int timestampEndIndex = -1;

            while (matcher.find()) {
                int minutes = parsePart(matcher.group(1));
                int seconds = parsePart(matcher.group(2));
                int millis = parseMillis(matcher.group(3));
                starts.add((minutes * 60 * 1000) + (seconds * 1000) + millis);
                timestampEndIndex = matcher.end();
            }

            if (starts.isEmpty()) {
                continue;
            }

            String value = rawLine.substring(Math.max(timestampEndIndex, 0)).trim();
            if (value.isEmpty()) {
                continue;
            }

            for (Integer start : starts) {
                Line line = new Line();
                line.setStart(start);
                line.setValue(value);
                parsedLines.add(line);
            }
        }

        if (parsedLines.isEmpty()) {
            return null;
        }

        parsedLines.sort(Comparator.comparing(Line::getStart, Comparator.nullsLast(Integer::compareTo)));

        StructuredLyrics structuredLyrics = new StructuredLyrics();
        structuredLyrics.setDisplayArtist(artist);
        structuredLyrics.setDisplayTitle(title);
        structuredLyrics.setLang(lang);
        structuredLyrics.setOffset(0);
        structuredLyrics.setSynced(true);
        structuredLyrics.setLine(parsedLines);

        LyricsList lyricsList = new LyricsList();
        lyricsList.setStructuredLyrics(Collections.singletonList(structuredLyrics));
        return lyricsList;
    }

    private int parsePart(String value) {
        try {
            return Integer.parseInt(Objects.requireNonNullElse(value, "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int parseMillis(String fraction) {
        if (TextUtils.isEmpty(fraction)) {
            return 0;
        }

        String normalized = fraction.trim();
        if (normalized.length() == 1) {
            return parsePart(normalized) * 100;
        }
        if (normalized.length() == 2) {
            return parsePart(normalized) * 10;
        }

        return parsePart(normalized.substring(0, Math.min(3, normalized.length())));
    }

    private String getString(JsonObject jsonObject, String key) {
        if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return null;
        }

        String value = jsonObject.get(key).getAsString();
        return TextUtils.isEmpty(value) ? null : value;
    }
}
