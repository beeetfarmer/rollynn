package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.Nullable;

import com.cappielloantonio.tempo.BuildConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds the log report people attach to bug reports. These get posted publicly,
 * and Subsonic puts the login in every request URL, so everything is redacted
 * before it leaves {@link #collect()}.
 */
public final class LogExportUtil {
    private static final String TAG = "LogExportUtil";
    private static final String REDACTED = "<redacted>";

    private static final String MAX_LINES = "10000";
    // Each char takes two bytes through Binder, which rejects transactions near 1 MB.
    static final int CLIPBOARD_LIMIT = 128 * 1024;
    // A shorter value would match inside ordinary words and garble the log.
    private static final int MIN_SECRET_LENGTH = 4;

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b([a-z][a-z0-9+.-]*://)[^\\s/?#\"'<>]+([^\\s?#\"'<>]*)[^\\s\"'<>]*");
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)([?&](?:u|p|t|s|sk|api_key|api_sig)=)[^\\s&\"']+");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)\\b(password|passwd|token|salt|secret|api[_-]?key|session[_-]?key)"
                    + "([\"']?\\s*[=:]\\s*[\"']?)[^\\s&\"',;}]+");
    private static final Pattern AUTH_HEADER = Pattern.compile(
            "(?i)\\b((?:x-nd-)?authorization\\s*[:=]\\s*)[^\\r\\n]+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(bearer\\s+)\\S+");

    private LogExportUtil() {
    }

    /** The redacted report, or null when the system's log can't be read. */
    @Nullable
    public static String collect() {
        try {
            return report(readLogcat());
        } catch (IOException e) {
            Log.w(TAG, "Failed to read logcat", e);
            return null;
        }
    }

    static String report(String log) {
        return "App: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.FLAVOR + " " + BuildConfig.BUILD_TYPE + ")\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n\n"
                + redact(log, storedSecrets());
    }

    static String redact(String log, Collection<String> secrets) {
        List<String> longestFirst = new ArrayList<>();
        for (String secret : secrets) {
            if (secret != null && secret.length() >= MIN_SECRET_LENGTH) longestFirst.add(secret);
        }
        // So a value that contains another isn't left half replaced.
        longestFirst.sort(Comparator.comparingInt(String::length).reversed());

        String redacted = log;
        for (String secret : longestFirst) redacted = redacted.replace(secret, REDACTED);

        redacted = URL.matcher(redacted).replaceAll("$1<server>$2");
        redacted = QUERY_SECRET.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = NAMED_SECRET.matcher(redacted).replaceAll("$1$2" + REDACTED);
        redacted = AUTH_HEADER.matcher(redacted).replaceAll("$1" + REDACTED);
        return BEARER.matcher(redacted).replaceAll("$1" + REDACTED);
    }

    /** Keeps the device details and the newest lines, which are the ones nearest the problem. */
    public static String forClipboard(String report) {
        if (report.length() <= CLIPBOARD_LIMIT) return report;

        String details = report.substring(0, report.indexOf("\n\n") + 2);
        return details + "[older lines removed]\n" + report.substring(report.length() - CLIPBOARD_LIMIT);
    }

    public static boolean save(Context context, Uri destination, String report) {
        try (OutputStream out = context.getContentResolver().openOutputStream(destination)) {
            if (out == null) return false;
            out.write(report.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to write logs", e);
            return false;
        }
    }

    public static String suggestedFileName() {
        return "rollynn-logs-" + DateFormat.format("yyyy-MM-dd", new Date()) + ".txt";
    }

    private static String readLogcat() throws IOException {
        // Android limits this to the app's own lines, including an earlier crashed process.
        Process process = new ProcessBuilder("logcat", "-v", "threadtime", "-t", MAX_LINES)
                .redirectErrorStream(true)
                .start();
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) log.append(line).append('\n');
        }
        return log.toString();
    }

    /** Hostnames are included because errors like UnknownHostException name them without a URL. */
    private static List<String> storedSecrets() {
        List<String> secrets = new ArrayList<>(Arrays.asList(
                Preferences.getUser(),
                Preferences.getPassword(),
                Preferences.getToken(),
                Preferences.getSalt(),
                Preferences.getLastFmApiKey(),
                Preferences.getKoitoApiKey(),
                Preferences.getAudioMuseApiToken(),
                Preferences.getTranslationApiKey(),
                Preferences.getPopinnPassword()));

        for (String url : Arrays.asList(
                Preferences.getServer(),
                Preferences.getPopinnServerUrl(),
                Preferences.getKoitoServerUrl(),
                Preferences.getAudioMuseServerUrl(),
                Preferences.getTranslationApiUrl())) {
            if (url != null) secrets.add(Uri.parse(url).getHost());
        }
        return secrets;
    }
}
