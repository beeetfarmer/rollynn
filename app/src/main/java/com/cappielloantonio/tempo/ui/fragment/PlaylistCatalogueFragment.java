package com.cappielloantonio.tempo.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
import android.widget.SearchView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentPlaylistCatalogueBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.model.PlaylistFolder;
import com.cappielloantonio.tempo.repository.PlaylistFolderRepository;
import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.PlaylistFolderAdapter;
import com.cappielloantonio.tempo.ui.adapter.PlaylistHorizontalAdapter;
import com.cappielloantonio.tempo.ui.dialog.PlaylistEditorDialog;
import com.cappielloantonio.tempo.ui.dialog.PlaylistFolderChooserDialog;
import com.cappielloantonio.tempo.ui.dialog.PlaylistFolderEditorDialog;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.viewmodel.PlaylistCatalogueViewModel;
import com.cappielloantonio.tempo.viewmodel.PlaylistFolderViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@UnstableApi
public class PlaylistCatalogueFragment extends Fragment implements ClickCallback {
    private FragmentPlaylistCatalogueBinding bind;
    private MainActivity activity;
    private PlaylistCatalogueViewModel playlistCatalogueViewModel;
    private PlaylistFolderViewModel folderViewModel;

    private PlaylistHorizontalAdapter pinnedPlaylistAdapter;
    private PlaylistFolderAdapter folderAdapter;
    private PlaylistHorizontalAdapter unpinnedPlaylistAdapter;
    private ConcatAdapter concatAdapter;

    private List<Playlist> allPlaylists = new ArrayList<>();
    private List<String> lastPinnedIds = new ArrayList<>();
    private List<String> folderPlaylistIds = null;
    private Set<String> allAssignedIds = new HashSet<>();

    private boolean selectionMode = false;
    private OnBackPressedCallback backCallback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentPlaylistCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        playlistCatalogueViewModel = new ViewModelProvider(requireActivity()).get(PlaylistCatalogueViewModel.class);
        folderViewModel = new ViewModelProvider(requireActivity()).get(PlaylistFolderViewModel.class);

        init();
        initAppBar();
        initPlaylistCatalogueView();
        initBackNavigation();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (backCallback != null) backCallback.remove();
        bind = null;
    }

    private void init() {
        Bundle args = getArguments();
        if (args != null) {
            if (args.getString(Constants.PLAYLIST_ALL) != null) {
                playlistCatalogueViewModel.setType(Constants.PLAYLIST_ALL);
            } else if (args.getString(Constants.PLAYLIST_DOWNLOADED) != null) {
                playlistCatalogueViewModel.setType(Constants.PLAYLIST_DOWNLOADED);
            } else {
                playlistCatalogueViewModel.setType(Constants.PLAYLIST_ALL);
            }
        } else {
            playlistCatalogueViewModel.setType(Constants.PLAYLIST_ALL);
        }
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        bind.toolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            if (selectionMode) {
                exitSelectionMode();
            } else if (!folderViewModel.isAtRoot()) {
                folderViewModel.navigateUp();
            } else {
                activity.navController.navigateUp();
            }
        });

        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (selectionMode) return;
            if ((bind.albumInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                PlaylistFolder current = folderViewModel.getCurrentFolder().getValue();
                bind.toolbar.setTitle(current != null ? current.getName() : getString(R.string.playlist_catalogue_title));
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });

        folderViewModel.getCurrentFolder().observe(getViewLifecycleOwner(), folder -> {
            if (bind == null || selectionMode) return;
            if (folder != null) {
                bind.toolbar.setTitle(folder.getName());
            } else {
                bind.toolbar.setTitle(R.string.playlist_catalogue_title);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initPlaylistCatalogueView() {
        bind.playlistCatalogueRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistCatalogueRecyclerView.setHasFixedSize(true);

        pinnedPlaylistAdapter = new PlaylistHorizontalAdapter(this);
        folderAdapter = new PlaylistFolderAdapter(this);
        unpinnedPlaylistAdapter = new PlaylistHorizontalAdapter(this);
        concatAdapter = new ConcatAdapter(pinnedPlaylistAdapter, folderAdapter, unpinnedPlaylistAdapter);
        bind.playlistCatalogueRecyclerView.setAdapter(concatAdapter);

        if (getActivity() != null) {
            playlistCatalogueViewModel.getPinnedPlaylists().observe(getViewLifecycleOwner(), pinned -> {
                if (pinned != null) {
                    lastPinnedIds = pinned.stream().map(Playlist::getId).collect(Collectors.toList());
                    filterPlaylistsForCurrentFolder();
                }
            });

            playlistCatalogueViewModel.getPlaylistList(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), playlists -> {
                if (playlists != null) {
                    allPlaylists = playlists;
                    filterPlaylistsForCurrentFolder();
                }
            });

            folderViewModel.getChildFoldersLive().observe(getViewLifecycleOwner(), folders -> {
                if (folders != null) {
                    folderAdapter.setItems(folders);
                    updateFolderSubtitles(folders);
                }
            });

            folderViewModel.getPlaylistIdsInFolder().observe(getViewLifecycleOwner(), ids -> {
                folderPlaylistIds = ids;
                filterPlaylistsForCurrentFolder();
            });

            folderViewModel.getAllAssignedPlaylistIds().observe(getViewLifecycleOwner(), ids -> {
                allAssignedIds = ids != null ? new HashSet<>(ids) : new HashSet<>();
                filterPlaylistsForCurrentFolder();
            });
        }

        bind.playlistCatalogueRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.playlistListSortImageView.setOnClickListener(view -> showSortMenu(view));

        bind.createFolderFab.setOnClickListener(v -> showCreateFolderDialog());
    }

    private void filterPlaylistsForCurrentFolder() {
        if (allPlaylists == null) return;

        List<Playlist> filtered;
        if (folderViewModel.isAtRoot()) {
            filtered = allPlaylists.stream()
                    .filter(p -> !allAssignedIds.contains(p.getId()))
                    .collect(Collectors.toList());
        } else {
            if (folderPlaylistIds == null) return;
            Set<String> idsSet = new HashSet<>(folderPlaylistIds);
            filtered = allPlaylists.stream()
                    .filter(p -> idsSet.contains(p.getId()))
                    .collect(Collectors.toList());
        }

        Set<String> pinnedSet = new HashSet<>(lastPinnedIds);

        List<Playlist> pinned = filtered.stream()
                .filter(p -> pinnedSet.contains(p.getId()))
                .collect(Collectors.toList());

        List<Playlist> unpinned = filtered.stream()
                .filter(p -> !pinnedSet.contains(p.getId()))
                .collect(Collectors.toList());

        for (Playlist p : pinned) p.setPinned(true);
        for (Playlist p : unpinned) p.setPinned(false);

        pinnedPlaylistAdapter.setItems(pinned);
        pinnedPlaylistAdapter.setPinnedIds(lastPinnedIds);
        unpinnedPlaylistAdapter.setItems(unpinned);
    }

    private void updateFolderSubtitles(List<PlaylistFolder> folders) {
        new Thread(() -> {
            PlaylistFolderRepository repo = new PlaylistFolderRepository();
            for (PlaylistFolder folder : folders) {
                int plCount = repo.getPlaylistCountInFolder(folder.getId());
                int subCount = repo.getChildFolderCount(folder.getId());
                String subtitle;
                if (plCount > 0 && subCount > 0) {
                    subtitle = getString(R.string.playlist_folder_subtitle_items, plCount, subCount);
                } else if (plCount > 0) {
                    subtitle = getString(R.string.playlist_folder_subtitle_playlists_only, plCount);
                } else if (subCount > 0) {
                    subtitle = getString(R.string.playlist_folder_subtitle_folders_only, subCount);
                } else {
                    subtitle = getString(R.string.playlist_folder_subtitle_empty);
                }
                folderAdapter.setSubtitle(folder.getId(), subtitle);
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> folderAdapter.notifyDataSetChanged());
            }
        }).start();
    }

    private void initBackNavigation() {
        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (selectionMode) {
                    exitSelectionMode();
                } else if (!folderViewModel.isAtRoot()) {
                    folderViewModel.navigateUp();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.playlist_catalogue_toolbar_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                pinnedPlaylistAdapter.getFilter().filter(newText);
                unpinnedPlaylistAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        MenuItem moveItem = menu.findItem(R.id.action_move_to_folder);
        if (searchItem != null) searchItem.setVisible(!selectionMode);
        if (moveItem != null) moveItem.setVisible(selectionMode);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_move_to_folder) {
            moveSelectedToFolder();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showSortMenu(View view) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(R.menu.sort_playlist_popup_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_playlist_sort_name) {
                pinnedPlaylistAdapter.sort(Constants.PLAYLIST_ORDER_BY_NAME);
                unpinnedPlaylistAdapter.sort(Constants.PLAYLIST_ORDER_BY_NAME);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_playlist_sort_random) {
                pinnedPlaylistAdapter.sort(Constants.PLAYLIST_ORDER_BY_RANDOM);
                unpinnedPlaylistAdapter.sort(Constants.PLAYLIST_ORDER_BY_RANDOM);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void enterSelectionMode(String initialPlaylistId) {
        selectionMode = true;
        pinnedPlaylistAdapter.setSelectionMode(true);
        unpinnedPlaylistAdapter.setSelectionMode(true);
        pinnedPlaylistAdapter.toggleSelection(initialPlaylistId);
        unpinnedPlaylistAdapter.toggleSelection(initialPlaylistId);
        updateSelectionTitle();
        if (bind != null) bind.createFolderFab.setVisibility(View.GONE);
        requireActivity().invalidateOptionsMenu();
    }

    private void exitSelectionMode() {
        selectionMode = false;
        pinnedPlaylistAdapter.setSelectionMode(false);
        unpinnedPlaylistAdapter.setSelectionMode(false);
        PlaylistFolder current = folderViewModel.getCurrentFolder().getValue();
        if (bind != null) {
            bind.toolbar.setTitle(current != null ? current.getName() : getString(R.string.playlist_catalogue_title));
            bind.createFolderFab.setVisibility(View.VISIBLE);
        }
        requireActivity().invalidateOptionsMenu();
    }

    private void updateSelectionTitle() {
        int count = pinnedPlaylistAdapter.getSelectedIds().size() + unpinnedPlaylistAdapter.getSelectedIds().size();
        if (bind != null) {
            bind.toolbar.setTitle(getString(R.string.playlist_move_selected, count));
        }
    }

    private void moveSelectedToFolder() {
        Set<String> selectedIds = new HashSet<>();
        selectedIds.addAll(pinnedPlaylistAdapter.getSelectedIds());
        selectedIds.addAll(unpinnedPlaylistAdapter.getSelectedIds());
        if (selectedIds.isEmpty()) return;

        new Thread(() -> {
            List<PlaylistFolder> allFolders = folderViewModel.getAllFoldersSync();
            Long currentFolderId = folderViewModel.getCurrentFolderId().getValue();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    PlaylistFolderChooserDialog dialog = new PlaylistFolderChooserDialog(
                            allFolders, currentFolderId,
                            (PlaylistFolderChooserDialog.Callback) folderId -> {
                                for (String id : selectedIds) {
                                    folderViewModel.movePlaylistToFolder(id, folderId);
                                }
                                exitSelectionMode();
                            });
                    dialog.show(requireActivity().getSupportFragmentManager(), null);
                });
            }
        }).start();
    }

    private void showCreateFolderDialog() {
        PlaylistFolderEditorDialog dialog = new PlaylistFolderEditorDialog(null,
                (PlaylistFolderEditorDialog.Callback) name -> folderViewModel.createFolder(name, null));
        dialog.show(requireActivity().getSupportFragmentManager(), null);
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        if (selectionMode) {
            Playlist playlist = bundle.getParcelable(Constants.PLAYLIST_OBJECT);
            if (playlist == null) return;
            pinnedPlaylistAdapter.toggleSelection(playlist.getId());
            unpinnedPlaylistAdapter.toggleSelection(playlist.getId());
            updateSelectionTitle();
            return;
        }

        bundle.putBoolean("is_offline", false);
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
        hideKeyboard(requireView());
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        Playlist playlist = bundle.getParcelable(Constants.PLAYLIST_OBJECT);
        if (playlist == null) return;

        if (selectionMode) {
            pinnedPlaylistAdapter.toggleSelection(playlist.getId());
            unpinnedPlaylistAdapter.toggleSelection(playlist.getId());
            updateSelectionTitle();
            return;
        }

        View anchor = bind.playlistCatalogueRecyclerView.findViewWithTag(playlist.getId());
        if (anchor == null) anchor = bind.getRoot();

        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.playlist_actions_menu, popup.getMenu());

        MenuItem pinItem = popup.getMenu().findItem(R.id.menu_playlist_pin);
        pinItem.setTitle(playlist.isPinned() ? R.string.playlist_unpin : R.string.playlist_pin);

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_playlist_pin) {
                if (playlist.isPinned()) {
                    playlistCatalogueViewModel.unpinPlaylist(playlist);
                } else {
                    playlistCatalogueViewModel.pinPlaylist(playlist);
                }
                return true;
            } else if (menuItem.getItemId() == R.id.menu_playlist_edit) {
                PlaylistEditorDialog dialog = new PlaylistEditorDialog(null);
                dialog.setArguments(bundle);
                dialog.show(activity.getSupportFragmentManager(), null);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_playlist_move_to_folder) {
                showMoveToFolderDialog(playlist);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_playlist_select) {
                enterSelectionMode(playlist.getId());
                return true;
            } else if (menuItem.getItemId() == R.id.menu_playlist_delete) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                builder.setTitle(R.string.menu_delete);
                builder.setMessage(R.string.playlist_editor_dialog_action_delete_toast);
                builder.setPositiveButton(R.string.playlist_editor_dialog_neutral_button, (dialog, which) -> {
                    new PlaylistRepository().deletePlaylist(playlist.getId());
                });
                builder.setNegativeButton(R.string.playlist_editor_dialog_negative_button, null);
                builder.show();
                return true;
            }
            return false;
        });

        popup.show();
        hideKeyboard(requireView());
    }

    @Override
    public void onPlaylistFolderClick(Bundle bundle) {
        long folderId = bundle.getLong(Constants.PLAYLIST_FOLDER_ID);
        folderViewModel.navigateToFolder(folderId);
    }

    @Override
    public void onPlaylistFolderLongClick(Bundle bundle) {
        long folderId = bundle.getLong(Constants.PLAYLIST_FOLDER_ID);
        String folderName = bundle.getString(Constants.PLAYLIST_FOLDER_OBJECT);

        View anchor = bind.playlistCatalogueRecyclerView.findViewWithTag("folder_" + folderId);
        if (anchor == null) anchor = bind.getRoot();

        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.playlist_folder_actions_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_folder_rename) {
                PlaylistFolderEditorDialog dialog = new PlaylistFolderEditorDialog(folderName,
                        (PlaylistFolderEditorDialog.Callback) name -> new Thread(() -> {
                            PlaylistFolder folder = new PlaylistFolderRepository().getFolderById(folderId);
                            if (folder != null) {
                                folderViewModel.renameFolder(folder, name);
                            }
                        }).start());
                dialog.show(requireActivity().getSupportFragmentManager(), null);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_folder_delete) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                builder.setTitle(R.string.playlist_folder_delete);
                builder.setMessage(R.string.playlist_folder_delete_confirm);
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    new Thread(() -> {
                        PlaylistFolder folder = new PlaylistFolderRepository().getFolderById(folderId);
                        if (folder != null) {
                            folderViewModel.deleteFolder(folder);
                        }
                    }).start();
                });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showMoveToFolderDialog(Playlist playlist) {
        new Thread(() -> {
            List<PlaylistFolder> allFolders = folderViewModel.getAllFoldersSync();
            com.cappielloantonio.tempo.model.PlaylistFolderEntry entry =
                    com.cappielloantonio.tempo.database.AppDatabase.getInstance()
                            .playlistFolderDao().getEntryForPlaylist(playlist.getId());
            Long currentFolderId = entry != null ? entry.getFolderId() : null;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    PlaylistFolderChooserDialog dialog = new PlaylistFolderChooserDialog(
                            allFolders, currentFolderId,
                            (PlaylistFolderChooserDialog.Callback) folderId ->
                                    folderViewModel.movePlaylistToFolder(playlist.getId(), folderId));
                    dialog.show(requireActivity().getSupportFragmentManager(), null);
                });
            }
        }).start();
    }
}
