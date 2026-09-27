package com.cappielloantonio.tempo.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.audiomuse.AudioMuseApi;
import com.cappielloantonio.tempo.audiomuse.AudioMuseChatRequest;
import com.cappielloantonio.tempo.audiomuse.AudioMuseChatResponse;
import com.cappielloantonio.tempo.audiomuse.AudioMuseClient;
import com.cappielloantonio.tempo.audiomuse.AudioMuseTrack;
import com.cappielloantonio.tempo.databinding.FragmentAiPlaylistBinding;
import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.google.gson.Gson;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Builds a playlist from a text prompt with AudioMuse-AI: the prompt goes to its
 * chat playlist endpoint, and the returned tracks (already music-server song ids)
 * are saved under the user's own account with a Subsonic createPlaylist call.
 */
public class AiPlaylistFragment extends Fragment {
    private FragmentAiPlaylistBinding bind;
    private Call<AudioMuseChatResponse> pending;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        bind = FragmentAiPlaylistBinding.inflate(inflater, container, false);
        bind.aiPlaylistToolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        bind.aiPlaylistGenerateButton.setOnClickListener(v -> generate());
        return bind.getRoot();
    }

    @Override
    public void onDestroyView() {
        // Leaving the page abandons the request; nothing is saved.
        if (pending != null) pending.cancel();
        super.onDestroyView();
        bind = null;
    }

    private void generate() {
        String name = text(bind.aiPlaylistNameEditText.getText());
        String prompt = text(bind.aiPlaylistPromptEditText.getText());
        bind.aiPlaylistNameLayout.setError(name.isEmpty() ? getString(R.string.ai_playlist_name_required) : null);
        bind.aiPlaylistPromptLayout.setError(prompt.isEmpty() ? getString(R.string.ai_playlist_prompt_required) : null);
        if (name.isEmpty() || prompt.isEmpty()) return;

        AudioMuseApi api = AudioMuseClient.getApi();
        if (api == null) {
            Toast.makeText(requireContext(), R.string.ai_playlist_not_configured, Toast.LENGTH_LONG).show();
            return;
        }

        hideKeyboard();
        showLoading(R.string.ai_playlist_generating, true);
        pending = api.chatPlaylist(new AudioMuseChatRequest(prompt));
        pending.enqueue(new Callback<AudioMuseChatResponse>() {
            @Override
            public void onResponse(@NonNull Call<AudioMuseChatResponse> call, @NonNull Response<AudioMuseChatResponse> response) {
                if (bind == null) return;
                if (!response.isSuccessful() || response.body() == null || response.body().getResponse() == null) {
                    fail(getString(R.string.ai_playlist_failed, errorMessage(response)));
                    return;
                }

                ArrayList<String> songIds = new ArrayList<>();
                if (response.body().getResponse().getQueryResults() != null) {
                    for (AudioMuseTrack track : response.body().getResponse().getQueryResults()) {
                        if (track.getItemId() != null) songIds.add(track.getItemId());
                    }
                }
                if (songIds.isEmpty()) {
                    fail(getString(R.string.ai_playlist_no_results));
                    return;
                }
                save(name, songIds);
            }

            @Override
            public void onFailure(@NonNull Call<AudioMuseChatResponse> call, @NonNull Throwable t) {
                if (bind == null || call.isCanceled()) return;
                fail(getString(R.string.ai_playlist_failed, t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }
        });
    }

    private void save(String name, ArrayList<String> songIds) {
        showLoading(R.string.ai_playlist_saving, false);
        new PlaylistRepository().createPlaylist(null, name, songIds, new PlaylistRepository.AddToPlaylistCallback() {
            @Override
            public void onSuccess() {
                if (bind == null) return;
                Toast.makeText(requireContext(), getString(R.string.ai_playlist_created, name, songIds.size()), Toast.LENGTH_LONG).show();
                Navigation.findNavController(bind.getRoot()).navigateUp();
            }

            @Override
            public void onFailure() {
                if (bind != null) fail(getString(R.string.ai_playlist_save_failed));
            }

            @Override
            public void onAllSkipped() {
            }
        });
    }

    /** AudioMuse-AI reports failures as {"error": "..."}; fall back to the HTTP status. */
    private String errorMessage(Response<AudioMuseChatResponse> response) {
        try {
            if (response.errorBody() != null) {
                AudioMuseChatResponse error = new Gson().fromJson(response.errorBody().charStream(), AudioMuseChatResponse.class);
                if (error != null && error.getError() != null) return error.getError();
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + response.code();
    }

    private void showLoading(int titleRes, boolean showDetail) {
        bind.aiPlaylistLoadingTitle.setText(titleRes);
        bind.aiPlaylistLoadingDetail.setVisibility(showDetail ? View.VISIBLE : View.GONE);
        bind.aiPlaylistLoading.setVisibility(View.VISIBLE);
    }

    private void fail(String message) {
        bind.aiPlaylistLoading.setVisibility(View.GONE);
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(bind.getRoot().getWindowToken(), 0);
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
