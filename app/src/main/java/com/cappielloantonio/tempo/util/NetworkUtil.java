package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.cappielloantonio.tempo.App;

import java.util.ArrayList;
import java.util.List;

public class NetworkUtil {
    private static volatile boolean serverReachable = true;

    public static void setServerReachable(boolean reachable) {
        serverReachable = reachable;
    }

    public static boolean isServerUnreachable() {
        return !serverReachable || isOffline();
    }

    public static boolean isOffline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) App.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();

            if (network != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);

                if (capabilities != null) {
                    // A VPN such as Tailscale stays up with Wi-Fi and mobile data off, but reaches nothing.
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && realNetworks(connectivityManager).isEmpty()) {
                        return true;
                    }
                    return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                }
            }
        }

        return true;
    }

    /** Wi-Fi, mobile data, Ethernet and the like: every network with internet that isn't a VPN. */
    @SuppressWarnings("deprecation") // getAllNetworks still works, and its replacement only reports changes.
    public static List<Network> realNetworks(ConnectivityManager connectivityManager) {
        List<Network> networks = new ArrayList<>();
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null
                    && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                networks.add(network);
            }
        }
        return networks;
    }
}
