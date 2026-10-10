package com.safarsaathi.app.ui.ride;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * "Does the phone have working internet right now?" (needs ACCESS_NETWORK_STATE, a permission Android grants without
 * asking). VALIDATED means Android itself reached the internet on that network, so a Wi-Fi with a login page still
 * counts as offline. Different from the Home sync line, which is about OUR backend only.
 */
public final class NetworkStatus {

    private NetworkStatus() {
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity == null) {
            return false;
        }
        Network network = connectivity.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : connectivity.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
