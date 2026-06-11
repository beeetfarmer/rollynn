package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogPlaylistChooserBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.ui.adapter.PlaylistDialogHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.viewmodel.PlaylistChooserViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PlaylistChooserDialog extends DialogFragment {
    private DialogPlaylistChooserBinding bind;
    private PlaylistChooserViewModel playlistChooserViewModel;
    private PlaylistDialogHorizontalAdapter playlistDialogHorizontalAdapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        bind = DialogPlaylistChooserBinding.inflate(getLayoutInflater());

        playlistChooserViewModel = new ViewModelProvider(requireActivity()).get(PlaylistChooserViewModel.class);

        bind.playlistDialogChooserVisibilitySwitch.setOnCheckedChangeListener(
                (buttonView,
                 isChecked) -> playlistChooserViewModel.setIsPlaylistPublic(isChecked)
        );
        bind.playlistChooserDialogCreateButton.setOnClickListener(v -> launchPlaylistEditor());
        bind.playlistChooserDialogCancelButton.setOnClickListener(v -> dismiss());
        bind.playlistChooserDialogConfirmButton.setOnClickListener(v -> {
            if (playlistChooserViewModel.getSongsToAdd() != null && !playlistChooserViewModel.getSongsToAdd().isEmpty()) {
                java.util.List<String> selectedIds = playlistDialogHorizontalAdapter.getSelectedIds();
                if (!selectedIds.isEmpty()) {
                    playlistChooserViewModel.addSongsToPlaylists(requireActivity(), getDialog(), selectedIds);
                } else {
                    Toast.makeText(requireContext(), R.string.playlist_chooser_dialog_toast_add_failure, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), R.string.playlist_chooser_dialog_toast_add_failure, Toast.LENGTH_SHORT).show();
            }
        });

        initPlaylistView();
        setSongInfo();

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(bind.getRoot())
                .setTitle(R.string.playlist_chooser_dialog_title)
                .create();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void setSongInfo() {
        playlistChooserViewModel.setSongsToAdd(requireArguments().getParcelableArrayList(Constants.TRACKS_OBJECT));
    }

    private void launchPlaylistEditor() {
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(
                Constants.TRACKS_OBJECT,
                playlistChooserViewModel.getSongsToAdd()
        );

        PlaylistEditorDialog editorDialog = new PlaylistEditorDialog(null);
        editorDialog.setArguments(bundle);
        editorDialog.show(
                requireActivity().getSupportFragmentManager(),
                null);

        dismiss();
    }

    private void initPlaylistView() {
        bind.playlistDialogRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistDialogRecyclerView.setHasFixedSize(true);

        playlistDialogHorizontalAdapter = new PlaylistDialogHorizontalAdapter(null);
        bind.playlistDialogRecyclerView.setAdapter(playlistDialogHorizontalAdapter);

        playlistChooserViewModel.getPlaylistList(requireActivity()).observe(requireActivity(), playlists -> {
            if (playlists != null) {
                if (!playlists.isEmpty()) {
                    if (bind != null) bind.noPlaylistsCreatedTextView.setVisibility(View.GONE);
                    if (bind != null) bind.playlistDialogRecyclerView.setVisibility(View.VISIBLE);
                    playlistDialogHorizontalAdapter.setItems(playlists);
                } else {
                    if (bind != null) bind.noPlaylistsCreatedTextView.setVisibility(View.VISIBLE);
                    if (bind != null) bind.playlistDialogRecyclerView.setVisibility(View.GONE);
                }
            }
        });

        bind.playlistSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (playlistDialogHorizontalAdapter != null) {
                    playlistDialogHorizontalAdapter.filter(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
}
