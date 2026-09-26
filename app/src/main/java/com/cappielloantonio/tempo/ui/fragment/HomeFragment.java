package com.cappielloantonio.tempo.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentHomeBinding;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.fragment.pager.HomePager;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Objects;

@UnstableApi
public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding bind;
    private MainActivity activity;

    private MaterialToolbar materialToolbar;
    private AppBarLayout appBarLayout;
    private TabLayout tabLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        bind = FragmentHomeBinding.inflate(inflater, container, false);
        return bind.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAppBar();
        initHomePager();
    }

    @Override
    public void onStart() {
        super.onStart();

        activity.setBottomNavigationBarVisibility(true);
        activity.setBottomSheetVisibility(true);

        applyHomeTitle();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initAppBar() {
        applyHomeTitle();

        tabLayout = bind.homeTabLayout;

        android.widget.ImageButton libraryFilter = bind.getRoot().findViewById(R.id.home_library_filter);
        if (libraryFilter != null) libraryFilter.setOnClickListener(v -> showLibraryFilterDialog());
    }

    /**
     * Lets the user scope the whole app to one Navidrome library (music folder) or
     * "All". The choice is stored globally and the activity is recreated so every
     * tab reloads through the folder-scoped API. See Subsonic.getParams().
     */
    private void showLibraryFilterDialog() {
        new com.cappielloantonio.tempo.repository.DirectoryRepository().getMusicFolders()
                .observe(getViewLifecycleOwner(), folders -> {
                    if (bind == null || getContext() == null) return;
                    if (folders == null || folders.isEmpty()) {
                        android.widget.Toast.makeText(getContext(), R.string.home_library_filter_none, android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    java.util.List<String> ids = new java.util.ArrayList<>();
                    java.util.List<CharSequence> labels = new java.util.ArrayList<>();
                    ids.add(null); // All libraries
                    labels.add(getString(R.string.home_library_filter_all));
                    for (com.cappielloantonio.tempo.subsonic.models.MusicFolder folder : folders) {
                        ids.add(folder.getId());
                        labels.add(folder.getName());
                    }

                    String current = Preferences.getActiveMusicFolderId();
                    int checked = 0;
                    for (int i = 0; i < ids.size(); i++) {
                        if (Objects.equals(ids.get(i), current)) {
                            checked = i;
                            break;
                        }
                    }

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.home_library_filter_title)
                            .setSingleChoiceItems(labels.toArray(new CharSequence[0]), checked, (dialog, which) -> {
                                String selected = ids.get(which);
                                dialog.dismiss();
                                if (!Objects.equals(selected, Preferences.getActiveMusicFolderId())) {
                                    Preferences.setActiveMusicFolderId(selected);
                                    showLibraryRestartDialog();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
    }

    private void showLibraryRestartDialog() {
        if (getContext() == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.library_restart_title)
                .setMessage(R.string.library_restart_message)
                .setCancelable(false)
                .setPositiveButton(R.string.library_restart_now, (d, w) -> restartApp())
                .setNegativeButton(R.string.library_restart_later, null)
                .show();
    }

    /** Fully restarts the app so every screen reloads scoped to the new library. */
    private void restartApp() {
        if (getContext() == null) return;
        android.content.Context context = requireContext().getApplicationContext();
        android.content.Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        }
        Runtime.getRuntime().exit(0);
    }

    private void applyHomeTitle() {
        if (bind == null) return;
        TextView toolbarTitle = bind.getRoot().findViewById(R.id.toolbar_title);
        if (toolbarTitle == null) return;

        if (Preferences.isHomeScreenTitleEnabled()) {
            toolbarTitle.setVisibility(View.VISIBLE);
            toolbarTitle.setText(Preferences.getHomeScreenTitle());
            toolbarTitle.setTextSize(32);
        } else {
            toolbarTitle.setVisibility(View.GONE);
        }
    }

    private void initHomePager() {
        HomePager pager = new HomePager(this);

        pager.addFragment(new HomeTabMusicFragment(), getString(R.string.home_section_music), R.drawable.ic_home);

        if (Preferences.isPodcastSectionVisible())
            pager.addFragment(new HomeTabPodcastFragment(), getString(R.string.home_section_podcast), R.drawable.ic_graphic_eq);

        if (Preferences.isRadioSectionVisible())
            pager.addFragment(new HomeTabRadioFragment(), getString(R.string.home_section_radio), R.drawable.ic_play_for_work);

        bind.homeViewPager.setAdapter(pager);
        bind.homeViewPager.setOffscreenPageLimit(3);
        bind.homeViewPager.setUserInputEnabled(false);

        new TabLayoutMediator(tabLayout, bind.homeViewPager,
                (tab, position) -> {
                    tab.setText(pager.getPageTitle(position));
                    // tab.setIcon(pager.getPageIcon(position));
                }
        ).attach();

        tabLayout.setVisibility(Preferences.isPodcastSectionVisible() || Preferences.isRadioSectionVisible() ? View.VISIBLE : View.GONE);
    }
}
