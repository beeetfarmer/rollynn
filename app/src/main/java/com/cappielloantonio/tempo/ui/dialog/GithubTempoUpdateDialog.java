package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogGithubTempoUpdateBinding;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class GithubTempoUpdateDialog extends DialogFragment {
    public static final String TAG = "GithubTempoUpdateDialog";
    private static final String ARG_URL = "url";

    /** The release page goes in the arguments, so the dialog can be rebuilt after a rotation. */
    public static GithubTempoUpdateDialog newInstance(String releaseUrl) {
        GithubTempoUpdateDialog dialog = new GithubTempoUpdateDialog();
        Bundle args = new Bundle();
        args.putString(ARG_URL, releaseUrl);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DialogGithubTempoUpdateBinding bind = DialogGithubTempoUpdateBinding.inflate(getLayoutInflater());

        return new MaterialAlertDialogBuilder(requireActivity())
                .setView(bind.getRoot())
                .setTitle(R.string.github_update_dialog_title)
                .setPositiveButton(R.string.github_update_dialog_positive_button, (dialog, id) -> openRelease())
                .setNegativeButton(R.string.github_update_dialog_negative_button, (dialog, id) -> Preferences.setTempusUpdateReminder())
                .create();
    }

    private void openRelease() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(requireArguments().getString(ARG_URL)));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
