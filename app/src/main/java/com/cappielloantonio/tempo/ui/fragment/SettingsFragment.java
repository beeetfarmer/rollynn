package com.cappielloantonio.tempo.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.helper.ThemeHelper;
import com.cappielloantonio.tempo.interfaces.DialogClickCallback;
import com.cappielloantonio.tempo.interfaces.ScanCallback;
import com.cappielloantonio.tempo.service.EqualizerManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.DeleteDownloadStorageDialog;
import com.cappielloantonio.tempo.ui.dialog.DownloadStorageDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredAlbumSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredArtistSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StreamingCacheStorageDialog;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.UIUtil;
import com.cappielloantonio.tempo.util.ExternalAudioReader;
import com.cappielloantonio.tempo.viewmodel.SettingViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class SettingsFragment extends PreferenceFragmentCompat implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    private static final String TAG = "SettingsFragment";

    private static final String DEFAULT_CATEGORY = "screen_appearance";

    private static final String[][] TAB_DEFINITIONS = {
            {"screen_appearance", "settings_tab_appearance"},
            {"screen_library", "settings_tab_library"},
            {"screen_playback", "settings_tab_playback"},
            {"screen_general", "settings_tab_general"},
    };

    private MainActivity activity;
    private SettingViewModel settingViewModel;

    private ActivityResultLauncher<Intent> equalizerResultLauncher;
    private ActivityResultLauncher<Intent> directoryPickerLauncher;

    private MediaService.LocalBinder mediaServiceBinder;
    private boolean isServiceBound = false;

    private String selectedCategory = DEFAULT_CATEGORY;
    private ChipGroup chipGroup;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        equalizerResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {}
        );

        if (!BuildConfig.FLAVOR.equals("tempus")) {
            PreferenceCategory githubUpdateCategory = findPreference("settings_github_update_category_key");
            if (githubUpdateCategory != null) {
                getPreferenceScreen().removePreference(githubUpdateCategory);
            }
        }

        directoryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri uri = data.getData();
                            if (uri != null) {
                                requireContext().getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                );

                                Preferences.setDownloadDirectoryUri(uri.toString());
                                ExternalAudioReader.refreshCache();
                                Toast.makeText(requireContext(), R.string.settings_download_folder_set, Toast.LENGTH_SHORT).show();
                                checkDownloadDirectory();
                            }
                        }
                    }
                });
    }

    private boolean isRoot() {
        return getArguments() == null || getArguments().getString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT) == null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        settingViewModel = new ViewModelProvider(requireActivity()).get(SettingViewModel.class);

        View prefView = super.onCreateView(inflater, container, savedInstanceState);

        if (!isRoot()) return prefView;

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(UIUtil.getThemeColor(requireContext(), android.R.attr.colorBackground));

        TextView title = new TextView(requireContext());
        title.setText(getString(R.string.settings_title));
        title.setTextSize(40);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(UIUtil.dpToPx(requireContext(), 24),
                         UIUtil.dpToPx(requireContext(), 32),
                         UIUtil.dpToPx(requireContext(), 24),
                         UIUtil.dpToPx(requireContext(), 8));
        title.setTextColor(UIUtil.getThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurface));
        root.addView(title);

        HorizontalScrollView scrollView = new HorizontalScrollView(requireContext());
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(UIUtil.dpToPx(requireContext(), 16), 0, UIUtil.dpToPx(requireContext(), 16), UIUtil.dpToPx(requireContext(), 8));

        chipGroup = new ChipGroup(requireContext());
        chipGroup.setSingleSelection(true);
        chipGroup.setSelectionRequired(true);
        chipGroup.setChipSpacingHorizontal(UIUtil.dpToPx(requireContext(), 8));

        for (String[] tab : TAB_DEFINITIONS) {
            Chip chip = new Chip(requireContext());
            chip.setTag(tab[0]);

            int stringResId = getResources().getIdentifier(tab[1], "string", requireContext().getPackageName());
            chip.setText(getString(stringResId));

            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);

            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    UIUtil.getThemeColor(requireContext(), com.google.android.material.R.attr.colorSurfaceVariant)));
            chip.setTextColor(UIUtil.getThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant));

            chip.setOnClickListener(v -> {
                String key = (String) v.getTag();
                if (!key.equals(selectedCategory)) {
                    switchCategory(key);
                }
            });

            chipGroup.addView(chip);
        }

        scrollView.addView(chipGroup);
        root.addView(scrollView);

        if (prefView != null) {
            root.addView(prefView);
        }

        selectChip(selectedCategory);

        return root;
    }

    private void selectChip(String categoryKey) {
        if (chipGroup == null) return;
        int primaryColor = UIUtil.getThemeColor(requireContext(), com.google.android.material.R.attr.colorPrimary);
        int onPrimaryColor = UIUtil.getThemeColor(requireContext(), com.google.android.material.R.attr.colorOnPrimary);
        int surfaceVariantColor = UIUtil.getThemeColor(requireContext(), com.google.android.material.R.attr.colorSurfaceVariant);
        int onSurfaceVariantColor = UIUtil.getThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant);
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            boolean selected = categoryKey.equals(chip.getTag());
            chip.setChecked(selected);
            if (selected) {
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(primaryColor));
                chip.setTextColor(onPrimaryColor);
            } else {
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(surfaceVariantColor));
                chip.setTextColor(onSurfaceVariantColor);
            }
        }
    }

    private void switchCategory(String rootKey) {
        selectedCategory = rootKey;
        setPreferencesFromResource(R.xml.global_preferences, rootKey);
        applyCustomLayouts(getPreferenceScreen());
        reinitializePreferences();
        selectChip(rootKey);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setDivider(null);
        setDividerHeight(0);

        if (getListView() != null) {
            getListView().setPadding(0, 0, 0, (int) getResources().getDimension(R.dimen.global_padding_bottom));
            getListView().setClipToPadding(false);
            getListView().addItemDecoration(new com.cappielloantonio.tempo.ui.view.SettingsItemDecoration(requireContext()));
        }

        initAppBar(view);
    }

    private void initAppBar(View view) {
        androidx.appcompat.widget.Toolbar toolbar = activity.findViewById(R.id.toolbar);

        if (toolbar != null) {
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(!isRoot());
                activity.getSupportActionBar().setDisplayShowHomeEnabled(!isRoot());
                activity.getSupportActionBar().setTitle(isRoot() ? "" : getPreferenceScreen().getTitle());
            }

            toolbar.setNavigationOnClickListener(v -> {
                if (!activity.navController.popBackStack()) {
                    activity.navController.navigateUp();
                }
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.setBottomNavigationBarVisibility(true);
        activity.setBottomSheetVisibility(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        reinitializePreferences();
        bindMediaService();
        actionAppEqualizer();
    }

    private void reinitializePreferences() {
        checkSystemEqualizer();
        checkCacheStorage();
        checkStorage();
        checkDownloadDirectory();

        setStreamingCacheSize();
        setAppLanguage();
        setVersion();
        setNetorkPingTimeoutBase();

        actionLogout();
        actionScan();
        actionSyncStarredAlbums();
        actionSyncStarredTracks();
        actionSyncStarredArtists();
        actionChangeStreamingCacheStorage();
        actionChangeDownloadStorage();
        actionSetDownloadDirectory();
        actionDeleteDownloadStorage();
        actionKeepScreenOn();
        actionAutoDownloadLyrics();
        actionLyricsRomanization();
        actionTranslationSettings();
        actionLastFmSettings();
        actionConfigureDock();
        actionConfigureMetadata();

        ListPreference themePreference = findPreference(Preferences.THEME);
        if (themePreference != null) {
            themePreference.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        String themeOption = (String) newValue;
                        ThemeHelper.applyTheme(themeOption);
                        return true;
                    });
        }
    }

    private void actionConfigureDock() {
        Preference pref = findPreference("configure_dock");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                NavController navController = NavHostFragment.findNavController(this);
                navController.navigate(R.id.dockConfigurationFragment);
                return true;
            });
        }
    }

    private void actionConfigureMetadata() {
        Preference pref = findPreference("configure_metadata");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                NavController navController = NavHostFragment.findNavController(this);
                navController.navigate(R.id.metadataConfigurationFragment);
                return true;
            });
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        activity.setBottomSheetVisibility(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (rootKey == null) {
            setPreferencesFromResource(R.xml.global_preferences, selectedCategory);
        } else {
            setPreferencesFromResource(R.xml.global_preferences, rootKey);
        }

        applyCustomLayouts(getPreferenceScreen());
    }

    private void applyCustomLayouts(androidx.preference.PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof PreferenceCategory) {
                pref.setLayoutResource(R.layout.preference_category_card);
            }
            if (pref instanceof androidx.preference.PreferenceGroup) {
                applyCustomLayouts((androidx.preference.PreferenceGroup) pref);
            }
        }
    }

    private void checkSystemEqualizer() {
        Preference equalizer = findPreference("system_equalizer");

        if (equalizer == null) return;

        Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);

        if ((intent.resolveActivity(requireActivity().getPackageManager()) != null)) {
            equalizer.setOnPreferenceClickListener(preference -> {
                equalizerResultLauncher.launch(intent);
                return true;
            });
        } else {
            equalizer.setVisible(false);
        }
    }

    private void checkCacheStorage() {
        Preference storage = findPreference("streaming_cache_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                storage.setSummary(Preferences.getStreamingCacheStoragePreference() == 0 ? R.string.download_storage_internal_dialog_negative_button : R.string.download_storage_external_dialog_positive_button);
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void checkStorage() {
        Preference storage = findPreference("download_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                int pref = Preferences.getDownloadStoragePreference();
                if (pref == 0) {
                    storage.setSummary(R.string.download_storage_internal_dialog_negative_button);
                } else if (pref == 1) {
                    storage.setSummary(R.string.download_storage_external_dialog_positive_button);
                } else {
                    storage.setSummary(R.string.download_storage_directory_dialog_neutral_button);
                }
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void checkDownloadDirectory() {
        Preference storage = findPreference("download_storage");
        Preference directory = findPreference("set_download_directory");

        if (directory == null) return;

        String current = Preferences.getDownloadDirectoryUri();
        if (current != null) {
            if (storage != null) storage.setVisible(false);
            directory.setVisible(true);
            directory.setIcon(R.drawable.ic_close);
            directory.setTitle(R.string.settings_clear_download_folder);
            directory.setSummary(current);
        } else {
            if (storage != null) storage.setVisible(true);
            if (Preferences.getDownloadStoragePreference() == 2) {
                directory.setVisible(true);
                directory.setIcon(R.drawable.ic_folder);
                directory.setTitle(R.string.settings_set_download_folder);
                directory.setSummary(R.string.settings_choose_download_folder);
            } else {
                directory.setVisible(false);
            }
        }
    }

    private void setNetorkPingTimeoutBase() {
        EditTextPreference networkPingTimeoutBase = findPreference("network_ping_timeout_base");

        if (networkPingTimeoutBase != null) {
            networkPingTimeoutBase.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            networkPingTimeoutBase.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setFilters(new InputFilter[]{ (source, start, end, dest, dstart, dend) -> {
                    for (int i = start; i < end; i++) {
                        if (!Character.isDigit(source.charAt(i))) {
                            return "";
                        }
                    }
                    return null;
            }});
        });

        networkPingTimeoutBase.setOnPreferenceChangeListener((preference, newValue) -> {
            String input = (String) newValue;
            return input != null && !input.isEmpty();
        });
        }
    }

    private void setStreamingCacheSize() {
        ListPreference streamingCachePreference = findPreference("streaming_cache_size");

        if (streamingCachePreference != null) {
            streamingCachePreference.setSummaryProvider(new Preference.SummaryProvider<ListPreference>() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull ListPreference preference) {
                    CharSequence entry = preference.getEntry();

                    if (entry == null) return null;

                    long currentSizeMb = DownloadUtil.getStreamingCacheSize(requireActivity()) / (1024 * 1024);

                    return getString(R.string.settings_summary_streaming_cache_size, entry, String.valueOf(currentSizeMb));
                }
            });
        }
    }

    private void setAppLanguage() {
        ListPreference localePref = (ListPreference) findPreference("language");
        if (localePref == null) return;

        Map<String, String> locales = UIUtil.getLangPreferenceDropdownEntries(requireContext());

        CharSequence[] entries = locales.keySet().toArray(new CharSequence[locales.size()]);
        CharSequence[] entryValues = locales.values().toArray(new CharSequence[locales.size()]);

        localePref.setEntries(entries);
        localePref.setEntryValues(entryValues);

        String value = localePref.getValue();
        if ("default".equals(value)) {
            localePref.setSummary(requireContext().getString(R.string.settings_system_language));
        } else {
            localePref.setSummary(Locale.forLanguageTag(value).getDisplayName());
        }

        localePref.setOnPreferenceChangeListener((preference, newValue) -> {
            if ("default".equals(newValue)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                preference.setSummary(requireContext().getString(R.string.settings_system_language));
            } else {
                LocaleListCompat appLocale = LocaleListCompat.forLanguageTags((String) newValue);
                AppCompatDelegate.setApplicationLocales(appLocale);
                preference.setSummary(Locale.forLanguageTag((String) newValue).getDisplayName());
            }
            return true;
        });
    }

    private void setVersion() {
        Preference pref = findPreference("version");
        if (pref != null) {
            pref.setSummary(BuildConfig.VERSION_NAME);
        }
    }

    private void actionLogout() {
        Preference pref = findPreference("logout");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                activity.quit();
                return true;
            });
        }
    }

    private void actionScan() {
        Preference pref = findPreference("scan_library");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                settingViewModel.launchScan(new ScanCallback() {
                    @Override
                    public void onError(Exception exception) {
                        Preference p = findPreference("scan_library");
                        if (p != null) p.setSummary(exception.getMessage());
                    }

                    @Override
                    public void onSuccess(boolean isScanning, long count) {
                        Preference p = findPreference("scan_library");
                        if (p != null) p.setSummary(getString(R.string.settings_scan_result, count));
                        if (isScanning) getScanStatus();
                    }
                });

                return true;
            });
        }
    }

    private void actionSyncStarredTracks() {
        Preference pref = findPreference("sync_starred_tracks_for_offline_use");
        if (pref != null) {
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Boolean) {
                    if ((Boolean) newValue) {
                        StarredSyncDialog dialog = new StarredSyncDialog(() -> {
                            ((SwitchPreference)preference).setChecked(false);
                        });
                        dialog.show(activity.getSupportFragmentManager(), null);
                        }
                }
                return true;
            });
        }
    }

    private void actionSyncStarredAlbums() {
        Preference pref = findPreference("sync_starred_albums_for_offline_use");
        if (pref != null) {
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Boolean) {
                    if ((Boolean) newValue) {
                        StarredAlbumSyncDialog dialog = new StarredAlbumSyncDialog(() -> {
                            ((SwitchPreference)preference).setChecked(false);
                        });
                        dialog.show(activity.getSupportFragmentManager(), null);
                    }
                }
                return true;
            });
        }
    }

    private void actionSyncStarredArtists() {
        Preference pref = findPreference("sync_starred_artists_for_offline_use");
        if (pref != null) {
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Boolean) {
                    if ((Boolean) newValue) {
                        StarredArtistSyncDialog dialog = new StarredArtistSyncDialog(() -> {
                            ((SwitchPreference)preference).setChecked(false);
                        });
                        dialog.show(activity.getSupportFragmentManager(), null);
                    }
                }
                return true;
            });
        }
    }

    private void actionChangeStreamingCacheStorage() {
        Preference pref = findPreference("streaming_cache_storage");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                StreamingCacheStorageDialog dialog = new StreamingCacheStorageDialog(new DialogClickCallback() {
                    @Override
                    public void onPositiveClick() {
                        Preference p = findPreference("streaming_cache_storage");
                        if (p != null) p.setSummary(R.string.streaming_cache_storage_external_dialog_positive_button);
                    }

                    @Override
                    public void onNegativeClick() {
                        Preference p = findPreference("streaming_cache_storage");
                        if (p != null) p.setSummary(R.string.streaming_cache_storage_internal_dialog_negative_button);
                    }
                });
                dialog.show(activity.getSupportFragmentManager(), null);
                return true;
            });
        }
    }

    private void actionChangeDownloadStorage() {
        Preference pref = findPreference("download_storage");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                DownloadStorageDialog dialog = new DownloadStorageDialog(new DialogClickCallback() {
                    @Override
                    public void onPositiveClick() {
                        Preference p = findPreference("download_storage");
                        if (p != null) p.setSummary(R.string.download_storage_external_dialog_positive_button);
                        checkDownloadDirectory();
                    }

                    @Override
                    public void onNegativeClick() {
                        Preference p = findPreference("download_storage");
                        if (p != null) p.setSummary(R.string.download_storage_internal_dialog_negative_button);
                        checkDownloadDirectory();
                    }

                    @Override
                    public void onNeutralClick() {
                        Preference p = findPreference("download_storage");
                        if (p != null) p.setSummary(R.string.download_storage_directory_dialog_neutral_button);
                        checkDownloadDirectory();
                    }
                });
                dialog.show(activity.getSupportFragmentManager(), null);
                return true;
            });
        }
    }

    private void actionSetDownloadDirectory() {
        Preference pref = findPreference("set_download_directory");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                String current = Preferences.getDownloadDirectoryUri();

                if (current != null) {
                    Preferences.setDownloadDirectoryUri(null);
                    Preferences.setDownloadStoragePreference(0);
                    ExternalAudioReader.refreshCache();
                    Toast.makeText(requireContext(), R.string.settings_download_folder_cleared, Toast.LENGTH_SHORT).show();
                    checkStorage();
                    checkDownloadDirectory();
                } else {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    directoryPickerLauncher.launch(intent);
                }
                return true;
            });
        }
    }

    private void actionDeleteDownloadStorage() {
        Preference pref = findPreference("delete_download_storage");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                DeleteDownloadStorageDialog dialog = new DeleteDownloadStorageDialog();
                dialog.show(activity.getSupportFragmentManager(), null);
                return true;
            });
        }
    }

    private void actionAutoDownloadLyrics() {
        SwitchPreference preference = findPreference("auto_download_lyrics");
        if (preference != null) {
            preference.setChecked(Preferences.isAutoDownloadLyricsEnabled());
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof Boolean) {
                    Preferences.setAutoDownloadLyricsEnabled((Boolean) newValue);
                }
                return true;
            });
        }
    }

    private void actionLyricsRomanization() {
        SwitchPreference preference = findPreference("lyrics_romanization");
        if (preference != null) {
            preference.setChecked(Preferences.isLyricsRomanizationEnabled());
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue instanceof Boolean) {
                    Preferences.setLyricsRomanizationEnabled((Boolean) newValue);
                }
                return true;
            });
        }
    }

    private void actionTranslationSettings() {
        EditTextPreference apiKeyPref = findPreference("translation_api_key");
        if (apiKeyPref != null) {
            apiKeyPref.setSummaryProvider(null);
            String currentKey = Preferences.getTranslationApiKey();
            if (currentKey != null && !currentKey.isEmpty()) {
                String masked = currentKey.substring(0, Math.min(4, currentKey.length())) + "****";
                apiKeyPref.setSummary(masked);
            }
            apiKeyPref.setOnPreferenceChangeListener((pref, newValue) -> {
                String key = (String) newValue;
                Preferences.setTranslationApiKey(key);
                if (key != null && !key.isEmpty()) {
                    String masked = key.substring(0, Math.min(4, key.length())) + "****";
                    pref.setSummary(masked);
                } else {
                    pref.setSummary(R.string.settings_translation_api_key_summary);
                }
                return false;
            });
        }

        EditTextPreference modelPref = findPreference("translation_model");
        if (modelPref != null) {
            modelPref.setOnPreferenceChangeListener((pref, newValue) -> {
                Preferences.setTranslationModel((String) newValue);
                return true;
            });
        }

        EditTextPreference urlPref = findPreference("translation_api_url");
        if (urlPref != null) {
            urlPref.setOnPreferenceChangeListener((pref, newValue) -> {
                Preferences.setTranslationApiUrl((String) newValue);
                return true;
            });
        }

        ListPreference providerPref = findPreference("translation_provider");
        if (providerPref != null) {
            providerPref.setOnPreferenceChangeListener((pref, newValue) -> {
                Preferences.setTranslationProvider((String) newValue);
                updateTranslationSettingsVisibility((String) newValue);
                return true;
            });
            updateTranslationSettingsVisibility(Preferences.getTranslationProvider());
        }

        ListPreference langPref = findPreference("translation_target_language");
        if (langPref != null) {
            langPref.setOnPreferenceChangeListener((pref, newValue) -> {
                Preferences.setTranslationTargetLanguage((String) newValue);
                return true;
            });
        }
    }

    private void actionLastFmSettings() {
        EditTextPreference apiKeyPref = findPreference("last_fm_api_key");
        if (apiKeyPref != null) {
            apiKeyPref.setSummaryProvider(null);
            String currentKey = Preferences.getLastFmApiKey();
            if (currentKey != null && !currentKey.isEmpty()) {
                String masked = currentKey.substring(0, Math.min(4, currentKey.length())) + "****";
                apiKeyPref.setSummary(masked);
            }
            apiKeyPref.setOnPreferenceChangeListener((pref, newValue) -> {
                String key = (String) newValue;
                Preferences.setLastFmApiKey(key);
                if (key != null && !key.isEmpty()) {
                    String masked = key.substring(0, Math.min(4, key.length())) + "****";
                    pref.setSummary(masked);
                } else {
                    pref.setSummary(null);
                }
                return false;
            });
        }
    }

    private void updateTranslationSettingsVisibility(String provider) {
        EditTextPreference modelPref = findPreference("translation_model");
        EditTextPreference urlPref = findPreference("translation_api_url");

        boolean isAI = !"deepl".equals(provider);
        if (modelPref != null) modelPref.setVisible(isAI);
        if (urlPref != null) urlPref.setVisible(isAI);
    }

    private void getScanStatus() {
        Preference pref = findPreference("scan_library");
        if (pref != null) {
            settingViewModel.getScanStatus(new ScanCallback() {
                @Override
                public void onError(Exception exception) {
                    Preference p = findPreference("scan_library");
                    if (p != null) p.setSummary(exception.getMessage());
                }

                @Override
                public void onSuccess(boolean isScanning, long count) {
                    Preference p = findPreference("scan_library");
                    if (p != null) p.setSummary(getString(R.string.settings_scan_result, count));
                    if (isScanning) getScanStatus();
                }
            });
        }
    }

    private void actionKeepScreenOn() {
        Preference pref = findPreference("always_on_display");
        if (pref != null) {
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Boolean) {
                    if ((Boolean) newValue) {
                        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
                return true;
            });
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediaServiceBinder = (MediaService.LocalBinder) service;
            isServiceBound = true;
            checkEqualizerBands();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaServiceBinder = null;
            isServiceBound = false;
        }
    };

    private void bindMediaService() {
        Intent intent = new Intent(requireActivity(), MediaService.class);
        intent.setAction(MediaService.ACTION_BIND_EQUALIZER);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isServiceBound = true;
    }

    private void checkEqualizerBands() {
        if (mediaServiceBinder != null) {
            EqualizerManager eqManager = mediaServiceBinder.getEqualizerManager();
            short numBands = eqManager.getNumberOfBands();
            Preference appEqualizer = findPreference("app_equalizer");
            if (appEqualizer != null) {
                appEqualizer.setVisible(numBands > 0);
            }
        }
    }

    private void actionAppEqualizer() {
        Preference appEqualizer = findPreference("app_equalizer");
        if (appEqualizer != null) {
            appEqualizer.setOnPreferenceClickListener(preference -> {
                NavController navController = NavHostFragment.findNavController(this);
                NavOptions navOptions = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.equalizerFragment, true)
                        .build();
                activity.setBottomNavigationBarVisibility(true);
                activity.setBottomSheetVisibility(true);
                navController.navigate(R.id.equalizerFragment, null, navOptions);
                return true;
            });
        }
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        String key = pref.getKey();
        for (String[] tab : TAB_DEFINITIONS) {
            if (tab[0].equals(key)) {
                return false;
            }
        }

        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, key);

        NavController navController = NavHostFragment.findNavController(this);
        navController.navigate(R.id.settingsFragment, args);
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isServiceBound) {
            requireActivity().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}
