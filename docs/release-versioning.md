# Release Versioning

SonyEdge uses Android `versionCode` and `versionName` from `app/build.gradle`.

Before handing a new APK build to a phone for real use, increment both values:

- Increase `versionCode` by 1.
- Increase `versionName` as a patch version for normal fixes, for example `0.1.1` to `0.1.2`.
- Use a minor version bump for larger user-visible feature changes, for example `0.1.1` to `0.2.0`.

Recommended command:

```powershell
.\scripts\bump-version.ps1 patch
```

Use `minor` or `major` instead of `patch` for larger releases.

Current baseline:

- `versionCode`: 5
- `versionName`: `0.1.4`

This keeps installed builds distinguishable in Android settings and makes GitHub releases easier to track.

Debug APK archive:

- `:app:assembleDebug` still writes the normal install target at `app/build/outputs/apk/debug/app-debug.apk`.
- Each debug build also archives a versioned copy under `app/build/outputs/versioned-apk/`.
- Archived APK names include version, code, build type, and timestamp, for example:

```text
SonyEdge-v0.1.2-3-debug-20260706-150000.apk
```

The timestamp prevents repeated local builds from overwriting earlier APKs with the same app version.
