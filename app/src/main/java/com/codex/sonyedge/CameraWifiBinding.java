package com.codex.sonyedge;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates Android's process-wide network binding across protocol and download work. */
public final class CameraWifiBinding {
    private static final String TAG = "SonyEdge-Network";
    private static final Object LOCK = new Object();
    private static ConnectivityManager manager;
    private static Network boundNetwork;
    private static Network preferredNetwork;
    private static int leaseCount;

    private CameraWifiBinding() {
    }

    public static Lease acquire(Context context) {
        synchronized (LOCK) {
            if (leaseCount > 0) {
                if (boundNetwork == null) {
                    throw new IllegalStateException("Wi-Fi lease state is inconsistent");
                }
                leaseCount++;
                Log.d(TAG, "Reused bound Wi-Fi network " + boundNetwork
                        + " lease count=" + leaseCount);
                return new Lease(boundNetwork);
            }

            ConnectivityManager currentManager = context.getApplicationContext()
                    .getSystemService(ConnectivityManager.class);
            if (currentManager == null) {
                throw new IllegalStateException("ConnectivityManager is unavailable");
            }
            Network wifi = preferredNetwork;
            if (!isWifi(currentManager, wifi)) {
                if (wifi != null) {
                    Log.d(TAG, "Preferred Wi-Fi network is no longer available: " + wifi);
                    preferredNetwork = null;
                }
                wifi = findWifi(currentManager);
            }
            if (wifi == null) {
                throw new IllegalStateException("No active Wi-Fi network is available");
            }
            if (!currentManager.bindProcessToNetwork(wifi)) {
                throw new IllegalStateException("Android rejected Wi-Fi process binding");
            }
            manager = currentManager;
            boundNetwork = wifi;
            leaseCount = 1;
            Log.d(TAG, "Bound process to Wi-Fi network " + wifi);
            Log.d(TAG, "Acquired Wi-Fi lease count=" + leaseCount);
            return new Lease(wifi);
        }
    }

    /**
     * Makes a specifically requested camera network the first choice for future leases.
     * Existing leases keep their current process binding until the final lease closes.
     */
    public static void setPreferredNetwork(Context context, Network network) {
        if (network == null) {
            throw new IllegalArgumentException("Preferred network must not be null");
        }
        ConnectivityManager currentManager = context.getApplicationContext()
                .getSystemService(ConnectivityManager.class);
        if (currentManager == null) {
            throw new IllegalStateException("ConnectivityManager is unavailable");
        }
        if (!isWifi(currentManager, network)) {
            throw new IllegalArgumentException("Preferred network is not an available Wi-Fi network");
        }

        synchronized (LOCK) {
            preferredNetwork = network;
            if (leaseCount > 0 && !network.equals(boundNetwork)) {
                Log.d(TAG, "Deferred preferred Wi-Fi network " + network
                        + " until " + leaseCount + " active lease(s) close");
            } else {
                Log.d(TAG, "Preferred Wi-Fi network set to " + network);
            }
        }
    }

    /** Clears the requested network only if it is still the current preference. */
    public static void clearPreferredNetwork(Network network) {
        if (network == null) {
            return;
        }
        synchronized (LOCK) {
            if (network.equals(preferredNetwork)) {
                preferredNetwork = null;
                Log.d(TAG, "Cleared preferred Wi-Fi network " + network);
            }
        }
    }

    private static Network findWifi(ConnectivityManager connectivityManager) {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (isWifi(connectivityManager, activeNetwork)) {
            return activeNetwork;
        }

        Network bestNetwork = null;
        int bestRank = Integer.MAX_VALUE;
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null
                    || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                continue;
            }
            boolean notVpn = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
            boolean validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            int rank;
            if (notVpn && !validated) {
                rank = 0;
            } else if (notVpn) {
                rank = 1;
            } else if (!validated) {
                rank = 2;
            } else {
                rank = 3;
            }
            if (rank < bestRank) {
                bestNetwork = network;
                bestRank = rank;
            }
        }
        return bestNetwork;
    }

    private static boolean isWifi(ConnectivityManager connectivityManager, Network network) {
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public static final class Lease implements AutoCloseable {
        public final Network network;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Lease(Network network) {
            this.network = network;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            synchronized (LOCK) {
                leaseCount = Math.max(0, leaseCount - 1);
                Log.d(TAG, "Released Wi-Fi lease count=" + leaseCount);
                if (leaseCount == 0 && manager != null) {
                    boolean released = manager.bindProcessToNetwork(null);
                    Log.d(TAG, "Released process Wi-Fi binding result=" + released);
                    manager = null;
                    boundNetwork = null;
                }
            }
        }
    }
}
