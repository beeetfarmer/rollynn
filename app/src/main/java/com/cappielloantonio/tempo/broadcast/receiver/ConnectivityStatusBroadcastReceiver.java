package com.cappielloantonio.tempo.broadcast.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.util.NetworkUtil;

@OptIn(markerClass = UnstableApi.class)
public class ConnectivityStatusBroadcastReceiver extends BroadcastReceiver {
    private final MainActivity activity;

    public ConnectivityStatusBroadcastReceiver(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

            if (noConnectivity) {
                NetworkUtil.setServerReachable(false);
                activity.bind.offlineModeTextView.setTag(true);
                activity.bind.offlineModeTextView.setVisibility(View.VISIBLE);
                activity.getServerReachable().setValue(false);
            } else {
                NetworkUtil.setServerReachable(true);
                activity.bind.offlineModeTextView.setTag(null);
                activity.bind.offlineModeTextView.setVisibility(View.GONE);
                activity.getServerReachable().setValue(true);
                MediaManager.submitPendingScrobbles();
            }
        }
    }
}
