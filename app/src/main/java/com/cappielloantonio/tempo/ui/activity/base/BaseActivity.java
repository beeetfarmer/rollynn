package com.cappielloantonio.tempo.ui.activity.base;

import android.Manifest;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.graphics.Color;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.cappielloantonio.tempo.service.DownloaderService;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.dialog.BatteryOptimizationDialog;
import com.cappielloantonio.tempo.util.Flavors;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.UIUtil;
import com.google.common.util.concurrent.ListenableFuture;

@UnstableApi
public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Flavors.initializeCastContext(this);
        initializeDownloader();
        checkBatteryOptimization();
        checkPermission();
        checkAlwaysOnDisplay();
    }

    @Override
    protected void onStart() {
        super.onStart();
        applySystemBarColors();
        initializeBrowser();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySystemBarColors();
    }

    @Override
    protected void onStop() {
        releaseBrowser();
        super.onStop();
    }

    private void checkBatteryOptimization() {
        if (detectBatteryOptimization() && Preferences.askForOptimization()) {
            showBatteryOptimizationDialog();
        }
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void checkAlwaysOnDisplay() {
        if (Preferences.isDisplayAlwaysOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private boolean detectBatteryOptimization() {
        String packageName = getPackageName();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return !powerManager.isIgnoringBatteryOptimizations(packageName);
    }

    private void showBatteryOptimizationDialog() {
        BatteryOptimizationDialog dialog = new BatteryOptimizationDialog();
        dialog.show(getSupportFragmentManager(), null);
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(this, new SessionToken(this, new ComponentName(this, MediaService.class))).buildAsync();
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    public ListenableFuture<MediaBrowser> getMediaBrowserListenableFuture() {
        return mediaBrowserListenableFuture;
    }

    private void initializeDownloader() {
        try {
            DownloadService.start(this, DownloaderService.class);
        } catch (IllegalStateException e) {
            DownloadService.startForeground(this, DownloaderService.class);
        }
    }

    protected void applySystemBarColors() {
        // Transparent navigation bar so it blends with the app background instead
        // of showing as a distinct (contrast-scrimmed) dark bar.
        applySystemBarColors(UIUtil.getSystemBarColor(this), Color.TRANSPARENT);
    }

    protected void applySystemBarColors(int rawColor) {
        applySystemBarColors(rawColor, rawColor);
    }

    protected void applySystemBarColors(int statusRawColor, int navRawColor) {
        // Keep colours as given so callers can pass a transparent (see-through) bar.
        int statusColor = Color.alpha(statusRawColor) == 0 ? statusRawColor : opaque(statusRawColor);
        int navColor = Color.alpha(navRawColor) == 0 ? navRawColor : opaque(navRawColor);

        getWindow().setStatusBarColor(statusColor);
        getWindow().setNavigationBarColor(navColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().setNavigationBarDividerColor(navColor);
        }

        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(Color.alpha(statusColor) != 0
                && ColorUtils.calculateLuminance(statusColor) > 0.5);
        insetsController.setAppearanceLightNavigationBars(Color.alpha(navColor) != 0
                && ColorUtils.calculateLuminance(navColor) > 0.5);
    }

    private int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }
}
