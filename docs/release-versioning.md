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

- `versionCode`: 2
- `versionName`: `0.1.1`

This keeps installed builds distinguishable in Android settings and makes GitHub releases easier to track.
