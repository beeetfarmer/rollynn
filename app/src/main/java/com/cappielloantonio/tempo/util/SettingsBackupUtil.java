package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.format.DateFormat;
import android.util.Log;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Writes settings to a JSON file the user chooses, and reads them back.
 *
 * Credentials are deliberately left out. They would otherwise travel in
 * cleartext inside a file destined for shared storage or a cloud sync folder,
 * and the app cannot know where that file ends up. Nothing here can reconstruct
 * a login, so a restore leaves the current session alone.
 */
public final class SettingsBackupUtil {
    private static final String TAG = "SettingsBackupUtil";

    private static final int FORMAT_VERSION = 1;

    private static final String KEY_FORMAT = "format";
    private static final String KEY_PACKAGE = "package";
    private static final String KEY_VERSION_NAME = "versionName";
    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";

    private static final String TYPE_STRING = "string";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_FLOAT = "float";
    private static final String TYPE_STRING_SET = "stringSet";

    /**
     * Never exported and never restored.
     *
     * Secrets are here even though they are meant to live in encrypted storage:
     * that store silently falls back to plain preferences when it cannot
     * initialise, so a key may be in either one and this is the only reliable
     * guard. Session keys are excluded for a different reason — restoring a
     * server and username with no matching password leaves a login that cannot
     * be completed.
     */
    private static final Set<String> EXCLUDED_KEYS = new HashSet<>(Arrays.asList(
            // Secrets
            "password",
            "token",
            "salt",
            "last_fm_api_key",
            "translation_api_key",
            "popinn_password",
            "audiomuse_api_token",
            // Session identity
            "server",
            "user",
            "server_id",
            "low_security",
            "in_use_server_address"
    ));

    /** Per-server passwords are stored as "password_<serverId>". */
    private static final String EXCLUDED_PREFIX = "password_";

    private SettingsBackupUtil() {
    }

    private static boolean isExcluded(String key) {
        return key == null || EXCLUDED_KEYS.contains(key) || key.startsWith(EXCLUDED_PREFIX);
    }

    /** Returns the number of settings written, or -1 if the backup failed. */
    public static int export(Context context, Uri destination) {
        SharedPreferences preferences = App.getInstance().getPreferences();

        try {
            JSONObject settings = new JSONObject();
            int exported = 0;

            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                if (isExcluded(entry.getKey())) continue;

                JSONObject encoded = encode(entry.getValue());
                if (encoded == null) continue;

                settings.put(entry.getKey(), encoded);
                exported++;
            }

            JSONObject root = new JSONObject();
            root.put(KEY_FORMAT, FORMAT_VERSION);
            root.put(KEY_PACKAGE, context.getPackageName());
            root.put(KEY_VERSION_NAME, BuildConfig.VERSION_NAME);
            root.put(KEY_SETTINGS, settings);

            try (OutputStream stream = context.getContentResolver().openOutputStream(destination, "wt")) {
                if (stream == null) return -1;
                stream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }

            return exported;
        } catch (JSONException | IOException | SecurityException exception) {
            Log.w(TAG, "Settings backup failed", exception);
            return -1;
        }
    }

    /** Returns the number of settings applied, or -1 if the file was unusable. */
    public static int restore(Context context, Uri source) {
        try {
            String content = read(context, source);
            if (content == null) return -1;

            JSONObject root = new JSONObject(content);
            // A future format could mean anything; refusing beats guessing.
            if (root.optInt(KEY_FORMAT, 0) > FORMAT_VERSION) return -1;

            JSONObject settings = root.optJSONObject(KEY_SETTINGS);
            if (settings == null) return -1;

            SharedPreferences.Editor editor = App.getInstance().getPreferences().edit();
            int restored = 0;

            for (Iterator<String> keys = settings.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (isExcluded(key)) continue;

                JSONObject encoded = settings.optJSONObject(key);
                if (encoded == null) continue;

                if (decodeInto(editor, key, encoded)) restored++;
            }

            // Committed rather than applied: the caller restarts the activity
            // straight after, and the write has to have landed by then.
            editor.commit();
            return restored;
        } catch (JSONException | SecurityException exception) {
            Log.w(TAG, "Settings restore failed", exception);
            return -1;
        }
    }

    private static String read(Context context, Uri source) {
        StringBuilder builder = new StringBuilder();

        try (InputStream stream = context.getContentResolver().openInputStream(source)) {
            if (stream == null) return null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
        } catch (IOException | SecurityException exception) {
            Log.w(TAG, "Could not read backup file", exception);
            return null;
        }

        return builder.toString();
    }

    private static JSONObject encode(Object value) throws JSONException {
        JSONObject encoded = new JSONObject();

        if (value instanceof String) {
            encoded.put(KEY_TYPE, TYPE_STRING);
            encoded.put(KEY_VALUE, value);
        } else if (value instanceof Boolean) {
            encoded.put(KEY_TYPE, TYPE_BOOLEAN);
            encoded.put(KEY_VALUE, value);
        } else if (value instanceof Integer) {
            encoded.put(KEY_TYPE, TYPE_INT);
            encoded.put(KEY_VALUE, value);
        } else if (value instanceof Long) {
            encoded.put(KEY_TYPE, TYPE_LONG);
            encoded.put(KEY_VALUE, value);
        } else if (value instanceof Float) {
            encoded.put(KEY_TYPE, TYPE_FLOAT);
            encoded.put(KEY_VALUE, (double) (Float) value);
        } else if (value instanceof Set) {
            encoded.put(KEY_TYPE, TYPE_STRING_SET);
            encoded.put(KEY_VALUE, new JSONArray((Set<?>) value));
        } else {
            return null;
        }

        return encoded;
    }

    /**
     * Types are carried in the file because SharedPreferences is strict about
     * them: reading a key back as the wrong type throws, and a preference
     * written as an Int cannot be read as a String.
     */
    private static boolean decodeInto(SharedPreferences.Editor editor, String key, JSONObject encoded) {
        String type = encoded.optString(KEY_TYPE, "");

        switch (type) {
            case TYPE_STRING:
                editor.putString(key, encoded.optString(KEY_VALUE, ""));
                return true;
            case TYPE_BOOLEAN:
                editor.putBoolean(key, encoded.optBoolean(KEY_VALUE, false));
                return true;
            case TYPE_INT:
                editor.putInt(key, encoded.optInt(KEY_VALUE, 0));
                return true;
            case TYPE_LONG:
                editor.putLong(key, encoded.optLong(KEY_VALUE, 0L));
                return true;
            case TYPE_FLOAT:
                editor.putFloat(key, (float) encoded.optDouble(KEY_VALUE, 0d));
                return true;
            case TYPE_STRING_SET:
                JSONArray array = encoded.optJSONArray(KEY_VALUE);
                if (array == null) return false;

                Set<String> values = new LinkedHashSet<>();
                for (int i = 0; i < array.length(); i++) {
                    values.add(array.optString(i));
                }
                editor.putStringSet(key, values);
                return true;
            default:
                return false;
        }
    }

    /** Suggested file name, dated so successive backups do not collide. */
    public static String suggestedFileName() {
        return "tempus-settings-" + DateFormat.format("yyyy-MM-dd", new Date()) + ".json";
    }
}
