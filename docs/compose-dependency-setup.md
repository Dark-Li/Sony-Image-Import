# Compose Dependency Setup

SonyEdge is currently being migrated from the legacy Java View UI to a Kotlin + Jetpack Compose UI.

The camera protocol and download path still use the existing Java classes, but the launcher activity now points at `ComposeMainActivity`.

## Dependency Blocker Recovery

If a machine can run Gradle, Android Gradle Plugin, and the Kotlin plugin, but cannot download Compose artifacts because HTTPS requests to Maven repositories fail with TLS handshake errors, use this note to restore the local dependency cache.

The failing dependency group is:

```text
androidx.activity:activity-compose:1.9.3
androidx.compose.ui:ui:1.7.5
androidx.compose.ui:ui-tooling:1.7.5
androidx.compose.ui:ui-tooling-preview:1.7.5
androidx.compose.foundation:foundation:1.7.5
androidx.compose.material3:material3:1.3.1
androidx.compose.material:material-icons-extended:1.7.5
```

The project is configured to resolve dependencies from these local locations before remote repositories:

```text
D:\Work\Codex\Sony\local-maven
C:\Users\N.k\.m2\repository
C:\Users\N.k\.gradle\caches\modules-2\files-2.1
```

## Recommended Fix

Open `D:\Work\Codex\Sony` in Android Studio and run `Sync Project with Gradle Files`.

If Android Studio can download the artifacts, Gradle will cache them automatically under:

```text
C:\Users\N.k\.gradle\caches\modules-2\files-2.1
```

After that, run:

```powershell
.\scripts\android-deploy-test.ps1
```

## Offline Fix

On a different computer that can access Maven/Google repositories:

1. Open or copy this project.
2. Run `gradlew.bat :app:assembleDebug`.
3. Copy the resolved dependency cache from:

```text
C:\Users\<username>\.gradle\caches\modules-2\files-2.1
```

to this machine:

```text
C:\Users\N.k\.gradle\caches\modules-2\files-2.1
```

Alternatively, publish the required artifacts and their transitive dependencies as a Maven repository under:

```text
D:\Work\Codex\Sony\local-maven
```

The `settings.gradle` file already checks `local-maven` before network repositories.
