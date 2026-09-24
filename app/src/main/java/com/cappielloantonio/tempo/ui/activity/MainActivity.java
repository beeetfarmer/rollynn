package com.cappielloantonio.tempo.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.broadcast.receiver.ConnectivityStatusBroadcastReceiver;
import com.cappielloantonio.tempo.databinding.ActivityMainBinding;
import com.cappielloantonio.tempo.github.utils.UpdateUtil;
import com.cappielloantonio.tempo.subsonic.api.navidrome.NavidromeClient;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.ui.activity.base.BaseActivity;
import com.cappielloantonio.tempo.ui.dialog.ConnectionAlertDialog;
import com.cappielloantonio.tempo.ui.dialog.GithubTempoUpdateDialog;
import com.cappielloantonio.tempo.ui.dialog.ServerUnreachableDialog;
import com.cappielloantonio.tempo.ui.fragment.PlayerBottomSheetFragment;
import com.cappielloantonio.tempo.util.AssetLinkNavigator;
import com.cappielloantonio.tempo.util.AssetLinkUtil;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.UIUtil;
import com.cappielloantonio.tempo.viewmodel.MainViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.color.DynamicColors;
import com.google.common.util.concurrent.MoreExecutors;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.cappielloantonio.tempo.service.PlaylistSyncWorker;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

@UnstableApi
public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivityLogs";

    public ActivityMainBinding bind;
    private MainViewModel mainViewModel;

    private FragmentManager fragmentManager;
    private NavHostFragment navHostFragment;
    public NavController navController;
    private BottomSheetBehavior bottomSheetBehavior;
    private boolean isLandscape = false;
    private boolean playerBarsExpanded = false;
    private boolean hasPlayerDynamicBarColors = false;
    private int playerStatusBarColor;
    private int playerNavBarColor;
    private AssetLinkNavigator assetLinkNavigator;
    private AssetLinkUtil.AssetLink pendingAssetLink;

    private ViewGroup dockContainer;
    ConnectivityStatusBroadcastReceiver connectivityStatusBroadcastReceiver;
    private Intent pendingDownloadPlaybackIntent;
    private final MutableLiveData<Boolean> serverReachable = new MutableLiveData<>(true);

    public MutableLiveData<Boolean> getServerReachable() {
        return serverReachable;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        
        int accentColor = Preferences.getAccentColor();
        if (accentColor == -1) accentColor = 0xFF6750A4;
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this,
            new com.google.android.material.color.DynamicColorsOptions.Builder()
                .setContentBasedSource(android.graphics.Bitmap.createBitmap(new int[]{accentColor}, 1, 1, android.graphics.Bitmap.Config.ARGB_8888))
                .build());

        super.onCreate(savedInstanceState);

        bind = ActivityMainBinding.inflate(getLayoutInflater());
        View view = bind.getRoot();
        setContentView(view);

        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        assetLinkNavigator = new AssetLinkNavigator(this);

        connectivityStatusBroadcastReceiver = new ConnectivityStatusBroadcastReceiver(this);
        connectivityStatusReceiverManager(true);

        isLandscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        init();
        checkConnectionType();
        getOpenSubsonicExtensions();
        checkTempoUpdate();

        maybeSchedulePlaybackIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        pingServer();
        initService();
        consumePendingPlaybackIntent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomSheetBehavior != null && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            applyPlayerSystemBarColors(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectivityStatusReceiverManager(false);
        bind = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        maybeSchedulePlaybackIntent(intent);
        consumePendingPlaybackIntent();
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED)
            collapseBottomSheetDelayed();
        else
            super.onBackPressed();
    }

    public void init() {
        fragmentManager = getSupportFragmentManager();

        initBottomSheet();
        initNavigation();

        if (Preferences.getPassword() != null || (Preferences.getToken() != null && Preferences.getSalt() != null)) {
            goFromLogin();
        } else {
            goToLogin();
        }
    }

    // BOTTOM SHEET/NAVIGATION
    private void initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.player_bottom_sheet));
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback);
        fragmentManager.beginTransaction().replace(R.id.player_bottom_sheet, new PlayerBottomSheetFragment(), "PlayerBottomSheet").commit();

        checkBottomSheetAfterStateChanged();
    }

    public void setBottomSheetInPeek(Boolean isVisible) {
        if (isVisible) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    public void setBottomSheetVisibility(boolean visibility) {
        if (visibility) {
            findViewById(R.id.player_bottom_sheet).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.player_bottom_sheet).setVisibility(View.GONE);
        }
    }

    private void checkBottomSheetAfterStateChanged() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> setBottomSheetInPeek(mainViewModel.isQueueLoaded());
        handler.postDelayed(runnable, 100);
    }

    public void collapseBottomSheetDelayed() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        handler.postDelayed(runnable, 100);
    }

    public void expandBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public void setBottomSheetDraggableState(Boolean isDraggable) {
        bottomSheetBehavior.setDraggable(isDraggable);
    }

    private final BottomSheetBehavior.BottomSheetCallback bottomSheetCallback =
            new BottomSheetBehavior.BottomSheetCallback() {
                int navigationHeight;

                @Override
                public void onStateChanged(@NonNull View view, int state) {
                    PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");

                    switch (state) {
                        case BottomSheetBehavior.STATE_HIDDEN:
                            resetMusicSession();
                            applyPlayerSystemBarColors(false);
                            break;
                        case BottomSheetBehavior.STATE_COLLAPSED:
                            if (playerBottomSheetFragment != null) {
                                playerBottomSheetFragment.goBackToFirstPage();
                                playerBottomSheetFragment.setBodyVisibility(false);
                            }
                            if (bind.offlineModeTextView.getTag() != null) {
                                bind.offlineModeTextView.setVisibility(View.VISIBLE);
                            }
                            applyPlayerSystemBarColors(false);
                            break;
                        case BottomSheetBehavior.STATE_EXPANDED:
                            if (playerBottomSheetFragment != null) {
                                playerBottomSheetFragment.setBodyVisibility(true);
                            }
                            if (bind.offlineModeTextView.getVisibility() == View.VISIBLE) {
                                bind.offlineModeTextView.setTag(true);
                                bind.offlineModeTextView.setVisibility(View.GONE);
                            }
                            applyPlayerSystemBarColors(true);
                            break;
                        case BottomSheetBehavior.STATE_SETTLING:
                        case BottomSheetBehavior.STATE_DRAGGING:
                        case BottomSheetBehavior.STATE_HALF_EXPANDED:
                            break;
                    }
                }

                @Override
                public void onSlide(@NonNull View view, float slideOffset) {
                    animateBottomSheet(slideOffset);
                    if (!isLandscape) {
                         animateBottomNavigation(slideOffset, navigationHeight);
                    }

                    boolean usePlayerColor = slideOffset >= 0.99f;
                    if (usePlayerColor != playerBarsExpanded) {
                        applyPlayerSystemBarColors(usePlayerColor);
                    }
            }
    };

    private void applyPlayerSystemBarColors(boolean playerExpanded) {
        playerBarsExpanded = playerExpanded;
        if (playerExpanded) {
            if (hasPlayerDynamicBarColors) {
                applySystemBarColors(playerStatusBarColor, playerNavBarColor);
            } else {
                applySystemBarColors(UIUtil.getPlayerBackgroundColor(this));
            }
        } else {
            applySystemBarColors();
        }
    }

    /**
     * Colours the status bar with the player gradient's top colour and the
     * navigation bar with its bottom colour, so neither shows through over the
     * album-art background. Applied immediately when the player is expanded.
     */
    public void setPlayerDynamicBarColors(int statusColor, int navColor) {
        playerStatusBarColor = statusColor;
        playerNavBarColor = navColor;
        hasPlayerDynamicBarColors = true;
        if (playerBarsExpanded) {
            applySystemBarColors(statusColor, navColor);
        }
    }

    public void clearPlayerDynamicBarColors() {
        hasPlayerDynamicBarColors = false;
        if (playerBarsExpanded) {
            applySystemBarColors(UIUtil.getPlayerBackgroundColor(this));
        }
    }

    /**
     * Transparent system bars so a full-bleed header (the artist page artwork)
     * shows through them. Icons are forced light since the artwork behind is dark.
     */
    public void applyImmersiveSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().getDecorView().requestApplyInsets();
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        androidx.core.view.WindowInsetsControllerCompat controller =
                new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        // Edge-to-edge drops the bottom sheet under the nav bar, so raise its peek
        // by the nav-bar inset to keep the mini player the same distance above the
        // dock as on normal (inset) pages.
        setBottomSheetPeekForImmersive(true);
    }

    private void setBottomSheetPeekForImmersive(boolean immersive) {
        if (bottomSheetBehavior == null) return;
        int basePeek = getResources().getDimensionPixelSize(R.dimen.bottom_sheet_behavior_peek_height);
        int navInset = 0;
        if (immersive) {
            androidx.core.view.WindowInsetsCompat insets =
                    androidx.core.view.ViewCompat.getRootWindowInsets(getWindow().getDecorView());
            if (insets != null) {
                navInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            }
        }
        bottomSheetBehavior.setPeekHeight(basePeek + navInset, false);
    }

    /**
     * On the immersive artist page, tints the status bar with the surface colour
     * once the header has collapsed (so no artwork shows behind it) and keeps it
     * transparent while expanded. Only the colour changes — the window stays
     * edge-to-edge, so the artwork still bleeds under the bar when expanded.
     */
    public void setArtistHeaderCollapsed(boolean collapsed) {
        int color = collapsed
                ? UIUtil.getThemeColor(this, com.google.android.material.R.attr.colorSurface)
                : android.graphics.Color.TRANSPARENT;
        getWindow().setStatusBarColor(color);
        androidx.core.view.WindowInsetsControllerCompat controller =
                new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(collapsed
                && androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5);
    }

    /** Restores the normal opaque surface-coloured system bars. */
    public void restoreDefaultSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().getDecorView().requestApplyInsets();
        setBottomSheetPeekForImmersive(false);
        if (playerBarsExpanded) {
            applyPlayerSystemBarColors(true);
        } else {
            applySystemBarColors();
        }
    }

    private void animateBottomSheet(float slideOffset) {
        PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");
        if (playerBottomSheetFragment != null) {
            float condensedSlideOffset = Math.max(0.0f, Math.min(0.2f, slideOffset - 0.2f)) / 0.2f;
            playerBottomSheetFragment.getPlayerHeader().setAlpha(1 - condensedSlideOffset);
            playerBottomSheetFragment.getPlayerHeader().setVisibility(condensedSlideOffset > 0.99 ? View.GONE : View.VISIBLE);
            
            // Show body during slide if expanding, and cross-fade it with the mini player so the
            // full-player album art doesn't linger fully opaque while dragging down.
            if (slideOffset > 0.01) {
                playerBottomSheetFragment.setBodyVisibility(true);
                playerBottomSheetFragment.setBodyAlpha(condensedSlideOffset);
            } else if (slideOffset <= 0) {
                playerBottomSheetFragment.setBodyVisibility(false);
            }
        }
    }

    private void animateBottomNavigation(float slideOffset, int navigationHeight) {
        if (slideOffset < 0) return;

        if (navigationHeight == 0) {
            navigationHeight = bind.navigationDock.dockCard.getHeight() + 200;
        }

        float slideY = navigationHeight * slideOffset;

        bind.navigationDock.dockCard.setTranslationY(slideY);
    }

    private void initNavigation() {
        dockContainer = bind.navigationDock.dockItemsContainer;
        navHostFragment = (NavHostFragment) fragmentManager.findFragmentById(R.id.nav_host_fragment);
        navController = Objects.requireNonNull(navHostFragment).getNavController();

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED && (
                    destination.getId() == R.id.homeFragment ||
                            destination.getId() == R.id.libraryFragment ||
                            destination.getId() == R.id.downloadFragment ||
                            destination.getId() == R.id.albumCatalogueFragment ||
                            destination.getId() == R.id.playlistCatalogueFragment ||
                            destination.getId() == R.id.searchFragment)
            ) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
            updateDockActiveState(destination.getId());
        });

        bind.navigationDock.dockCard.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            if (width > 0) {
                PlayerBottomSheetFragment fragment = (PlayerBottomSheetFragment) getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");
                if (fragment != null) {
                    if (isLandscape) {
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int sideMargin = UIUtil.dpToPx(this, 24);
                        int minWidth = UIUtil.dpToPx(this, 420);
                        int availableWidth = screenWidth - width - (sideMargin * 2);
                        fragment.setMiniPlayerWidth(Math.max(minWidth, availableWidth));
                    } else {
                        // Independent of dock size: a consistently wide mini player (screen minus side margins).
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int sideMargin = UIUtil.dpToPx(this, 28);
                        fragment.setMiniPlayerWidth(screenWidth - sideMargin * 2);
                    }
                }
            }
        });

        // Keep the dock clear of the navigation bar. On edge-to-edge pages (the
        // artist page) the window reaches the screen bottom, so add the nav-bar
        // inset to the dock's margin; on normal pages the inset is 0.
        int baseDockMargin = UIUtil.dpToPx(this, 8);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bind.navigationDock.getRoot(), (v, insets) -> {
            int navBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            int target = baseDockMargin + navBottom;
            if (lp.bottomMargin != target) {
                lp.bottomMargin = target;
                v.setLayoutParams(lp);
            }
            return insets;
        });

        setupDock();
    }

    private void setupDock() {
        dockContainer.removeAllViews();
        if (dockContainer instanceof LinearLayout) {
            ((LinearLayout) dockContainer).setOrientation(isLandscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        }
        List<String> items = new java.util.ArrayList<>(Preferences.getDockItems());

        // Home and Search are always in the dock; everything else (Settings included) is optional
        // and falls into the More menu when not enabled.
        if (!items.contains(Constants.DOCK_ITEM_HOME)) items.add(0, Constants.DOCK_ITEM_HOME);
        if (!items.contains(Constants.DOCK_ITEM_SEARCH)) items.add(Constants.DOCK_ITEM_SEARCH);

        // Rewind only exists while its tab is enabled in settings.
        if (!Preferences.isRewindTabEnabled()) items.remove(Constants.DOCK_ITEM_REWIND);

        // At most 4 items in the dock; everything else lives behind the More button.
        if (items.size() > 4) items = new java.util.ArrayList<>(items.subList(0, 4));
        final List<String> dockItems = items;

        for (String item : dockItems) {
            View dockItemView = getLayoutInflater().inflate(R.layout.item_dock_nav, dockContainer, false);
            ImageView icon = dockItemView.findViewById(R.id.dock_item_icon);
            TextView label = dockItemView.findViewById(R.id.dock_item_label);

            int fragmentId = getFragmentId(item);
            icon.setImageResource(getDockIcon(item));
            label.setText(getDockLabel(item));

            dockItemView.setOnClickListener(v -> {
                if (navController.getCurrentDestination() == null || navController.getCurrentDestination().getId() != fragmentId) {
                    navController.navigate(fragmentId);
                }
            });
            dockItemView.setTag(fragmentId);
            dockContainer.addView(dockItemView);
        }

        // Always-present More button opens every section not shown in the dock.
        View moreView = getLayoutInflater().inflate(R.layout.item_dock_nav, dockContainer, false);
        ((ImageView) moreView.findViewById(R.id.dock_item_icon)).setImageResource(R.drawable.ic_more_vert);
        ((TextView) moreView.findViewById(R.id.dock_item_label)).setText(R.string.dock_more);
        moreView.setOnClickListener(v -> showMoreMenu(dockItems));
        dockContainer.addView(moreView);

        updateDockActiveState(navController.getCurrentDestination() != null ? navController.getCurrentDestination().getId() : -1);
    }

    private void showMoreMenu(List<String> dockItems) {
        String[] allSections = {
                Constants.DOCK_ITEM_HOME, Constants.DOCK_ITEM_LIBRARY, Constants.DOCK_ITEM_ALBUMS,
                Constants.DOCK_ITEM_ARTISTS, Constants.DOCK_ITEM_REWIND, Constants.DOCK_ITEM_PLAYLISTS,
                Constants.DOCK_ITEM_DOWNLOADS, Constants.DOCK_ITEM_SEARCH, Constants.DOCK_ITEM_SETTINGS
        };

        com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        container.setPadding(0, pad, 0, pad);

        for (String item : allSections) {
            if (dockItems.contains(item)) continue;
            if (item.equals(Constants.DOCK_ITEM_REWIND) && !Preferences.isRewindTabEnabled()) continue;
            View row = getLayoutInflater().inflate(R.layout.item_more_menu, container, false);
            ((ImageView) row.findViewById(R.id.more_item_icon)).setImageResource(getDockIcon(item));
            ((TextView) row.findViewById(R.id.more_item_label)).setText(getDockLabel(item));
            int fragmentId = getFragmentId(item);
            row.setOnClickListener(v -> {
                if (navController.getCurrentDestination() == null || navController.getCurrentDestination().getId() != fragmentId) {
                    navController.navigate(fragmentId);
                }
                sheet.dismiss();
            });
            container.addView(row);
        }

        sheet.setContentView(container);
        sheet.show();
    }

    private String getDockLabel(String item) {
        switch (item) {
            case Constants.DOCK_ITEM_LIBRARY: return "Library";
            case Constants.DOCK_ITEM_DOWNLOADS: return "Downloads";
            case Constants.DOCK_ITEM_ALBUMS: return "Albums";
            case Constants.DOCK_ITEM_ARTISTS: return "Artists";
            case Constants.DOCK_ITEM_REWIND: return "Rewind";
            case Constants.DOCK_ITEM_PLAYLISTS: return "Playlists";
            case Constants.DOCK_ITEM_SEARCH: return "Search";
            case Constants.DOCK_ITEM_SETTINGS: return "Settings";
            default: return "Home";
        }
    }

    private int getFragmentId(String item) {
        switch (item) {
            case Constants.DOCK_ITEM_LIBRARY: return R.id.libraryFragment;
            case Constants.DOCK_ITEM_DOWNLOADS: return R.id.downloadFragment;
            case Constants.DOCK_ITEM_ALBUMS: return R.id.albumCatalogueFragment;
            case Constants.DOCK_ITEM_ARTISTS: return R.id.artistCatalogueFragment;
            case Constants.DOCK_ITEM_REWIND: return R.id.rewindFragment;
            case Constants.DOCK_ITEM_PLAYLISTS: return R.id.playlistCatalogueFragment;
            case Constants.DOCK_ITEM_SEARCH: return R.id.searchFragment;
            case Constants.DOCK_ITEM_SETTINGS: return R.id.settingsFragment;
            default: return R.id.homeFragment;
        }
    }

    private int getDockIcon(String item) {
        switch (item) {
            case Constants.DOCK_ITEM_LIBRARY: return R.drawable.ic_graphic_eq;
            case Constants.DOCK_ITEM_DOWNLOADS: return R.drawable.ic_file_download;
            case Constants.DOCK_ITEM_ALBUMS: return R.drawable.ic_album;
            case Constants.DOCK_ITEM_ARTISTS: return R.drawable.ic_artist;
            case Constants.DOCK_ITEM_REWIND: return R.drawable.ic_history;
            case Constants.DOCK_ITEM_PLAYLISTS: return R.drawable.ic_playlist_add;
            case Constants.DOCK_ITEM_SEARCH: return R.drawable.ic_search;
            case Constants.DOCK_ITEM_SETTINGS: return R.drawable.ic_settings;
            default: return R.drawable.ic_home;
        }
    }

    private void updateDockActiveState(int activeId) {
        int colorOnPrimaryContainer = UIUtil.getThemeColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer);
        int colorOnSurfaceVariant = UIUtil.getThemeColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int colorOnSurface = UIUtil.getThemeColor(this, com.google.android.material.R.attr.colorOnSurface);

        for (int i = 0; i < dockContainer.getChildCount(); i++) {
            View child = dockContainer.getChildAt(i);
            View root = child.findViewById(R.id.dock_item_root);
            ImageView icon = child.findViewById(R.id.dock_item_icon);
            TextView label = child.findViewById(R.id.dock_item_label);
            
            if (child.getTag() instanceof Integer && (Integer)child.getTag() == activeId) {
                root.setBackgroundResource(R.drawable.bg_dock_item_selected);
                icon.setAlpha(1.0f);
                label.setAlpha(1.0f);
                icon.setColorFilter(colorOnPrimaryContainer);
                label.setTextColor(colorOnPrimaryContainer);
            } else {
                root.setBackground(null);
                icon.setAlpha(0.6f);
                label.setAlpha(0.6f);
                icon.setColorFilter(colorOnSurfaceVariant);
                label.setTextColor(colorOnSurface);
            }
        }
    }

    public void setBottomNavigationBarVisibility(boolean visibility) {
        bind.navigationDock.dockCard.setVisibility(visibility ? View.VISIBLE : View.GONE);
    }

    private void initService() {
        MediaManager.check(getMediaBrowserListenableFuture());

        getMediaBrowserListenableFuture().addListener(() -> {
            try {
                getMediaBrowserListenableFuture().get().addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        if (isPlaying && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                            setBottomSheetInPeek(true);
                        }
                    }
                });
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void goToLogin() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        setBottomNavigationBarVisibility(false);
        setBottomSheetVisibility(false);

        if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.landingFragment) {
            navController.navigate(R.id.action_landingFragment_to_loginFragment);
        } else if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.settingsFragment) {
            navController.navigate(R.id.action_settingsFragment_to_loginFragment);
        } else if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.homeFragment) {
            navController.navigate(R.id.action_homeFragment_to_loginFragment);
        }
    }

    private void goToHome() {
        setBottomNavigationBarVisibility(true);

        if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.landingFragment) {
            navController.navigate(R.id.action_landingFragment_to_homeFragment);
        } else if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.loginFragment) {
            navController.navigate(R.id.action_loginFragment_to_homeFragment);
        }
    }

    public void goFromLogin() {
        setBottomSheetInPeek(mainViewModel.isQueueLoaded());
        goToHome();
        consumePendingAssetLink();
    }

    public void openAssetLink(@NonNull AssetLinkUtil.AssetLink assetLink) {
        openAssetLink(assetLink, true);
    }

    public void openAssetLink(@NonNull AssetLinkUtil.AssetLink assetLink, boolean collapsePlayer) {
        if (!isUserAuthenticated()) {
            pendingAssetLink = assetLink;
            return;
        }
        if (collapsePlayer) {
            setBottomSheetInPeek(true);
        }
        if (assetLinkNavigator != null) {
            assetLinkNavigator.open(assetLink);
        }
    }

    public void quit() {
        resetUserSession();
        resetMusicSession();
        resetViewModel();
        goToLogin();
    }

    private void resetUserSession() {
        Preferences.setServerId(null);
        Preferences.setSalt(null);
        Preferences.setToken(null);
        Preferences.setPassword(null);
        Preferences.setServer(null);
        Preferences.setLocalAddress(null);
        Preferences.setUser(null);

        NavidromeClient.clearCredentials();

        Preferences.setOpenSubsonic(false);
        Preferences.setPlaybackSpeed(1.0f);
        Preferences.setSkipSilenceMode(false);
        Preferences.setDataSavingMode(false);
        Preferences.setStarredSyncEnabled(false);
        Preferences.setStarredAlbumsSyncEnabled(false);
    }

    private void resetMusicSession() {
        MediaManager.reset(getMediaBrowserListenableFuture());
    }

    private void hideMusicSession() {
        MediaManager.hide(getMediaBrowserListenableFuture());
    }

    private void resetViewModel() {
        this.getViewModelStore().clear();
    }

    // CONNECTION
    private void connectivityStatusReceiverManager(boolean isActive) {
        if (isActive) {
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(connectivityStatusBroadcastReceiver, filter);
        } else {
            unregisterReceiver(connectivityStatusBroadcastReceiver);
        }
    }

    private void pingServer() {
        if (Preferences.getServer() == null) return;
        if (Preferences.getToken() == null && Preferences.getPassword() == null) return;

        if (Preferences.isInUseServerAddressLocal()) {
            mainViewModel.ping().observe(this, subsonicResponse -> {
                if (subsonicResponse == null) {
                    NetworkUtil.setServerReachable(false);
                    serverReachable.setValue(false);
                    Preferences.setServerSwitchableTimer();
                    Preferences.switchInUseServerAddress();
                    App.refreshSubsonicClient();
                    pingServer();
                    resetView();
                } else {
                    NetworkUtil.setServerReachable(true);
                    serverReachable.setValue(true);
                    Preferences.setOpenSubsonic(subsonicResponse.getOpenSubsonic() != null && subsonicResponse.getOpenSubsonic());
                    com.cappielloantonio.tempo.glide.CoverArtCache.cacheAllDownloads();
                    schedulePlaylistSync();
                }
            });
        } else {
            if (Preferences.isServerSwitchable()) {
                NetworkUtil.setServerReachable(false);
                serverReachable.setValue(false);
                Preferences.setServerSwitchableTimer();
                Preferences.switchInUseServerAddress();
                App.refreshSubsonicClient();
                pingServer();
                resetView();
            } else {
                mainViewModel.ping().observe(this, subsonicResponse -> {
                    if (subsonicResponse == null) {
                        NetworkUtil.setServerReachable(false);
                        serverReachable.setValue(false);
                        if (Preferences.showServerUnreachableDialog() && getSupportFragmentManager().findFragmentByTag("ServerUnreachableDialog") == null) {
                            ServerUnreachableDialog dialog = new ServerUnreachableDialog();
                            dialog.show(getSupportFragmentManager(), "ServerUnreachableDialog");
                        }
                    } else {
                        NetworkUtil.setServerReachable(true);
                        serverReachable.setValue(true);
                        Preferences.setOpenSubsonic(subsonicResponse.getOpenSubsonic() != null && subsonicResponse.getOpenSubsonic());
                        com.cappielloantonio.tempo.glide.CoverArtCache.cacheAllDownloads();
                        schedulePlaylistSync();
                    }
                });
            }
        }
    }

    private void schedulePlaylistSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                PlaylistSyncWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "playlist_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest);
    }

    private void resetView() {
        resetViewModel();
        int id = Objects.requireNonNull(navController.getCurrentDestination()).getId();
        navController.popBackStack(id, true);
        navController.navigate(id);
    }

    private void getOpenSubsonicExtensions() {
        if (Preferences.getServer() != null && (Preferences.getToken() != null || Preferences.getPassword() != null)) {
            mainViewModel.getOpenSubsonicExtensions().observe(this, openSubsonicExtensions -> {
                if (openSubsonicExtensions != null) {
                    Preferences.setOpenSubsonicExtensions(openSubsonicExtensions);
                }
            });
        }
    }

    private void checkTempoUpdate() {
        if (BuildConfig.FLAVOR.equals("tempus") && Preferences.isGithubUpdateEnabled() && Preferences.showTempusUpdateDialog()) {
            mainViewModel.checkTempoUpdate().observe(this, latestRelease -> {
                if (latestRelease != null && UpdateUtil.showUpdateDialog(latestRelease)) {
                    GithubTempoUpdateDialog dialog = new GithubTempoUpdateDialog(latestRelease);
                    dialog.show(getSupportFragmentManager(), null);
                }
            });
        }
    }

    private void checkConnectionType() {
        if (Preferences.isWifiOnly()) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

            if (networkInfo != null && networkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
                ConnectionAlertDialog dialog = new ConnectionAlertDialog();
                dialog.show(getSupportFragmentManager(), null);
            }
        }
    }

    private void maybeSchedulePlaybackIntent(Intent intent) {
        if (intent == null) return;
        if (Constants.ACTION_PLAY_EXTERNAL_DOWNLOAD.equals(intent.getAction())
                || intent.hasExtra(Constants.EXTRA_DOWNLOAD_URI)) {
            pendingDownloadPlaybackIntent = new Intent(intent);
        }
        handleAssetLinkIntent(intent);
    }

    private void consumePendingPlaybackIntent() {
        if (pendingDownloadPlaybackIntent == null) return;
        Intent intent = pendingDownloadPlaybackIntent;
        pendingDownloadPlaybackIntent = null;
        playDownloadedMedia(intent);
    }

    private void handleAssetLinkIntent(Intent intent) {
        AssetLinkUtil.AssetLink assetLink = AssetLinkUtil.parse(intent);
        if (assetLink == null) {
            return;
        }
        if (!isUserAuthenticated()) {
            pendingAssetLink = assetLink;
            intent.setData(null);
            return;
        }
        if (assetLinkNavigator != null) {
            assetLinkNavigator.open(assetLink);
        }
        intent.setData(null);
    }

    private boolean isUserAuthenticated() {
        return Preferences.getPassword() != null
                || (Preferences.getToken() != null && Preferences.getSalt() != null);
    }

    private void consumePendingAssetLink() {
        if (pendingAssetLink == null || assetLinkNavigator == null) {
            return;
        }
        assetLinkNavigator.open(pendingAssetLink);
        pendingAssetLink = null;
    }

    private void playDownloadedMedia(Intent intent) {
        String uriString = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_URI);
        if (TextUtils.isEmpty(uriString)) {
            return;
        }

        Uri uri = Uri.parse(uriString);
        String mediaId = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_MEDIA_ID);
        if (TextUtils.isEmpty(mediaId)) {
            mediaId = uri.toString();
        }

        String title = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_TITLE);
        String artist = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_ARTIST);
        String album = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_ALBUM);
        int duration = intent.getIntExtra(Constants.EXTRA_DOWNLOAD_DURATION, 0);

        Bundle extras = new Bundle();
        extras.putString("id", mediaId);
        extras.putString("title", title);
        extras.putString("artist", artist);
        extras.putString("album", album);
        extras.putString("uri", uri.toString());
        extras.putString("type", Constants.MEDIA_TYPE_MUSIC);
        extras.putInt("duration", duration);

        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setExtras(extras)
                .setIsBrowsable(false)
                .setIsPlayable(true);

        if (!TextUtils.isEmpty(title)) metadataBuilder.setTitle(title);
        if (!TextUtils.isEmpty(artist)) metadataBuilder.setArtist(artist);
        if (!TextUtils.isEmpty(album)) metadataBuilder.setAlbumTitle(album);

        MediaItem mediaItem = new MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(metadataBuilder.build())
                .setUri(uri)
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder()
                        .setMediaUri(uri)
                        .setExtras(extras)
                        .build())
                .build();

        MediaManager.playDownloadedMediaItem(getMediaBrowserListenableFuture(), mediaItem);
    }
}
