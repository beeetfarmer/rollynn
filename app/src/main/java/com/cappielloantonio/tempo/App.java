package com.cappielloantonio.tempo;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.cappielloantonio.tempo.github.Github;
import com.cappielloantonio.tempo.helper.ThemeHelper;
import com.cappielloantonio.tempo.subsonic.Subsonic;
import com.cappielloantonio.tempo.subsonic.SubsonicPreferences;
import com.cappielloantonio.tempo.util.Preferences;

public class App extends Application {
    private static App instance;
    private static Context context;
    private static Subsonic subsonic;
    private static Github github;
    private static SharedPreferences preferences;
    private static SharedPreferences encryptedPreferences;

    private static final String[] SENSITIVE_KEYS = {"password", "token", "salt", "last_fm_api_key"};

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String themePref = sharedPreferences.getString(Preferences.THEME, ThemeHelper.DEFAULT_MODE);
        ThemeHelper.applyTheme(themePref);

        instance = new App();
        context = getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        initEncryptedPreferences();
    }

    private static boolean encryptedPreferencesAvailable = false;

    private void initEncryptedPreferences() {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                encryptedPreferences = EncryptedSharedPreferences.create(
                        "secure_prefs",
                        masterKeyAlias,
                        context,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
                encryptedPreferencesAvailable = true;
                migrateSensitivePreferences();
                return;
            } catch (Exception e) {
                Log.e("App", "Encrypted preferences init failed (attempt " + (attempt + 1) + ")", e);
            }
        }
        encryptedPreferences = null;
        encryptedPreferencesAvailable = false;
    }

    public static boolean isEncryptedPreferencesAvailable() {
        return encryptedPreferencesAvailable;
    }

    private void migrateSensitivePreferences() {
        if (encryptedPreferences == null) return;

        SharedPreferences.Editor encEditor = encryptedPreferences.edit();
        SharedPreferences.Editor plainEditor = preferences.edit();
        boolean migrated = false;

        for (String key : SENSITIVE_KEYS) {
            if (preferences.contains(key) && !encryptedPreferences.contains(key)) {
                String value = preferences.getString(key, null);
                if (value != null) {
                    encEditor.putString(key, value);
                    plainEditor.remove(key);
                    migrated = true;
                }
            }
        }

        if (migrated) {
            encEditor.apply();
            plainEditor.apply();
        }
    }

    public static App getInstance() {
        if (instance == null) {
            instance = new App();
        }

        return instance;
    }

    public static Context getContext() {
        if (context == null) {
            context = getInstance();
        }

        return context;
    }

    public static Subsonic getSubsonicClientInstance(boolean override) {
        if (subsonic == null || override) {
            subsonic = getSubsonicClient();
        }
        return subsonic;
    }
    
    public static Subsonic getSubsonicPublicClientInstance(boolean override) {

        /*
        If I do the shortcut that the IDE suggests:
            SubsonicPreferences preferences = getSubsonicPreferences1();
        During the chain of calls it will run the following:
            String server = Preferences.getInUseServerAddress();
        Which could return Local URL, causing issues like generating public shares with Local URL

        To prevent this I just replicated the entire chain of functions here,
        if you need a call to Subsonic using the Server (Public) URL use this function.
         */

        String server = Preferences.getServer();
        String username = Preferences.getUser();
        String password = Preferences.getPassword();
        String token = Preferences.getToken();
        String salt = Preferences.getSalt();
        boolean isLowSecurity = Preferences.isLowScurity();

        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl(server);
        preferences.setUsername(username);
        preferences.setAuthentication(password, token, salt, isLowSecurity);

        if (subsonic == null || override) {
            
            if (preferences.getAuthentication() != null) {
                if (preferences.getAuthentication().getPassword() != null)
                    Preferences.setPassword(preferences.getAuthentication().getPassword());
                if (preferences.getAuthentication().getToken() != null)
                    Preferences.setToken(preferences.getAuthentication().getToken());
                if (preferences.getAuthentication().getSalt() != null)
                    Preferences.setSalt(preferences.getAuthentication().getSalt());
            }

            
        }
        
        return new Subsonic(preferences);
    }

    public static Github getGithubClientInstance() {
        if (github == null) {
            github = new Github();
        }
        return github;
    }

    public SharedPreferences getPreferences() {
        if (preferences == null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(context);
        }

        return preferences;
    }

    public SharedPreferences getEncryptedPreferences() {
        if (encryptedPreferences != null) return encryptedPreferences;
        Log.w("App", "Encrypted preferences unavailable, credentials will not be persisted securely");
        return preferences;
    }

    public static void refreshSubsonicClient() {
        subsonic = getSubsonicClient();
    }

    private static Subsonic getSubsonicClient() {
        SubsonicPreferences preferences = getSubsonicPreferences();

        if (preferences.getAuthentication() != null) {
            if (preferences.getAuthentication().getPassword() != null)
                Preferences.setPassword(preferences.getAuthentication().getPassword());
            if (preferences.getAuthentication().getToken() != null)
                Preferences.setToken(preferences.getAuthentication().getToken());
            if (preferences.getAuthentication().getSalt() != null)
                Preferences.setSalt(preferences.getAuthentication().getSalt());
        }

        return new Subsonic(preferences);
    }

    @NonNull
    private static SubsonicPreferences getSubsonicPreferences() {
        String server = Preferences.getInUseServerAddress();
        String username = Preferences.getUser();
        String password = Preferences.getPassword();
        String token = Preferences.getToken();
        String salt = Preferences.getSalt();
        boolean isLowSecurity = Preferences.isLowScurity();

        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl(server);
        preferences.setUsername(username);
        preferences.setAuthentication(password, token, salt, isLowSecurity);

        return preferences;
    }
}
