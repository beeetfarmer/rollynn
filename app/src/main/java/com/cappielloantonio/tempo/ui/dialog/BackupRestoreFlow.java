package com.cappielloantonio.tempo.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogBackupPasswordBinding;
import com.cappielloantonio.tempo.helper.ThemeHelper;
import com.cappielloantonio.tempo.popinn.PopinnClient;
import com.cappielloantonio.tempo.popinn.PopinnRepository;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.SettingsBackupUtil;
import com.cappielloantonio.tempo.viewmodel.HomeViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Restores a backup file, from Settings or the login screen: asks for its password when it holds
 * server logins or API keys, restores off the main thread, and asks again after a wrong password.
 */
public final class BackupRestoreFlow {
    public interface Callback {
        /**
         * On the main thread, after a successful restore.
         *
         * @param loginServerId the server in use when the backup was made, if its login was restored
         */
        void onRestored(int restored, @Nullable String loginServerId);
    }

    private BackupRestoreFlow() {
    }

    public static void start(Fragment fragment, Uri source, Callback callback) {
        if (SettingsBackupUtil.hasEncryptedSecrets(fragment.requireContext(), source)) {
            askPassword(fragment, source, false, callback);
        } else {
            restore(fragment, source, null, callback);
        }
    }

    /**
     * Brings the app in line with what was restored. The caller then recreates the activity, as theme
     * and language are only read when it starts.
     */
    @OptIn(markerClass = UnstableApi.class)
    public static void applyRestoredSettings(FragmentActivity activity) {
        // The Popinn client caches its server and login, both of which may have changed.
        PopinnClient.reset();
        PopinnRepository.clearCache();
        // The home screen keeps its section order in a model that outlives the activity.
        new ViewModelProvider(activity).get(HomeViewModel.class).refreshHomeSectorList();
        ThemeHelper.applyTheme(App.getInstance().getPreferences().getString(Preferences.THEME, ThemeHelper.DEFAULT_MODE));
    }

    private static void askPassword(Fragment fragment, Uri source, boolean wrongPassword, Callback callback) {
        DialogBackupPasswordBinding bind = DialogBackupPasswordBinding.inflate(fragment.getLayoutInflater());
        bind.backupPasswordConfirmLayout.setVisibility(View.GONE);
        if (wrongPassword) {
            bind.backupPasswordLayout.setError(fragment.getString(R.string.settings_restore_keys_wrong_password));
        }

        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.settings_restore_secrets_title)
                .setMessage(R.string.settings_restore_secrets_message)
                .setView(bind.getRoot())
                .setNeutralButton(android.R.string.cancel, null)
                .setNegativeButton(R.string.settings_backup_secrets_skip, (dialog, which) -> restore(fragment, source, null, callback))
                .setPositiveButton(R.string.settings_restore_confirm_button, (dialog, which) ->
                        restore(fragment, source, String.valueOf(bind.backupPasswordText.getText()).toCharArray(), callback))
                .show();
    }

    private static void restore(Fragment fragment, Uri source, @Nullable char[] password, Callback callback) {
        Activity activity = fragment.requireActivity();
        Context context = activity.getApplicationContext();

        // The key derivation is slow on purpose.
        new Thread(() -> {
            AtomicReference<String> loginServerId = new AtomicReference<>();
            int restored = SettingsBackupUtil.restore(context, source, password, loginServerId);
            activity.runOnUiThread(() -> {
                if (!fragment.isAdded()) return;

                if (restored == SettingsBackupUtil.RESTORE_WRONG_PASSWORD) {
                    askPassword(fragment, source, true, callback);
                } else if (restored < 0) {
                    Toast.makeText(context, R.string.settings_restore_failure, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, context.getString(R.string.settings_restore_success, restored), Toast.LENGTH_LONG).show();
                    callback.onRestored(restored, loginServerId.get());
                }
            });
        }).start();
    }
}
