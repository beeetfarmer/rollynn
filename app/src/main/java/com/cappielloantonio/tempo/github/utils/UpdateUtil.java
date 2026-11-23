package com.cappielloantonio.tempo.github.utils;

import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.github.models.LatestRelease;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateUtil {
    private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+)*");

    public static boolean showUpdateDialog(LatestRelease release) {
        return release.getTagName() != null && release.getHtmlUrl() != null
                && isNewer(release.getTagName(), BuildConfig.VERSION_NAME);
    }

    /** Compares the numbers only, so "v0.7.0" and "0.7" match and "-beta" is ignored. */
    static boolean isNewer(String remote, String local) {
        int[] r = numbers(remote);
        int[] l = numbers(local);
        if (r == null || l == null) return false;

        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int remotePart = i < r.length ? r[i] : 0;
            int localPart = i < l.length ? l[i] : 0;
            if (remotePart != localPart) return remotePart > localPart;
        }
        return false;
    }

    private static int[] numbers(String version) {
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.find()) return null;

        String[] parts = matcher.group().split("\\.");
        int[] numbers = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) numbers[i] = Integer.parseInt(parts[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return numbers;
    }
}
