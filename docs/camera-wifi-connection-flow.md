# Camera Wi-Fi connection flow

## Goal

SonyEdge should be able to request a previously configured Sony camera Wi-Fi network from inside the app, while preserving the existing path for users who joined the camera network manually.

## Platform limits

- Android 10 and newer use `WifiNetworkSpecifier` for the camera's local-only access point.
- Android always owns the first approval UI. The app cannot silently force a Wi-Fi switch.
- Android can remember approval for the same app and exact access point, so later requests may connect without another confirmation.
- Android 8 and 9 use the legacy `WifiConfiguration` path.
- The network request callback must remain registered while browsing or downloading. Releasing it can disconnect the local-only camera network.

## Architecture

```text
Compose UI
  -> SonyEdgeViewModel
      -> CameraWifiProfileStore (Android Keystore encrypted password)
      -> CameraWifiConnector (owns NetworkRequest/NetworkCallback)
          -> CameraWifiBinding (preferred Network)
              -> SSDP / DMS / Scalar protocol
              -> Compose thumbnail and preview requests
              -> DownloadService
```

`CameraWifiBinding` still supports an already-connected Wi-Fi network, but a network returned by `CameraWifiConnector` always takes precedence.
Every camera HTTP request must hold a binding lease until its response is fully read. Compose image requests use `lease.network.openConnection(...)` explicitly because a `WifiNetworkSpecifier` network is local-only and is not guaranteed to become Android's default route.

## UI states

1. Disconnected: connect a saved camera, enter first-use credentials, or browse an already-connected camera network.
2. Connecting: connect Wi-Fi, verify Sony services, then prepare the camera library.
3. Connected home: show real model, SSID and host data, then let the user open photos or import history.

After service discovery, the connection flow distinguishes the camera's operating mode from its advertised services:

- `ContentDirectory` without `XPushList`: prepare the normal camera library and show the connected home.
- `ContentDirectory` with a usable `XPushList`: skip the normal root browse, start the camera-selected transfer automatically, and hand the resulting list directly to `DownloadService`.

The Settings action remains as a diagnostic retry, but automatic and manual receive share one guarded request. Connection generation checks prevent a stale receive from updating a newer connection. If automatic receive fails, the camera remains connected and a later normal browse performs a fresh root request.

During camera-selected transfer setup and download, connection-changing actions are disabled. The XPush list is handed to `DownloadService` as a foreground service, which reports progress and sends `X_TransferEnd` before releasing its Wi-Fi lease. Sony cameras normally close the temporary access point after a terminal transfer; that final network loss preserves the Imports result and is not reported as a connection failure.

The connected home exposes photo browsing and import history. Protocol folders are an implementation detail: Back from a single-day photo grid returns to the visible `Date` list, and Back from the `Date` list returns to the connected home. The internal `Camera` and `PhotoRoot` parents are never exposed.

Users can add a different camera without deleting the remembered one first. The existing encrypted profile is replaced only after the new camera Wi-Fi and Sony services have both been verified. Disconnect releases an app-requested camera network and clears the active camera session while retaining the remembered profile for the next one-tap connection.

Passwords never enter `SonyEdgeUiState`, logs, saved instance state, or diagnostics. SharedPreferences only store AES-GCM ciphertext and its IV; the key remains in Android Keystore. App backup is disabled so encrypted preference data cannot be restored without its device-bound key.

## Compatibility behavior

The existing manual workflow remains available: when the phone is already joined to the camera hotspot, SonyEdge can bind that Wi-Fi and run protocol discovery without storing a password.
