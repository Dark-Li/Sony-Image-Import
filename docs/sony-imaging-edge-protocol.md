# Sony A7R III Imaging Edge-Compatible Protocol Notes

This project intentionally targets only the camera Wi-Fi / PlayMemories-style protocol path.
It does not implement an FTP fallback.

## Known constraints

- Sony's ILCE-7RM3 help guide says RAW images are converted to JPEG when sent to a smartphone.
- Sony's current Camera Remote SDK supported-device list does not include the original ILCE-7RM3.
- The app therefore treats RAW transfer as an empirical capability: it must inspect the actual downloaded file extension, MIME type, byte count, and header.

## Expected camera network

When the camera creates the Wi-Fi access point, older Sony cameras commonly expose HTTP JSON-RPC endpoints on:

- `http://192.168.122.1:8080/sony/camera`
- `http://192.168.122.1:8080/sony/avContent`
- `http://192.168.122.1:8080/sony/system`

The app first sends SSDP `M-SEARCH` for `urn:schemas-sony-com:service:ScalarWebAPI:1`, parses the returned device description XML, and extracts `X_ScalarWebAPI_ActionList_URL`. It also probes the Android Wi-Fi gateway address because some firmware or connection modes may use a different host.

If SSDP returns nothing and TCP scans show no open ports on the camera gateway, the phone is connected to the camera access point but the active camera mode is not exposing the old ScalarWebAPI service. In that case, try the camera's smartphone-control / remote-control connection mode rather than a one-shot image-send mode.

Android network requirements implemented here:

- cleartext HTTP is explicitly enabled because Sony's old camera endpoints are plain HTTP.
- `CHANGE_WIFI_MULTICAST_STATE` is requested so SSDP responses are not filtered.
- the process attempts to bind requests to the Wi-Fi network to avoid Android routing camera traffic over cellular data.

## Core JSON-RPC shape

```json
{
  "method": "getAvailableApiList",
  "params": [],
  "id": 1,
  "version": "1.0"
}
```

The implementation keeps responses visible in the UI so real A7R III behavior can drive later refinements. Every feature is gated by `getAvailableApiList`; the code must not assume an API exists just because another Sony model supports it.

Download URL fields are read defensively from:

- `downloadUrl`
- `originalUrl`
- `contentUrl`
- `largeUrl`
- `smallUrl`
- `original[].downloadUrl`
- `content.original[].downloadUrl`

## Minimum validation matrix

- JPEG-only: list contents, download original/large URL, verify JPEG header `FF D8`.
- RAW-only: list contents, try original URL, verify `.ARW` or TIFF-style RAW header `II 2A 00` / `MM 00 2A`.
- RAW+JPEG: confirm whether the protocol exposes both files or only JPEG.
- Batch: download 20 and 100 items while monitoring failures and file validation results.
