package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.format.DateFormat;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

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
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Writes settings to a JSON file the user chooses, and reads them back.
 *
 * Credentials never go in as plain settings. They would otherwise travel in
 * cleartext inside a file destined for shared storage or a cloud sync folder,
 * and the app cannot know where that file ends up. Nothing here can reconstruct
 * a login, so a restore leaves the current session alone.
 *
 * API keys for other services are hard to replace, so the user can choose a
 * password to carry them, encrypted, in their own section of the file.
 */
public final class SettingsBackupUtil {
    private static final String TAG = "SettingsBackupUtil";

    private static final int FORMAT_VERSION = 1;

    private static final String KEY_FORMAT = "format";
    private static final String KEY_PACKAGE = "package";
    private static final String KEY_VERSION_NAME = "versionName";
    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_API_KEYS = "apiKeys";
    private static final String KEY_ITERATIONS = "iterations";
    private static final String KEY_SALT = "salt";
    private static final String KEY_IV = "iv";
    private static final String KEY_DATA = "data";
    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";

    private static final String TYPE_STRING = "string";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_FLOAT = "float";
    private static final String TYPE_STRING_SET = "stringSet";

    /** From {@link #restore}: the settings were left alone so the user can try again. */
    public static final int RESTORE_WRONG_PASSWORD = -2;

    // SHA-256 needs API 26. SHA-1 stays sound inside PBKDF2 and every supported version has it.
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int KDF_ITERATIONS = 1_300_000;
    // The count comes from the file, and a huge one would hang the restore.
    private static final int KDF_MAX_ITERATIONS = 10_000_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

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
            "koito_api_key",
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

    /** The server login is not here: restoring it onto another session could not be completed. */
    private static JSONObject readApiKeys() throws JSONException {
        JSONObject keys = new JSONObject();
        putIfSet(keys, "last_fm_api_key", Preferences.getLastFmApiKey());
        putIfSet(keys, "translation_api_key", Preferences.getTranslationApiKey());
        putIfSet(keys, "koito_api_key", Preferences.getKoitoApiKey());
        putIfSet(keys, "audiomuse_api_token", Preferences.getAudioMuseApiToken());
        putIfSet(keys, "popinn_password", Preferences.getPopinnPassword());
        return keys;
    }

    private static void putIfSet(JSONObject keys, String name, @Nullable String value) throws JSONException {
        if (value != null && !value.isEmpty()) keys.put(name, value);
    }

    /** Keys missing from the backup are left as they are: secrets are never cleared by a restore. */
    private static int writeApiKeys(JSONObject keys) {
        if (keys.has("last_fm_api_key")) Preferences.setLastFmApiKey(keys.optString("last_fm_api_key"));
        if (keys.has("translation_api_key")) Preferences.setTranslationApiKey(keys.optString("translation_api_key"));
        if (keys.has("koito_api_key")) Preferences.setKoitoApiKey(keys.optString("koito_api_key"));
        if (keys.has("audiomuse_api_token")) Preferences.setAudioMuseApiToken(keys.optString("audiomuse_api_token"));
        if (keys.has("popinn_password")) Preferences.setPopinnPassword(keys.optString("popinn_password"));
        return keys.length();
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return new SecretKeySpec(SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static JSONObject encrypt(JSONObject keys, char[] password) throws GeneralSecurityException, JSONException {
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, KDF_ITERATIONS), new GCMParameterSpec(TAG_BITS, iv));

        JSONObject encrypted = new JSONObject();
        encrypted.put(KEY_ITERATIONS, KDF_ITERATIONS);
        encrypted.put(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));
        encrypted.put(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP));
        encrypted.put(KEY_DATA, Base64.encodeToString(cipher.doFinal(keys.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP));
        return encrypted;
    }

    private static JSONObject decrypt(JSONObject encrypted, char[] password) throws GeneralSecurityException, JSONException {
        int iterations = encrypted.getInt(KEY_ITERATIONS);
        if (iterations < 1 || iterations > KDF_MAX_ITERATIONS) throw new JSONException("Bad iteration count");

        byte[] salt = Base64.decode(encrypted.getString(KEY_SALT), Base64.NO_WRAP);
        byte[] iv = Base64.decode(encrypted.getString(KEY_IV), Base64.NO_WRAP);
        byte[] data = Base64.decode(encrypted.getString(KEY_DATA), Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), new GCMParameterSpec(TAG_BITS, iv));
        return new JSONObject(new String(cipher.doFinal(data), StandardCharsets.UTF_8));
    }

    /** Whether there are any API keys a backup could carry. */
    public static boolean hasApiKeys() {
        try {
            return readApiKeys().length() > 0;
        } catch (JSONException exception) {
            return false;
        }
    }

    /** Whether the file holds encrypted API keys, and so needs a password to restore them. */
    public static boolean hasEncryptedApiKeys(Context context, Uri source) {
        try {
            String content = read(context, source);
            return content != null && new JSONObject(content).optJSONObject(KEY_API_KEYS) != null;
        } catch (JSONException exception) {
            return false;
        }
    }

    /**
     * Returns the number of settings and keys written, or -1 if the backup failed.
     * The key derivation is slow on purpose, so with a password this must run off the main thread.
     */
    public static int export(Context context, Uri destination, @Nullable char[] password) {
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

            if (password != null) {
                JSONObject keys = readApiKeys();
                if (keys.length() > 0) {
                    root.put(KEY_API_KEYS, encrypt(keys, password));
                    exported += keys.length();
                }
            }

            try (OutputStream stream = context.getContentResolver().openOutputStream(destination, "wt")) {
                if (stream == null) return -1;
                stream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }

            return exported;
        } catch (JSONException | IOException | GeneralSecurityException | SecurityException exception) {
            Log.w(TAG, "Settings backup failed", exception);
            return -1;
        }
    }

    /**
     * Returns the number of settings and keys applied, -1 if the file was unusable, or
     * {@link #RESTORE_WRONG_PASSWORD}. Nothing changes unless it succeeds. A null password restores
     * only the settings. With a password this must run off the main thread, as for {@link #export}.
     */
    public static int restore(Context context, Uri source, @Nullable char[] password) {
        try {
            String content = read(context, source);
            if (content == null) return -1;

            JSONObject root = new JSONObject(content);
            // A future format could mean anything; refusing beats guessing.
            if (root.optInt(KEY_FORMAT, 0) > FORMAT_VERSION) return -1;

            JSONObject settings = root.optJSONObject(KEY_SETTINGS);
            if (settings == null) return -1;

            // Decrypted first, so a wrong password leaves the settings untouched.
            JSONObject encryptedKeys = root.optJSONObject(KEY_API_KEYS);
            JSONObject apiKeys = password != null && encryptedKeys != null ? decrypt(encryptedKeys, password) : null;

            SharedPreferences preferences = App.getInstance().getPreferences();
            SharedPreferences.Editor editor = preferences.edit();
            int restored = 0;

            // A setting left at its default is not stored, so it is not in the
            // file either. Clearing first is what returns those to their defaults.
            for (String key : preferences.getAll().keySet()) {
                if (!isExcluded(key)) editor.remove(key);
            }

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
            if (apiKeys != null) restored += writeApiKeys(apiKeys);
            return restored;
        } catch (BadPaddingException exception) {
            return RESTORE_WRONG_PASSWORD;
        } catch (JSONException | GeneralSecurityException | IllegalArgumentException | SecurityException exception) {
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
        return "rollynn-settings-" + DateFormat.format("yyyy-MM-dd", new Date()) + ".json";
    }
}
