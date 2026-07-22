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
              -> SSDP / DMS / Scalar protocol and DownloadService
```

`CameraWifiBinding` still supports an already-connected Wi-Fi network, but a network returned by `CameraWifiConnector` always takes precedence.

## UI states

1. Disconnected: connect a saved camera, enter first-use credentials, or browse an already-connected camera network.
2. Connecting: connect Wi-Fi, verify Sony services, then prepare the camera library.
3. Connected home: show real model, SSID and host data, then let the user open photos, albums or imports.

Passwords never enter `SonyEdgeUiState`, logs, saved instance state, or diagnostics. SharedPreferences only store AES-GCM ciphertext and its IV; the key remains in Android Keystore. App backup is disabled so encrypted preference data cannot be restored without its device-bound key.

## Compatibility behavior

The existing manual workflow remains available: when the phone is already joined to the camera hotspot, SonyEdge can bind that Wi-Fi and run protocol discovery without storing a password.
